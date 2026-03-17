# Phase 6: Placeholder Objects - Context

**Gathered:** 2026-03-16
**Status:** Ready for planning

<domain>
## Phase Boundary

Builder blocks return typed placeholder objects (reusing the existing Result classes) that users can assign to FK properties on any builder, enabling explicit and cross-tree FK wiring without escaping to raw jOOQ. Includes README documentation of both the Phase 5 naming convention and the new placeholder pattern.

</domain>

<decisions>
## Implementation Decisions

### Placeholder type
- Reuse the existing per-table Result classes (e.g., `AppUserResult`, `OrganizationResult`) as placeholders
- The placeholder instance returned from a builder block is the **same instance** that appears in the overall `DslResult` returned by `execute()`
- Pre-insert: set fields are readable, generated fields (id, timestamps) return null/default
- Post-insert (after `execute()` completes): all fields populated including DB-generated values
- Full access always — no guards or restrictions on pre-insert property access

### Return behavior
- Every builder block always returns its Result type — both root-level `DslScope` extensions and nested child builder functions
- Users capture with `val x = appUser { }` when needed; ignore the return value otherwise (Kotlin discards silently)
- No API bifurcation — single code path for all builder invocations

### FK assignment syntax
- Overloaded property with backing ref: keep existing typed property (e.g., `var organizationId: Long?`) AND add a new property accepting the Result type (e.g., `var organization: OrganizationResult?`)
- Property name follows FK column minus `_id` convention (same as Phase 5 builder function naming): `organization_id` → `organization`, `created_by` → `createdBy`
- At insert time: if a placeholder ref is set, resolve its PK instead of using the raw value
- Placeholder assignment silently overrides parent-context auto-resolved FK (PLCH-04) — no warning, the explicit assignment is the user's intent

### Cross-tree wiring
- Graph-level topological sort: placeholder assignments create edges in the dependency graph so referenced records insert first
- Same `val x = builder { }` syntax works at both root and nested levels
- Fan-out supported: a single placeholder can be assigned to multiple builders (e.g., one user as `createdBy` on many tasks)
- Circular placeholder references detected and reported with a descriptive error naming the involved tables and FK fields

### README documentation (DOCS-01)
- Usage examples section with 3-4 code examples: basic capture, cross-tree wiring, parent override, fan-out
- Also document Phase 5 naming convention changes (child-table-named builders, FK column fallback)
- Use actual test schema tables (organization, app_user, task, category) in examples
- Brief explanations between examples — typical library README style

### Claude's Discretion
- Internal data structures for tracking placeholder references in RecordNode/RecordGraph
- How Result instances are created eagerly and connected to the insert pipeline
- Exact mechanism for deferred child block return value propagation
- TopologicalSorter extension approach for cross-tree edges
- Cycle detection error message formatting

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Requirements
- `.planning/REQUIREMENTS.md` — PLCH-01, PLCH-02, PLCH-03, PLCH-04, DOCS-01 specs

### Prior phase context
- `.planning/phases/05-child-table-named-builder-functions/05-CONTEXT.md` — Phase 5 naming decisions (FK column minus `_id` convention reused for placeholder property naming)

### Key source files — Runtime
- `dsl-runtime/src/main/kotlin/com/nickanderssohn/declarativejooq/Dsl.kt` — `execute()` entry point, orchestrates DslScope → TopologicalInserter → ResultAssembler
- `dsl-runtime/src/main/kotlin/com/nickanderssohn/declarativejooq/DslScope.kt` — holds RecordGraph, receives generated extension functions
- `dsl-runtime/src/main/kotlin/com/nickanderssohn/declarativejooq/RecordGraph.kt` — tracks root nodes and declaration indices
- `dsl-runtime/src/main/kotlin/com/nickanderssohn/declarativejooq/RecordNode.kt` — parent/child tree structure, FK field metadata
- `dsl-runtime/src/main/kotlin/com/nickanderssohn/declarativejooq/RecordBuilder.kt` — abstract base for generated builders, `build()` and `buildWithChildren()`
- `dsl-runtime/src/main/kotlin/com/nickanderssohn/declarativejooq/TopologicalInserter.kt` — FK resolution at insert time (parent PK → child FK field)
- `dsl-runtime/src/main/kotlin/com/nickanderssohn/declarativejooq/TopologicalSorter.kt` — Kahn's algorithm for insert ordering
- `dsl-runtime/src/main/kotlin/com/nickanderssohn/declarativejooq/DslResult.kt` — raw result wrapper
- `dsl-runtime/src/main/kotlin/com/nickanderssohn/declarativejooq/ResultAssembler.kt` — assembles DslResult from inserted records

