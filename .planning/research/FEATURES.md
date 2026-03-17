# Feature Landscape

**Domain:** jOOQ-specific declarative test data DSL (Kotlin, code-generated, FK-aware)
**Researched:** 2026-03-16
**Milestone focus:** v0.2 — child-table-named builder functions and placeholder objects
**Overall confidence:** HIGH for naming algorithm and placeholder design (direct codebase analysis); MEDIUM for ecosystem comparison of placeholder patterns (web search confirmed pattern, no single authoritative source)

---

## Context: What Already Exists (v0.1)

The v0.1 system generates builder classes for each jOOQ table. Child builder functions are placed on the *parent* builder class and are named after the FK column with `_id` stripped:

```
app_user.organization_id → organization builder gets: fun organizationId(block: ...) stripped to fun organization(block: ...)
task.created_by → app_user builder gets: fun createdBy(block: ...)
task.updated_by → app_user builder gets: fun updatedBy(block: ...)
```

The naming logic in `MetadataExtractor.kt` (line 71–76):
```kotlin
val strippedFkCol = fkColumnName.removeSuffix("_id")
val builderFunctionName = if (isSelfRef) {
    "child" + toPascalCase(tableName)
} else {
    toCamelCase(strippedFkCol)
}
```

This produces FK-column-based names. `user { }` was the intended name (from `organization_id` → strip `_id` → `organization` → camelCase → `organization`), but only works by accident when the FK column happens to be named after the parent table.

For the multi-FK case (`created_by`, `updated_by` → `app_user`), the current names are `createdBy { }` and `updatedBy { }` — these are FK-column-based names that *don't* match the child table name, which is correct disambiguation behavior.

---

## Table Stakes (for v0.2)

Features that must work correctly for the v0.2 milestone to be considered complete.

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| **Child-table-named builder functions (standard case)** | `user { }` is more natural than `organizationId { }` when the FK column encodes the parent table name | Med | Naming algorithm change in `MetadataExtractor.kt`; affects generated DSL and tests |
| **FK-column fallback when column doesn't match parent** | `createdBy { }` and `updatedBy { }` remain correct for disambiguating two FKs to the same table | Low | Already done; the fallback must not regress |
| **Edge case: `table_name` and `table_name_id` columns both FK to same table** | Real schemas have both `manager` and `manager_id`; the generator must not produce two identical function names | Med | Must detect collision and apply a tiebreaker (e.g., prefer `_id`-suffixed column name, or suffix with `ById`) |
| **Placeholder object returned from builder blocks** | Cross-tree FK assignment requires a handle on the future record | High | Requires DSL signature change: builder functions return a placeholder, not Unit |
| **Placeholder usable as FK column value** | `val alice = user { }; task { updatedBy = alice }` — assignable directly to FK fields | High | Placeholder must wrap `RecordNode`; FK resolution at insert time reads from placeholder |
| **Placeholder override of parent-context auto-resolution** | If a placeholder is assigned, skip the auto-wired parent FK | Med | Insert-time logic must prefer explicit placeholder over parent-context default |
| **Cross-root-tree placeholder references** | Placeholder from one `organization { }` tree assigned to a builder in a different root tree | Med | No structural blocker — placeholders are just `RecordNode` references already in the graph |
| **Compilation correctness for generated DSL** | Generated code using new naming and placeholder return types must compile | Med | KotlinPoet emitter changes; verify with existing compile-testing infrastructure |

---

## Differentiators (for v0.2)

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| **Natural language-first naming** | `organization { user { task { } } }` reads as a sentence — nesting reflects real entity relationships | Low | Algorithm change only; no new runtime concepts |
| **Explicit FK override without escaping to raw jOOQ** | Users can wire FK relationships the library can't infer (cross-tree, optional FKs) without dropping to `record.set(FIELD, id)` | High | Core new runtime concept; placeholder must carry future PK reference |
| **Disambiguation that's self-documenting** | When column-based fallback triggers, names like `createdBy { }` tell the reader which FK relationship is being filled | Low | Already present; must be preserved correctly |

---

## Anti-Features (for v0.2)

| Anti-Feature | Why Avoid | What to Do Instead |
|--------------|-----------|-------------------|
| **Placeholder as a full result object pre-insert** | Placeholders represent *future* records; they cannot have PK values yet | Return a thin wrapper (`RecordNodePlaceholder`) that resolves at insert time |
| **Placeholder equality/hashCode on future PK** | PK is not known until insert; using it for equality before insert creates bugs | Identity equality on the placeholder object itself (reference equality) |
| **Eager FK resolution at builder-block time** | FK values aren't available at DSL construction time; must be deferred to insert | Placeholders store a reference to the `RecordNode`; `TopologicalInserter` reads the PK after parent is stored |
| **Allowing placeholders to escape the `execute { }` scope** | A placeholder from one `execute` block reused in another would reference a stale record | Placeholders should be internal to the `execute { }` call; no `suspend` or cross-call reuse |
| **String-based naming for child builders** | Builder function name derived from string manipulation of table/column names; mistakes are silent until runtime | Algorithm tested with compile-testing harness immediately |

