# Architecture Patterns

**Domain:** Kotlin DSL library — v0.2 child-table-named builders and placeholder objects
**Researched:** 2026-03-16
**Confidence:** HIGH — based on direct code inspection of existing v0.1 implementation

---

## Existing Architecture (v0.1 Baseline)

This document is written against the actual shipped code, not the pre-build plan. The v0.1
implementation is in 4 modules with the following structure.

### Module Map

```
declarative-jooq/
├── dsl-runtime/          Runtime library: DslScope, RecordBuilder, RecordGraph,
│                         RecordNode, TopologicalInserter, TopologicalSorter,
│                         ResultAssembler, DslResult
├── codegen/              Code generator: ClasspathScanner, MetadataExtractor,
│                         IR models (TableIR, ColumnIR, ForeignKeyIR),
│                         emitters (BuilderEmitter, DslScopeEmitter,
│                         ResultEmitter, DslResultEmitter), CodeGenerator
├── gradle-plugin/        Gradle integration: DeclarativeJooqPlugin,
│                         DeclarativeJooqExtension, GenerateDeclarativeJooqDslTask
└── integration-tests/    End-to-end Postgres tests via Testcontainers
```

### Current Data Pipeline

**Build time:**

```
jOOQ .class files
    → ClasspathScanner (finds TableImpl subclasses)
    → MetadataExtractor (reflects over table instances → produces TableIR list)
        [Two-pass: first build all TableIR, then cross-link inboundFKs]
    → BuilderEmitter / DslScopeEmitter / ResultEmitter / DslResultEmitter
        (KotlinPoet → .kt source)
```

**Runtime:**

```
execute(dslContext) { ... }
    → DslScope holds RecordGraph
    → Builder extension functions (generated) add RecordNode entries to RecordGraph
    → TopologicalInserter.insertAll(graph):
        1. buildTableGraph from RecordNode.parentNode links
        2. TopologicalSorter.sort (Kahn's algorithm, self-edges stripped)
        3. Insert each node in order; resolve parentNode PK → child FK field
        4. Second pass for self-referential nodes (UPDATE after all inserts)
    → ResultAssembler.assemble → DslResult
```

### Current Naming Logic (the thing being changed)

`MetadataExtractor` computes `builderFunctionName` in `ForeignKeyIR` at extraction time:

```kotlin
// Current logic (MetadataExtractor.kt lines 72-76)
val strippedFkCol = fkColumnName.removeSuffix("_id")
val builderFunctionName = if (isSelfRef) {
    "child" + toPascalCase(tableName)
} else {
    toCamelCase(strippedFkCol)   // "organization_id" → "organization"
}
```

`BuilderEmitter` uses `fk.builderFunctionName` verbatim when generating child functions on
parent builders (line 165). `ForeignKeyIR.builderFunctionName` is the single source of truth
for the generated function name — change the naming logic here and every emitter follows.

---

## New Feature 1: Child-Table-Named Builder Functions

### What Changes

**Goal:** Builder functions on parent builders should default to the child table name, falling
back to FK column name only when the FK column name doesn't map back to the parent table.

Concrete example: `app_user` has FK `organization_id → organization`. The current name is
`organization` (stripped FK column). The new name should also be `appUser` (child table name).
Both produce the same ergonomics here, but the rule becomes clearer when they differ:

- `task.created_by → app_user`: current = `createdBy`, new = `createdBy` (unchanged — falls
  back because `created_by` stripped of `_by` is `created`, which does NOT match `app_user`)
- `task.updated_by → app_user`: current = `updatedBy`, new = `updatedBy` (unchanged — same
  fallback reasoning)
- `order_item.order_id → order`: current = `order`, new = `orderItem` (child table name wins)

**Disambiguation rule for multiple FKs from same child to same parent:** when two FKs from the
same child table point to the same parent table, the child-table-name would collide; fall back
to FK column name for both. Example: `task.created_by` and `task.updated_by` both point to
`app_user` — `AppUserBuilder` gets `createdBy` and `updatedBy`, not two `task` functions.

### Where to Make the Change

**Single location: `MetadataExtractor.builderFunctionName` computation.**

The function name lives in `ForeignKeyIR.builderFunctionName`. This field is populated in
`MetadataExtractor.extract()` during the outbound FK loop (lines 61-86). The naming logic
is already isolated in that block.