### Key source files — Code Generation
- `codegen/src/main/kotlin/com/nickanderssohn/declarativejooq/codegen/emitter/BuilderEmitter.kt` — generates builder classes with properties and child builder functions
- `codegen/src/main/kotlin/com/nickanderssohn/declarativejooq/codegen/emitter/DslScopeEmitter.kt` — generates DslScope extension functions (root-level builders)
- `codegen/src/main/kotlin/com/nickanderssohn/declarativejooq/codegen/emitter/ResultEmitter.kt` — generates per-table Result wrapper classes
- `codegen/src/main/kotlin/com/nickanderssohn/declarativejooq/codegen/emitter/DslResultEmitter.kt` — generates typed DslResult with accessor functions
- `codegen/src/main/kotlin/com/nickanderssohn/declarativejooq/codegen/ir/TableIR.kt` — IR with table/column/FK metadata
- `codegen/src/main/kotlin/com/nickanderssohn/declarativejooq/codegen/ir/ForeignKeyIR.kt` — FK metadata including builderFunctionName

### Key source files — Tests
- `codegen/src/test/kotlin/com/nickanderssohn/declarativejooq/codegen/CodeGeneratorTest.kt` — compile-and-run codegen tests with embedded Kotlin source strings
- `integration-tests/src/test/kotlin/com/nickanderssohn/declarativejooq/integration/FullPipelineTest.kt` — end-to-end Postgres integration tests
- `dsl-runtime/src/test/kotlin/com/nickanderssohn/declarativejooq/DslExecutionTest.kt` — runtime DSL execution tests

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `ResultEmitter` already generates per-table Result classes (e.g., `AppUserResult`) — these become the placeholder type with no structural changes
- `RecordBuilder.buildWithChildren()` already returns `RecordNode` — just needs to be exposed as a Result instead of discarded
- `TopologicalSorter` implements Kahn's algorithm with cycle detection — extend for cross-tree placeholder edges
- `ForeignKeyIR.builderFunctionName` uses the FK column minus `_id` convention — reuse same naming for placeholder setter properties

### Established Patterns
- Builder properties are generated as `var columnName: Type? = null` by `BuilderEmitter`
- Child builder functions are lambdas stored in `childBlocks` list, executed during `buildWithChildren()`
- `DslScope` extension functions create builder, run block, call `buildWithChildren()`, add to `recordGraph`
- Result classes wrap a single typed record with read-only property accessors for all columns

### Integration Points
- `BuilderEmitter`: add placeholder-accepting properties alongside existing FK column properties
- `DslScopeEmitter`: change return type from Unit to Result type
- `BuilderEmitter` child functions: change return type from Unit to Result type, propagate return from deferred blocks
- `TopologicalInserter`: resolve placeholder FK values at insert time (placeholder's PK → child's FK field), respecting override semantics
- `TopologicalSorter`: incorporate cross-tree placeholder edges into dependency graph
- `ResultAssembler`: ensure placeholder Result instances are the same objects that appear in DslResult
- `RecordNode` or new structure: track placeholder references (which node is referenced by which FK field on which other node)

</code_context>

<specifics>
## Specific Ideas

- The placeholder Result instance must be the **same object** in both the captured variable and the DslResult — not a copy. This means Result objects are created eagerly (wrapping the record at declaration time) and the same instance is used throughout.
- `val alice = appUser { name = "Alice" }` — `alice.name` returns "Alice" immediately; `alice.id` returns null until after `execute()` inserts and refreshes the record.
- Fan-out is a key use case: create one user, use as `createdBy` on many tasks. This is the primary motivator for cross-tree wiring.

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope.

</deferred>

---

*Phase: 06-placeholder-objects*
*Context gathered: 2026-03-16*