---

## Naming Algorithm: Child-Table-Named Builders

### The New Rule (replacing line 71–76 in `MetadataExtractor.kt`)

Given a FK from child table `child_table` via column `fk_column` to parent table `parent_table`:

```
stripped = fk_column.removeSuffix("_id")
if (stripped == parent_table OR stripped + "_id" == fk_column AND stripped == parent_table):
    builderFunctionName = toCamelCase(child_table)   // table-name-based
else:
    builderFunctionName = toCamelCase(stripped)       // FK-column-based (fallback)
```

Concretely:
- `app_user.organization_id` → `stripped = "organization"` == parent `"organization"` → name is `toCamelCase("app_user")` = `"appUser"`
- `task.created_by` → `stripped = "created_by"` ≠ parent `"app_user"` → name is `toCamelCase("created_by")` = `"createdBy"` (fallback)
- `task.updated_by` → `stripped = "updated_by"` ≠ parent `"app_user"` → name is `toCamelCase("updated_by")` = `"updatedBy"` (fallback)
- `category.parent_id` (self-ref) → already handled by `"child" + toPascalCase(tableName)` rule; no change needed

### Edge Case: `table_name` and `table_name_id` both FK to same table

If two FK columns `manager` and `manager_id` both reference `app_user` on the same parent table, the algorithm produces two candidates both wanting name `manager`. Tiebreaker:
- Prefer the `_id`-suffixed column for the child-table-named function: `manager_id` → `appUser { }`
- Use the bare column name for the other: `manager` → `manager { }`
- If neither has `_id` suffix, fall back to disambiguation by full column name (both use FK-column-based naming)

### Collision with existing child-table name

If a parent table already has an FK whose stripped name equals the child table name (already using table-name-based naming), but *another* FK also points to the same child from a different column — these are the multi-FK-same-target cases and must be disambiguated via FK column fallback. The algorithm should detect when two FKs from the same parent would resolve to the same function name, and force both to FK-column-based naming.

---

## Placeholder Design: Expected Behavior

### What Users Expect

Based on analogous DSL patterns in the Kotlin ecosystem (Gradle `Provider<T>`, kotlinx.html `Tag`, Ktor DSL configuration blocks), the standard Kotlin DSL convention is:

```kotlin
// Root builder functions return the placeholder
val alice: AppUserResult = execute(ctx) {
    val org = organization { name = "Acme" }   // returns placeholder
    appUser {
        name = "Alice"
        organizationId = org                    // placeholder assigned to FK field
    }
    task {
        // Auto-wired to appUser via parent context (no placeholder needed)
        title = "Alice's task"
    }
}
```

Key behaviors expected:
1. Builder block lambda body changes signature from `() -> Unit` to return a placeholder
2. `organizationId = org` compiles — placeholder type must be assignable to FK field type in the generated code
3. At insert time, the placeholder's resolved PK is used for the FK value
4. Auto-wiring from parent context is the default; explicit placeholder assignment overrides it
5. Both patterns work in the same `execute { }` block

### What the Placeholder Is NOT

- It is not an already-inserted record (no PK yet at assignment time)
- It is not a `Future<T>` or `Deferred<T>` — there's no async involved
- It is not a copy of the record — it is a reference to the same `RecordNode` that will be inserted

### Placeholder vs. Auto-wiring Priority

| Scenario | Behavior |
|----------|----------|
| Child nested under parent, no explicit placeholder | Auto-wire from parent context (existing behavior) |
| Child nested under parent, explicit placeholder for same FK | Placeholder wins; parent context FK is not applied |
| Child nested under parent, explicit placeholder for *different* FK | Both are applied: parent context for its FK, placeholder for its FK |
| Child at different nesting level, cross-tree placeholder | Placeholder is the only source; no parent context |

---

## Feature Dependencies

```
Existing (v0.1):
  Code generator → Builder emitter → FK-column-named child functions
  RecordNode → RecordGraph → TopologicalInserter → FK resolution at insert

New (v0.2):
  Naming algorithm change (MetadataExtractor.kt)
    └─→ Child-table-named builder functions (default)
    └─→ FK-column fallback (unchanged logic, new trigger condition)
    └─→ Edge case collision detection (new)

  RecordNodePlaceholder (new runtime type)
    └─→ Builder block return type changes (from Unit to Placeholder)
          └─→ KotlinPoet emitter changes (BuilderEmitter.kt)
                └─→ DslScopeEmitter.kt (root extension functions return Placeholder)
    └─→ Placeholder FK field type on generated builders
          └─→ TopologicalInserter.kt: detect placeholder FK, resolve PK after parent stored
    └─→ Override logic: explicit placeholder takes priority over parentNode auto-wire
```