The new algorithm needs to run after the full FK list for a child table is known, because
disambiguation requires knowing whether multiple FKs point to the same parent. The current
code computes names inline inside the single-FK loop. The fix is to compute the list of FKs
first, then apply naming in a separate pass over that list.

**Algorithm:**

```
For each child table T:
    fks = all outbound FKs of T
    For each fk in fks:
        candidateName = childTableCamelCase(T.tableName)  // "app_user" → "appUser"
        colBasedName = toCamelCase(fkColumnName.removeSuffix("_id"))

        if fk.isSelfReferential:
            name = "child" + toPascalCase(T.tableName)    // unchanged
        else if multipleTargetsToSameParent(fks, fk.parentTableName):
            name = colBasedName                            // disambiguate via FK column
        else:
            name = candidateName                           // child table name wins
```

`multipleTargetsToSameParent(fks, parentName)` = `fks.count { it.parentTableName == parentName } > 1`

**Edge case: FK column is `tableName` or `tableName_id` pointing to same table.**

Example: `employee.manager_id → employee` (self-ref) vs. a hypothetical case where
`report.user_id` and `report.author_id` both point to `user`. The disambiguation rule
(multiple FKs to same parent → use column name) correctly handles both directions. For
self-ref the existing `child` prefix rule still applies. No new logic is needed beyond
the disambiguation check.

### IR Changes Required

`ForeignKeyIR` does not need new fields. The `builderFunctionName` field already stores
the final computed name. Only the computation logic in `MetadataExtractor` changes.

`TableIR` does not change.

### Emitter Changes Required

`BuilderEmitter` and `DslScopeEmitter` are unchanged — they consume
`fk.builderFunctionName` as a black box.

### Test Impact

`CodeGeneratorTest.multipleFkNaming()` currently asserts:

```kotlin
assertTrue("createdBy" in methodNames)
assertTrue("updatedBy" in methodNames)
assertTrue("task" !in methodNames)
```

With the new logic, `createdBy` and `updatedBy` still win (disambiguation applies because
two FKs from `task` both point to `app_user`). The test passes unchanged.

New tests needed:
- Single FK where child table name replaces stripped column name (e.g., `order_item.order_id → order` → builder function is `orderItem`)
- Edge case where `tableName` and `tableName_id` both exist for the same parent table

---

## New Feature 2: Placeholder Objects

### What They Are

A placeholder is a value returned from a builder block that represents a "not-yet-inserted"
record. It allows a DSL user to wire FKs explicitly rather than relying on parent-context
auto-resolution, and to wire FKs across independent root trees.

```kotlin
// Desired DSL usage
val alice = execute(dslContext) {
    val alicePlaceholder = appUser {
        name = "Alice"
        email = "alice@example.com"
    }
    task {
        title = "Fix bug"
        createdBy = alicePlaceholder   // explicit FK assignment
    }
}
```

### Design Constraints

1. `execute {}` is synchronous. Record nodes are built during the block, inserted after.
2. FK field values (Long IDs) are not known until after insert. A placeholder cannot carry
   a concrete ID at assignment time.
3. The topological sort must still determine correct insert order. If task's `createdBy`
   field references Alice's placeholder, task must insert after Alice.
4. The placeholder must be resolvable at insert time — after Alice's node is stored,
   `node.record.get(pk)` provides the ID.

### New Runtime Component: `RecordPlaceholder<R>`

Add to `dsl-runtime`:

```kotlin
class RecordPlaceholder<R : UpdatableRecord<R>>(
    internal val node: RecordNode
)
```

This is a thin wrapper around a `RecordNode` that was already registered into the
`RecordGraph`. Its only job is to be a typed handle the DSL user can assign to a FK field
on another builder.

The `RecordNode` is created during the builder's `build()` call — the same moment as today.
The placeholder is just a reference to that node, returned from the builder extension
function.

### Generated Code Changes: Builder Extension Functions Return Placeholder

Currently `DslScopeEmitter` emits:

```kotlin
fun DslScope.appUser(block: AppUserBuilder.() -> Unit) {
    val builder = AppUserBuilder(recordGraph)
    builder.block()
    val node = builder.buildWithChildren()
    recordGraph.addRootNode(node)
    // returns Unit
}
```

