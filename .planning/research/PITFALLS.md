# Domain Pitfalls

**Domain:** Kotlin code generator / jOOQ DSL wrapper — v0.2 child-table-named builders and placeholder objects
**Project:** declarative-jooq
**Researched:** 2026-03-16
**Confidence:** HIGH (derived from direct codebase analysis — all pitfalls are grounded in the actual implementation in `MetadataExtractor.kt`, `BuilderEmitter.kt`, `TopologicalInserter.kt`, `ForeignKeyIR.kt`, and `RecordNode.kt`)

---

## Critical Pitfalls

Mistakes that cause silent wrong behavior, runtime errors on insertion, or require rewriting the naming logic after discovery.

---

### Pitfall 1: Builder Function Name Collision When Two FKs to Same Table Have Matching Child-Table Names

**What goes wrong:** The new naming rule says: use the child table name as the builder function name, falling back to the FK column name only when there are multiple FKs to the same parent table. The `task` table has two FKs to `app_user`: `created_by` and `updated_by`. Both point to `app_user`. Under child-table-based naming, both would naively resolve to `appUser()` — a direct collision. The existing fallback logic strips `_id` and camelCases the FK column name (`createdBy`, `updatedBy`). If the new naming logic fails to trigger the fallback correctly, two methods named `appUser` are emitted on `AppUserBuilder`, and only one survives at compile time (KotlinPoet will emit both but Kotlin's overload rules may allow it if the parameter types differ — but since both take the same `AppUserBuilder.() -> Unit` lambda type, they will silently overwrite each other or cause a compile error).

**Why it happens:** The fallback condition "use FK column name when there are multiple FKs to the same target table" is easy to state but subtle to implement. The `inboundFKs` list on a `TableIR` groups FKs by parent table (`parentTableName`), not by child table name. The collision check must be: "does the parent `TableIR` have more than one inbound FK from the same child table pointing to the same parent table?" The current `ForeignKeyIR.builderFunctionName` is computed in `MetadataExtractor` based on the outbound FK column name. The new naming logic must re-derive the function name in a context that knows about siblings — a purely per-FK computation misses this.

**Consequences:** Generated code has two methods with identical signatures on the same class — Kotlin compiler error, or (if KotlinPoet deduplicates by name) silently emits only one, losing disambiguation. Existing tests in `CodeGeneratorTest.multipleFkNaming()` explicitly assert that `createdBy` and `updatedBy` exist and `task` does NOT exist; these tests will fail if the new naming breaks this invariant.

**Prevention:**
- Compute `builderFunctionName` in a two-pass: first pass assigns child-table-based names; second pass detects collisions (same parent, multiple inbound FKs from same child table or from different child tables that produce the same camelCase name) and switches to FK-column-name fallback for all colliding entries.
- The collision check must be on the **parent** table's `inboundFKs` grouped by both `childTableName` and the derived camelCase name. A collision exists when two FK entries on the same parent would produce the same function name.
- Preserve the existing test assertion: `AppUserBuilder` must have `createdBy()` and `updatedBy()`, not `task()`.

**Detection:** `CodeGeneratorTest.multipleFkNaming()` fails with "createdBy not found" or compile error in generated code. Also: `KotlinCompilation.ExitCode.COMPILATION_ERROR` with "platform declaration clash" referencing two methods with the same JVM signature.

**Phase:** Naming logic computation in `MetadataExtractor` or wherever `builderFunctionName` is assigned. Must be addressed before any builder emission work.

---

### Pitfall 2: The `table_name` vs `table_name_id` Column Ambiguity Produces Duplicate Builder Function Names

**What goes wrong:** A table like `post` might have a column named `author` (FK to `user`) and another column named `author_id` (also FK to `user`, just named differently by the schema author). Under the child-table-based naming rule, both would generate `user()` as the function name on `UserBuilder`. Under the FK-column fallback, stripping `_id` from `author_id` → `author`, and `author` stripped becomes `author` — both produce `author()`. Either way, a collision.

**Why it happens:** The `_id` suffix strip in `MetadataExtractor.toCamelCase` + `removeSuffix("_id")` is not idempotent when a column is already named without `_id`. `organization_id` → `organization`. A column literally named `organization` → `organization`. Both produce the same camelCase name. The PROJECT.md milestone requirements explicitly call this out as a named edge case: "Edge case handling for FK columns `table_name` and `table_name_id` pointing to same table generating same builder name."

**Consequences:** Two builder functions with the same name and same lambda parameter type on the same class — Kotlin compile error or silent overwrite.

**Prevention:**
- After computing all candidate function names for a parent's inbound FKs, detect duplicates before emission.
- When a collision is detected between a `table_name` and a `table_name_id` variant, use the full FK column name (not stripped) as the disambiguator: `author` stays `author`, `authorId` is derived from `author_id` without stripping.
- Write a test schema specifically exercising this case (a table with both `author` and `author_id` FK columns to the same target).

**Detection:** Codegen produces two methods with the same name and signature on the same builder class. KotlinPoet will emit both; Kotlin compiler will reject with duplicate method error.

**Phase:** `MetadataExtractor` naming logic, same pass as Pitfall 1. Needs a dedicated test case.

---

### Pitfall 3: Self-Referential FK Naming Change Breaks Existing API

**What goes wrong:** The current self-ref naming is hard-coded in `MetadataExtractor`: `"child" + toPascalCase(tableName)` → `childCategory()`. Under child-table-based naming, the natural name would be `category()` (same as the parent). This would make a root-level `category { }` block and a child `category { }` block use the same function name at different lexical scopes. That is acceptable in Kotlin (the receiver changes), but it means the DSL looks like:

```kotlin
category {
    name = "Electronics"
    category {               // same function name, different receiver
        name = "Phones"
    }
}
```

This is actually the goal stated in the milestone — "child-table-named builders." But if the implementation changes `childCategory` to `category` the existing integration test harness in `FullPipelineTest` and `CodeGeneratorTest` calls `childCategory { }` and will break. This is a deliberate breaking change, but it must be handled explicitly — not discovered as a surprise test failure.

**Why it happens:** `childCategory` was specifically chosen in v0.1 to make the self-ref call site syntactically unambiguous. The new naming rule makes them syntactically identical but distinguished by receiver type (the outer `category { }` is a `DslScope` extension; the inner `category { }` is a method on `CategoryBuilder`). Kotlin supports this, but the existing test harness strings still say `childCategory`.

**Consequences:** All existing tests using `childCategory` will fail at compilation after renaming. If the change is partially applied (some tests updated, some not), you get confusing partial failures.

**Prevention:**
- Treat this as a known breaking change with a concrete migration checklist: rename every `childCategory` call in `CodeGeneratorTest.edgeCaseHarnessSource()`, `FullPipelineTest.integrationHarnessSource()`, and `DslExecutionTest` to the new name.
- Decide the self-ref naming rule explicitly before implementation: option A is `category()` (same name, receiver disambiguation), option B is `childCategory()` (preserved), option C is configurable. Document the decision.
- Update both the `MetadataExtractor` naming logic AND all test harness strings atomically.

**Detection:** `CodeGeneratorTest.selfReferentialFkInserts()` or `FullPipelineTest.selfReferentialFkTwoPassInsert()` fail with "Unresolved reference: childCategory" after the naming change.

**Phase:** Naming implementation phase. The test harness migration must be in scope — do not leave it as a follow-up.

---

### Pitfall 4: Placeholder Objects Captured Before Insert — Stale PK Reference

**What goes wrong:** Placeholder objects represent "use the record inserted from this builder block as the FK value." A placeholder is returned from a builder block like:

```kotlin
val alicePlaceholder = organization {
    appUser {
        name = "Alice"
        // ... returns AppUserPlaceholder
    }
}
// Later, in a different root tree:
organization {
    task {
        title = "Cross-tree task"
        createdBy = alicePlaceholder  // placeholder FK assignment
    }
}
```

The placeholder holds a reference to the `RecordNode`. At placeholder capture time (DSL block evaluation), the record has not been inserted yet and has no PK. The FK assignment must happen at insertion time, after the parent record has been stored. If the runtime resolves placeholder FKs at graph construction time (when the lambda runs), it will read a null PK and silently insert `NULL` for the FK.

**Why it happens:** `RecordNode.record` is populated at build time (when `buildRecord()` runs), but jOOQ only populates the identity field (`id`) after `record.store()` is called and `getGeneratedKeys()` is read. The current `TopologicalInserter` resolves `parentNode.record.get(parentPk.fields[0])` at insert time, after the parent is stored — this works for tree-based parent-child. Placeholders must use the same deferred-PK-read pattern, not read the PK at placeholder creation time.

**Consequences:** Tasks with placeholder-assigned FK receive `NULL` for `created_by`, violating NOT NULL constraints and causing a DB error, or silently inserting 0 if the PK is a Long defaulting to 0.

**Prevention:**
- Placeholder objects must store a reference to a `RecordNode` (not a PK value). The `TopologicalInserter` must resolve placeholder-based FKs the same way it resolves tree-based FKs: read `node.record.get(pk.fields[0])` at insert time, after the placeholder's record has been stored.
- The topological sort must account for placeholder dependencies: if `task` has a placeholder FK to `app_user`, the sort must place `app_user` before `task` in insertion order. This means `buildTableGraph()` in `TopologicalInserter` must include placeholder edges, not just parent-node tree edges.
- Add a validation step: before insertion begins, verify all placeholders in the graph reference nodes that are present in the graph (cross-root-tree validation).

**Detection:** Runtime `DataIntegrityViolationException` for NOT NULL FK columns, or `NullPointerException` in `TopologicalInserter` when reading PK of a placeholder node that hasn't been stored yet.

**Phase:** Runtime placeholder resolution — `TopologicalInserter.buildTableGraph()` and the FK assignment step.

---

### Pitfall 5: Placeholder Override Silently Ignored When Parent Context Also Resolves the Same FK

**What goes wrong:** The override semantic is: "if a placeholder is explicitly assigned to an FK field, it takes precedence over the parent-context auto-resolution." In the current system, the parent-context FK is wired at builder construction time — the `parentFkField` is passed into the `RecordBuilder` constructor. At insertion time, `TopologicalInserter` reads `node.parentNode.record.get(pk)` and sets it on the child's record. If a placeholder is also assigned to the same field, the last write wins — which may be the parent-context write (done at insert time) rather than the placeholder (which may be applied at a different point in the insert loop).

**Why it happens:** The current insertion code in `TopologicalInserter.insertAll()` applies the parent-context FK assignment unconditionally for all non-self-referential nodes that have a `parentNode`. If placeholder override is added as a separate step, the parent-context write may overwrite the placeholder value, or vice versa, depending on execution order.

**Consequences:** Placeholder override silently has no effect — the FK is set to the parent context value. The DSL appears to accept the override syntax but the inserted record has the wrong FK value.

**Prevention:**
- Add a flag to `RecordNode` (or a separate structure) that marks a specific FK field as "explicitly overridden by placeholder." The insertion logic must check this flag: if set, skip the parent-context auto-resolution for that field.
- Alternatively, model placeholder assignment as replacing `parentNode`/`parentFkField` at builder-build time, so no separate override mechanism is needed — the placeholder becomes the effective parent for that FK.
- Write a specific test: declare a child inside a parent context, assign a placeholder to the child's FK field, verify the inserted FK matches the placeholder record, not the parent-context record.

**Detection:** Integration test where placeholder override is expected but the inserted FK still points to the parent-context record. Silent wrong value — no exception.

**Phase:** Runtime `TopologicalInserter` FK resolution step. Must be tested with an explicit override scenario.

---

### Pitfall 6: Topological Sort Misses Cross-Root-Tree Placeholder Dependencies

**What goes wrong:** The current `buildTableGraph()` in `TopologicalInserter` builds the dependency graph by traversing `RecordNode.parentNode` links — tree edges only. Cross-root-tree placeholder assignments are not tree edges: "Alice" is a root node in one tree; a task in a different root tree has a placeholder FK to Alice. The topological sort sees no edge from `task` → `app_user` (the task's tree parent is its own branch), so it may insert `task` before `app_user`, causing the placeholder PK read to fail.

**Why it happens:** `buildTableGraph()` only walks `node.parentNode` links. Placeholder FKs are stored differently (on the record node or as a separate list) and are not currently represented as edges in `buildTableGraph()`.

**Consequences:** `task` is inserted before `app_user`. When the inserter tries to read Alice's PK for the placeholder FK, the PK is null (Alice hasn't been inserted yet). This causes a runtime NPE or DB constraint violation.

**Prevention:**
- `RecordNode` (or a new `PlaceholderFK` structure) must carry a reference to the placeholder target node for each placeholder-assigned FK.
- `buildTableGraph()` must iterate both `node.parentNode` edges AND all placeholder FK target nodes to add edges to the dependency graph.
- Extend `TopologicalSorterTest` with a test for a multi-root graph where table B depends on table A via placeholder (not tree edge).

**Detection:** `NullPointerException` in `TopologicalInserter` when reading placeholder node PK, or DB constraint violation from inserting null into a NOT NULL FK column.

**Phase:** `TopologicalInserter.buildTableGraph()` — must be extended before placeholder runtime support is shipped.

---

## Moderate Pitfalls

---

### Pitfall 7: Child-Table-Named Functions Change the DSL's Breaking-Change Surface

**What goes wrong:** The v0.1 API uses FK-column-based names. Any code written against v0.1-generated DSL uses `organization { ... organization { ... } }` in the existing test harnesses — but only because the FK column happened to be `organization_id`, and stripping `_id` gives `organization`. For the `task` table's FKs to `app_user`, users write `createdBy { }` and `updatedBy { }`. Under the new child-table-based rule, these would change to `appUser` with a disambiguator needed — the exact desirable form depends on the design decision.

The key risk is that the new naming rule is applied and the old test harness strings are not all updated. The `CodeGeneratorTest.testHarnessSource()` inline string references builder method names literally. A missed rename means the harness fails to compile, but because it's a string inside a `SourceFile.kotlin(...)` call, the failure appears as a `KotlinCompilation.ExitCode.COMPILATION_ERROR` with the message pointing inside the dynamically compiled source — which can be confusing to diagnose.

**Why it happens:** The test harness sources are inline Kotlin strings in test code — they don't get IDE rename refactoring support. A name change in generated API requires manual string updates in multiple places.

**Prevention:**
- Before starting the naming change, grep all test harness source strings for hardcoded builder method names. Create a checklist of every occurrence to update.
- After the rename, run the full test suite including `CodeGeneratorTest` and `FullPipelineTest` before declaring the phase complete.
- The test harness strings are in: `CodeGeneratorTest.testHarnessSource()`, `CodeGeneratorTest.edgeCaseHarnessSource()`, `FullPipelineTest.integrationHarnessSource()`.

**Detection:** `KotlinCompilation.ExitCode.COMPILATION_ERROR` with "Unresolved reference: [old function name]" inside a harness source file.

**Phase:** Naming change implementation phase — include test harness migration in the same phase, not a separate one.

---

### Pitfall 8: Placeholder Object Return Type Leaks Into DSL Lambda Signature

**What goes wrong:** If builder block lambdas currently have return type `Unit` (as generated by `BuilderEmitter` — `LambdaTypeName.get(receiver = childBuilderClass, returnType = UNIT)`), changing them to return a placeholder object means changing the lambda return type. That is a breaking change to the generated DSL API: any call site that passes a trailing lambda without capturing the return value continues to work (Kotlin ignores return values), but the generated function signature changes from `fun appUser(block: AppUserBuilder.() -> Unit)` to `fun appUser(block: AppUserBuilder.() -> AppUserPlaceholder)`. Kotlin can call a `() -> AppUserPlaceholder` function and discard the value, so existing non-capturing calls continue to compile — but this needs to be verified.

**Why it happens:** KotlinPoet emits the lambda return type exactly as specified in `LambdaTypeName.get(returnType = ...)`. Changing `returnType = UNIT` to `returnType = placeholderType` changes the generated signature. The `BuilderEmitter` and any emitters that call child builder functions must all agree on the return type.

**Consequences:** If incorrectly done, call sites that don't capture the placeholder get a type mismatch. If the `buildWithChildren()` method doesn't return the placeholder type, the child builder invocation code won't compile.

**Prevention:**
- Decide at design time: does the builder block lambda return a placeholder, or does the parent builder function itself return a placeholder? The latter is cleaner: `val p = organization { ... appUser { ... } }` where `appUser { }` returns `AppUserPlaceholder`, not where the whole `organization { }` block returns something.
- When the lambda return type must remain `Unit` (for non-placeholder usage), consider providing both: `fun appUser(block: AppUserBuilder.() -> Unit): AppUserPlaceholder` where the function itself returns the placeholder but the lambda stays `Unit`. This is the simplest design.
- Test: verify existing calls without capture still compile after the signature change.

**Detection:** Compile error in generated code or test harness: "Type mismatch: expected () -> Unit, got () -> AppUserPlaceholder."

**Phase:** Builder emission design — decide placeholder return type before writing any emission code.

---

### Pitfall 9: Placeholder Cross-Root-Tree Reference Requires Global Node Registry

**What goes wrong:** Cross-root-tree placeholder assignment means a node in tree B references a node in tree A. Currently, `RecordGraph.allNodes()` collects all nodes by traversing `_rootNodes` — this works correctly. But the placeholder must hold a reference to a specific node. If the placeholder is just a typed wrapper around a `RecordNode`, and `RecordNode` is accessible across trees because it's just an object reference, this works. But if placeholders are resolved by table name lookup or by index, cross-tree resolution will fail for trees that were added in the wrong order or if the graph is cleared between uses.

**Why it happens:** The simple case works: `val p = someNode` where `p` is a direct reference. The failure mode is if placeholder identity is based on anything other than direct object reference — e.g., a position index that assumes single-tree ordering, or a lookup by table name that returns the first matching node.

**Prevention:**
- Placeholder identity must be a direct `RecordNode` object reference, never a position index, never a string key.
- `RecordGraph.allNodes()` must include all nodes regardless of which root tree they belong to — verify it does (it does, based on the current implementation).
- Test: a two-root-tree execute block where tree B's record references tree A's record via placeholder. Verify both are in `allNodes()` and the FK resolves correctly.

**Detection:** Placeholder resolves to wrong record or NPE when placeholder target node is in a different root tree.

**Phase:** Placeholder runtime design — establish identity model before any implementation.

---

### Pitfall 10: Generated `buildWithChildren()` Must Return Placeholder, Not `RecordNode`

**What goes wrong:** The current `BuilderEmitter` generates `buildWithChildren()` returning `RecordNode`. If placeholder support requires builders to return a placeholder type, `buildWithChildren()` must either return the placeholder or the caller must wrap the returned `RecordNode` into a placeholder. The `DslScopeEmitter` calls `builder.buildWithChildren()` and then `recordGraph.addRootNode(node)` — it ignores the return value beyond assigning to `node`. If `buildWithChildren()` is changed to return a placeholder, all call sites in emitted code must be updated.

**Why it happens:** `buildWithChildren()` is currently generated unconditionally on all builders with a fixed return type of `RecordNode`. A return type change is a generated-API change that touches every generated file.

**Consequences:** If `buildWithChildren()` return type is changed but `DslScopeEmitter` is not updated, the emitted scope function has a type error. If `BuilderEmitter` is updated but `DslScopeEmitter` is not rebuilt, the generated code has mismatched types.

**Prevention:**
- Change `buildWithChildren()` to return both the `RecordNode` (for internal graph wiring) and the placeholder (for user capture). Options: return a `Pair<RecordNode, Placeholder>`, or make placeholder a thin wrapper that also exposes `node`, or add a separate `placeholder()` function on the builder.
- The cleanest approach: `buildWithChildren()` stays returning `RecordNode` internally; the `DslScopeEmitter` wraps the result in a placeholder before returning it to the user's lambda capture.
- Coordinate changes to `BuilderEmitter` and `DslScopeEmitter` in the same implementation task.

**Detection:** Compile errors in generated code when `buildWithChildren()` return type is changed without updating all consumers.

**Phase:** Builder emission redesign for placeholder support.

---

## Minor Pitfalls

---

### Pitfall 11: `ForeignKeyIR.builderFunctionName` Is Set in `MetadataExtractor` but Consumed in `BuilderEmitter` — Two Places to Change

**What goes wrong:** The naming logic lives entirely in `MetadataExtractor` — `builderFunctionName` is computed and set on each `ForeignKeyIR`. `BuilderEmitter` consumes `fk.builderFunctionName` directly. The new child-table-based naming must be applied in `MetadataExtractor`; if someone also changes `BuilderEmitter` to re-derive the name (to fix a test), the two locations diverge and produce different results.

**Prevention:** Keep naming logic in exactly one place — `MetadataExtractor`. `BuilderEmitter` is a pure consumer. Add a comment in `BuilderEmitter` noting this contract.

**Detection:** Generated function names differ between what tests assert and what is emitted; tracing the discrepancy requires checking both files.

**Phase:** Naming implementation.

---

### Pitfall 12: `kotlin-compile-testing` Doesn't Show Errors in Dynamically Compiled Harness With Good Precision

**What goes wrong:** When the new placeholder API is tested via `CodeGeneratorTest` or `FullPipelineTest`, failures in the harness source strings appear as `KotlinCompilation.ExitCode.COMPILATION_ERROR` with the error pointing into the inline string. The message is printed to stdout but not captured into an assertion message by default. If the test only asserts `exitCode == OK` without printing `result.messages`, failures during development are hard to diagnose.

**Prevention:** The existing pattern in `CodeGeneratorTest.generatedCodeCompiles()` already prints messages on failure — maintain this pattern for all new test harnesses that use placeholder API. Write harness strings for placeholder tests incrementally: compile-test after each small addition.

**Detection:** `KotlinCompilation.ExitCode.COMPILATION_ERROR` with no visible error message in the test output.

**Phase:** All test phases involving `kotlin-compile-testing`.

---

### Pitfall 13: Child-Table-Named Self-Ref Builder Produces Same Name as DslScope Extension

**What goes wrong:** If `CategoryBuilder` gets a method named `category()` (child-table-based self-ref name), and `DslScope` also has an extension function `category()`, within a `category { }` block the user would write `category { }` to create a child — which resolves to `CategoryBuilder.category()` (the receiver wins). This is the intended behavior. But if the `DslScope.category()` extension is accidentally emitted as a method on `CategoryBuilder` too (e.g., because the emitter erroneously marks it as a root AND adds a same-name inbound FK method), there will be a duplicate.

**Prevention:** The `DslScopeEmitter` only emits extension functions on `DslScope` for root tables (`tableIR.isRoot`). `BuilderEmitter` emits inbound FK methods on the builder class. These are different classes — no collision unless someone emits to the wrong class. Verify that `CategoryBuilder.category()` and `DslScope.category()` are distinct: one is a member function on `CategoryBuilder`, the other is an extension on `DslScope`. Kotlin resolves `category { }` inside a `CategoryBuilder` lambda to the member function, not the extension.

**Detection:** Confusing Kotlin "overload resolution ambiguity" error inside a self-ref block — unlikely given the receiver type difference, but possible if extension scoping is misconfigured.

**Phase:** Self-ref naming change.

---

## Phase-Specific Warnings

| Phase Topic | Likely Pitfall | Mitigation |
|-------------|---------------|------------|
| Child-table naming — collision detection | Two FKs to same table produce same function name (Pitfall 1) | Two-pass naming: assign names, then detect and resolve collisions |
| Child-table naming — `table_name` vs `table_name_id` | Same derived name from two different column patterns (Pitfall 2) | Add dedicated test schema for this case before implementing |
| Child-table naming — self-ref rename | `childCategory` → `category` breaks all test harnesses (Pitfall 3) | Audit all harness strings, update atomically with naming change |
| Child-table naming — API churn | Test harness strings use old function names (Pitfall 7) | Grep all harness strings for hardcoded method names before starting |
| Placeholder design — PK timing | Placeholder reads PK before record is inserted (Pitfall 4) | Placeholder must hold `RecordNode` reference; PK read deferred to insertion time |
| Placeholder design — override semantics | Parent-context auto-resolution overwrites explicit placeholder (Pitfall 5) | Add override flag on `RecordNode`; inserter skips parent-context for flagged FKs |
| Placeholder runtime — cross-tree sort | Cross-root-tree placeholder dependencies not in `buildTableGraph()` (Pitfall 6) | Extend `buildTableGraph()` to include placeholder FK edges |
| Placeholder design — return type | Lambda return type change breaks existing call sites (Pitfall 8) | Decide return type model before emitter changes; test non-capturing calls |
| Placeholder runtime — global registry | Cross-tree placeholder needs stable node identity (Pitfall 9) | Use direct `RecordNode` reference; never index-based lookup |
| Builder emitter changes — `buildWithChildren()` | Return type change touches all generated files (Pitfall 10) | Coordinate `BuilderEmitter` and `DslScopeEmitter` changes in same task |
| Naming logic location | `builderFunctionName` computed in extractor, consumed in emitter (Pitfall 11) | Change naming in `MetadataExtractor` only; `BuilderEmitter` is a pure consumer |

---

## Sources

- Direct codebase analysis: `MetadataExtractor.kt` (naming logic, FK extraction), `BuilderEmitter.kt` (function emission, `childBlocks` pattern), `TopologicalInserter.kt` (`buildTableGraph`, parent-FK resolution), `ForeignKeyIR.kt`, `RecordNode.kt`, `RecordGraph.kt` — HIGH confidence (reading the actual implementation)
- `CodeGeneratorTest.kt` and `FullPipelineTest.kt`: inline harness source strings reveal which function names are tested and where breakage will surface — HIGH confidence
- `PROJECT.md` milestone requirements: explicitly names "edge case handling for FK columns `table_name` and `table_name_id` pointing to same table" as a required feature — HIGH confidence
- Kotlin language specification: receiver-based overload resolution for extension functions vs member functions, lambda return type variance — HIGH confidence (stable Kotlin behavior)
- KotlinPoet `LambdaTypeName.get(returnType = ...)` behavior: the `returnType` parameter of a generated lambda type is exactly as specified — HIGH confidence (direct API)