### Dependency on Existing v0.1 Components

| Existing Component | v0.2 Change Required |
|--------------------|---------------------|
| `MetadataExtractor.kt` — `builderFunctionName` derivation | Rewrite naming algorithm (primary change) |
| `ForeignKeyIR.kt` | No change to data class; `builderFunctionName` field value changes |
| `BuilderEmitter.kt` — child function body | Return type changes from `Unit` to `Placeholder`; builder lambda return type changes |
| `BuilderEmitter.kt` — constructor/property for explicit placeholder FK fields | New: add `var xyzId: RecordNodePlaceholder?` properties for FK columns |
| `TopologicalInserter.kt` — FK resolution | New: check for explicit placeholder before falling back to parentNode |
| `RecordNode.kt` | Possibly add `explicitFkPlaceholders: Map<TableField<*,*>, RecordNodePlaceholder>` |
| `DslScopeEmitter.kt` | Root extension functions must return `RecordNodePlaceholder` |
| Test harness (`TestBuilders.kt`, `FullPipelineTest.kt`) | Update builder function signatures and integration harness |

---

## Complexity Assessment

| Feature | Estimate | Blocking? |
|---------|----------|-----------|
| Rename `organization_id` → `appUser` in naming algorithm | Low — single function, ~10 lines | No |
| FK-column fallback preservation | Low — condition change only | No |
| Collision detection (`table_name` + `table_name_id`) | Med — need to detect before emitting, add test cases | No |
| Placeholder type definition | Low — thin wrapper around `RecordNode` | No |
| Builder block return type change (emitter) | Med — KotlinPoet changes + all test snapshots update | No |
| Explicit FK placeholder property on builders | Med — new `var` field per FK column, type must be nullable `Placeholder` | No |
| Insert-time placeholder resolution | Med — `TopologicalInserter` must read from placeholder map before parentNode | No |
| Cross-tree placeholder references | Low — `RecordNode` is already in the graph; no structural barrier | No |
| Placeholder override of parent-context | Med — logic inversion in inserter; must not break auto-wire case | No |
| Update `TestBuilders.kt` and integration harness | Med — all existing usages must be updated | No |

No blocker features. The placeholder design has the most interconnected changes but no individual piece is architecturally novel.

---

## MVP Recommendation for v0.2

**Must ship together (atomic — naming change invalidates existing generated output):**
1. Child-table-named builder functions (naming algorithm change)
2. FK-column fallback rule (preserved but re-conditioned)
3. Collision detection for `table_name` / `table_name_id` edge case
4. Update `TestBuilders.kt` hand-written builders to match new naming
5. Update integration test harness to use new names

**Can ship as second part of v0.2 (independent of naming):**
6. Placeholder type (`RecordNodePlaceholder` wrapping `RecordNode`)
7. Builder block return type change (emitter + root extension functions)
8. Explicit FK placeholder properties on builders
9. Insert-time placeholder resolution with override logic
10. Cross-tree placeholder test cases

**Defer (confirmed out of scope per PROJECT.md):**
- Composite FK placeholders
- Lazy/deferred insertion model
- Placeholder escaping `execute { }` scope

---

## Sources

- Codebase analysis: `/Users/nick/Projects/declarative-jooq/codegen/src/main/kotlin/.../MetadataExtractor.kt` (HIGH confidence — direct inspection)
- Codebase analysis: `/Users/nick/Projects/declarative-jooq/codegen/src/main/kotlin/.../BuilderEmitter.kt` (HIGH confidence — direct inspection)
- Codebase analysis: `/Users/nick/Projects/declarative-jooq/dsl-runtime/src/main/kotlin/.../RecordNode.kt`, `TopologicalInserter.kt` (HIGH confidence — direct inspection)
- Codebase analysis: `/Users/nick/Projects/declarative-jooq/integration-tests/src/test/kotlin/.../FullPipelineTest.kt` (HIGH confidence — direct inspection)
- Project requirements: `/Users/nick/Projects/declarative-jooq/.planning/PROJECT.md` (HIGH confidence)
- Web search: Kotlin DSL builder return value patterns (MEDIUM confidence — ecosystem consensus, no single authoritative source)
- [Kotlin official docs: type-safe builders](https://kotlinlang.org/docs/type-safe-builders.html) — confirms builder lambda is `Unit`-returning; wrapping function returns the object (HIGH confidence)
- [Test data DSL patterns](https://betterprogramming.pub/test-data-creation-using-the-power-of-kotlin-dsl-9526a1fad05b) — general DSL test data article (MEDIUM confidence — access denied, cited from search results only)