New version returns `RecordPlaceholder<AppUserRecord>`:

```kotlin
fun DslScope.appUser(block: AppUserBuilder.() -> Unit): RecordPlaceholder<AppUserRecord> {
    val builder = AppUserBuilder(recordGraph)
    builder.block()
    val node = builder.buildWithChildren()
    recordGraph.addRootNode(node)
    return RecordPlaceholder(node)
}
```

Child builder functions inside parent builders also return placeholders:

```kotlin
// Inside OrganizationBuilder
fun appUser(block: AppUserBuilder.() -> Unit): RecordPlaceholder<AppUserRecord> {
    var resultNode: RecordNode? = null
    childBlocks.add { parentNode ->
        val builder = AppUserBuilder(recordGraph = graph, parentNode = parentNode, parentFkField = ...)
        builder.block()
        resultNode = builder.buildWithChildren()
    }
    return RecordPlaceholder(resultNode!!)   // <-- problem: node doesn't exist yet
}
```

**The deferred execution problem.** Child builder blocks (`childBlocks`) are lambdas that
run after the parent's `build()` call. At the time the `appUser { }` call returns, the
child block hasn't run yet and `resultNode` is null.

**Solution: Deferred placeholder.** The placeholder wraps a `Lazy<RecordNode>` or a
`lateinit`-style container that is filled in when the child block executes:

```kotlin
class RecordPlaceholder<R : UpdatableRecord<R>> {
    private var _node: RecordNode? = null

    internal fun resolve(node: RecordNode) { _node = node }

    internal val node: RecordNode
        get() = _node ?: error("Placeholder not yet resolved — builder block has not executed")
}
```

The child builder function creates a placeholder, passes it into the lambda that runs later,
and the lambda calls `placeholder.resolve(node)` when it executes:

```kotlin
fun appUser(block: AppUserBuilder.() -> Unit): RecordPlaceholder<AppUserRecord> {
    val placeholder = RecordPlaceholder<AppUserRecord>()
    childBlocks.add { parentNode ->
        val builder = AppUserBuilder(recordGraph = graph, parentNode = parentNode, parentFkField = ...)
        builder.block()
        val node = builder.buildWithChildren()
        placeholder.resolve(node)
    }
    return placeholder
}
```

This is safe because: the DSL user uses the placeholder only inside the outer execute block
(to assign to another builder's field), and by the time `execute {}` returns control to
`insertAll`, all builder blocks have run (the block is synchronous). The placeholder is
resolved before it is ever read.

### Explicit FK Assignment on Builders

Builder classes gain a new setter that accepts a `RecordPlaceholder`:

```kotlin
// In generated AppUserBuilder
var organizationId: Long? = null  // existing direct-value setter
var organization: RecordPlaceholder<OrganizationRecord>? = null  // new placeholder setter
```

At insert time, `TopologicalInserter` must check: if a node has an explicit placeholder
assignment (not via parent-context parentNode), it reads the PK from that placeholder's node
and sets the FK field.

**Alternatively** (and more cleanly): the placeholder FK assignment is applied during
`buildRecord()`. When the builder's `buildRecord()` is called, if a placeholder field is set,
the builder sets the corresponding FK column to the placeholder's node's PK value.

But `buildRecord()` is called before insert — the PK isn't available yet at that time.

**Correct approach: RecordNode gets an additional explicit dependency list.**

`RecordNode` gains a new field:

```kotlin
class RecordNode(
    // ... existing fields ...
    val explicitDependencies: MutableList<Pair<TableField<*, *>, RecordPlaceholder<*>>> = mutableListOf()
)
```

`TopologicalInserter` already iterates nodes for FK resolution. It adds a step:

```kotlin
// After existing parentNode FK resolution:
for ((fkField, placeholder) in node.explicitDependencies) {
    val depPk = placeholder.node.table.primaryKey!!.fields[0]
    val depPkValue = placeholder.node.record.get(depPk)
        ?: error("Placeholder node PK is null — dependency must insert before this node")
    @Suppress("UNCHECKED_CAST")
    (node.record as Record).set(fkField as Field<Any?>, depPkValue)
}
```

**Topological sort must include explicit dependencies.** `buildTableGraph` currently only
looks at `node.parentNode`. It must also include `node.explicitDependencies`:

```kotlin
// In TopologicalInserter.buildTableGraph:
for (node in nodes) {
    graph.getOrPut(node.table.name) { mutableSetOf() }
    if (node.parentNode != null) {
        graph[node.table.name]!!.add(node.parentNode.table.name)
    }
    for ((_, placeholder) in node.explicitDependencies) {
        graph[node.table.name]!!.add(placeholder.node.table.name)
    }
}
```

### How Builders Register Explicit Dependencies

The generated builder class gets typed placeholder setter properties for each FK. When the
setter is called, it adds an entry to a deferred list. During `build()`, the deferred list
is passed into the new `RecordNode`:

```kotlin
// In RecordBuilder (base class change):
val explicitPlaceholderDeps: MutableList<Pair<String, RecordPlaceholder<*>>> = mutableListOf()
```

Or the generated builder overrides `build()` to inject them. The cleanest option is to
add placeholder registration to `RecordBuilder.build()` via a hook:

```kotlin
// Generated code registers deps before build():
fun build(): RecordNode {
    val record = buildRecord()
    val deps = collectPlaceholderDependencies()  // generated override
    val node = RecordNode(
        table = table, record = record, parentNode = parentNode,
        parentFkField = parentFkField, declarationIndex = recordGraph.nextDeclarationIndex(),
        isSelfReferential = isSelfReferential,
        explicitDependencies = deps
    )
    parentNode?.children?.add(node)
    return node
}
```

`collectPlaceholderDependencies()` is generated in each builder class and returns the list
of `(TableField, RecordPlaceholder)` pairs corresponding to any placeholder-typed setter
that was assigned.

### Placeholder Override of Parent-Context FK

When a user sets `organization = somePlaceholder` on an `AppUserBuilder` that is already
a child of an organization block, the explicit placeholder should win. This means: if
`explicitDependencies` contains an entry for the same FK field that `parentFkField` targets,
the explicit dependency takes precedence at insert time.

`TopologicalInserter` applies explicit dependencies after the parentNode resolution step —
the last write wins, which is the explicit one.

---

## Component Boundary Changes Summary

| Component | v0.1 Status | v0.2 Change |
|-----------|-------------|-------------|
| `ForeignKeyIR` | Existing | No new fields; `builderFunctionName` computation logic changes |
| `MetadataExtractor` | Existing | Naming algorithm refactored to two-pass; new disambiguation logic |
| `BuilderEmitter` | Existing | New: generate placeholder setter properties; generate `collectPlaceholderDependencies()` override; child functions return `RecordPlaceholder` |
| `DslScopeEmitter` | Existing | Return type changes from `Unit` to `RecordPlaceholder<RecordType>` |
| `RecordNode` | Existing | New field: `explicitDependencies: MutableList<Pair<TableField<*,*>, RecordPlaceholder<*>>>` |
| `RecordBuilder` | Existing | `build()` passes `explicitDependencies` to `RecordNode` constructor |
| `TopologicalInserter` | Existing | `buildTableGraph` includes explicit dependencies; FK resolution loop applies explicit deps after parentNode resolution |
| `RecordPlaceholder` | New | New class in `dsl-runtime` |

---

## Data Flow with Both Features

```
execute(dslContext) {
    val userPlaceholder = appUser {         // DslScopeEmitter now returns RecordPlaceholder
        name = "Alice"
        email = "alice@acme.com"
        organization = orgPlaceholder       // explicit FK setter (BuilderEmitter generates this)
    }
    task {
        title = "Fix bug"
        createdBy = userPlaceholder         // explicit FK setter with placeholder
    }
}

-- During execute {} block execution: --
AppUserBuilder.build() called:
    → RecordNode created with explicitDependencies = [(ORGANIZATION_ID, orgPlaceholder)]
    → RecordPlaceholder(node) returned as userPlaceholder

TaskBuilder.build() called:
    → RecordNode created with explicitDependencies = [(CREATED_BY, userPlaceholder)]

-- TopologicalInserter.insertAll: --
buildTableGraph:
    organization: {}
    app_user: {organization}               // from explicit dep via orgPlaceholder.node
    task: {app_user}                       // from explicit dep via userPlaceholder.node

TopologicalSorter.sort: [organization, app_user, task]

Insert organization → PK = 1
Insert app_user:
    explicit dep ORGANIZATION_ID → orgPlaceholder.node.record.get(pk) = 1
    store()
Insert task:
    explicit dep CREATED_BY → userPlaceholder.node.record.get(pk) = 5
    store()
```

---

## Build Order for v0.2

Dependencies govern order. Both features touch `codegen` and `dsl-runtime`. Neither requires
changes to `gradle-plugin` or `integration-tests` module structure.

**Step 1: `dsl-runtime` — Add `RecordPlaceholder` and update `RecordNode`**

No dependencies on other new code. Self-contained new type plus a field addition to an
existing data class. `TopologicalInserter` change depends on `RecordNode` change.

Tasks:
- Add `RecordPlaceholder<R>` class with deferred `resolve()` mechanism
- Add `explicitDependencies` to `RecordNode`
- Update `RecordBuilder.build()` to pass explicit dependencies through
- Update `TopologicalInserter.buildTableGraph` to include explicit dependencies
- Update `TopologicalInserter` FK resolution loop to apply explicit dependencies

**Step 2: `codegen` — Naming logic and placeholder emission**

Depends on Step 1 because the emitters reference `RecordPlaceholder` type by name.

Tasks:
- Refactor `MetadataExtractor` FK naming to two-pass with disambiguation
- Update `BuilderEmitter` to emit placeholder setter properties per FK
- Update `BuilderEmitter` to emit `collectPlaceholderDependencies()` override
- Update `BuilderEmitter` child functions to return `RecordPlaceholder<ChildRecord>`
- Update `DslScopeEmitter` to return `RecordPlaceholder<Record>` from root functions
- Update `BuilderEmitter` child functions (deferred lambda path) to call `placeholder.resolve(node)`

**Step 3: Tests**

Tasks:
- Add `MetadataExtractor` unit tests for new naming cases
- Update `CodeGeneratorTest.multipleFkNaming()` assertions for any changed names
- Add compile-testing harness for placeholder assignment (explicit FK wiring)
- Add integration test: cross-root-tree placeholder reference
- Add integration test: placeholder overrides parent-context FK

---

## Risks and Constraints

### Deferred Placeholder Resolution Ordering

The deferred-lambda mechanism in child builder functions means the placeholder returned from
`organizationBuilder.appUser { }` is not resolved at call-return time. It is resolved when
`buildWithChildren()` runs on the organization builder. That happens before `insertAll` is
called (it happens inside the `execute {}` block). So by the time `TopologicalInserter`
runs, all placeholders are resolved. This ordering invariant must be preserved — do not
change the execution model.

### Cross-Root-Tree Placeholder

A placeholder returned from one root-level call (e.g., `val alice = appUser { }`) and
assigned inside another root-level call (e.g., `task { createdBy = alice }`) works naturally
because both calls are inside the same `execute {}` block. The `RecordGraph` contains both
nodes. The `explicitDependencies` link is in `RecordNode` which is in the same graph. No
special handling required — it's the same mechanism.

The topological sort treats all nodes from all root trees together (`graph.allNodes()` returns
everything), so cross-tree ordering is already handled.

### Single-Column FK Only

The existing architecture only handles single-column FKs. Placeholders inherit this
constraint. A placeholder wraps one `RecordNode` and resolves one PK value. Multi-column
FK support is out of scope.

### RecordNode is Already Public

`RecordNode` fields are `val` and the class is in `dsl-runtime` main sources. Adding
`explicitDependencies` as a `MutableList` constructor parameter with a default empty list
is a backward-compatible change for code that instantiates `RecordNode` directly (the
generated `RecordBuilder` subclasses). The change is internal to `dsl-runtime` — nothing
in user code constructs `RecordNode` directly.

---

## Sources

All findings are HIGH confidence — based on direct inspection of the v0.1 source code at:
- `/Users/nick/Projects/declarative-jooq/codegen/src/main/kotlin/` (MetadataExtractor, BuilderEmitter, DslScopeEmitter, IR models)
- `/Users/nick/Projects/declarative-jooq/dsl-runtime/src/main/kotlin/` (RecordBuilder, RecordNode, RecordGraph, TopologicalInserter, TopologicalSorter)
- `/Users/nick/Projects/declarative-jooq/codegen/src/test/kotlin/` (CodeGeneratorTest — existing naming assertions)
