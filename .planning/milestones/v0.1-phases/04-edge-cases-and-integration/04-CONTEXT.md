# Phase 4: Edge Cases and Integration - Context

**Gathered:** 2026-03-16
**Status:** Ready for planning

<domain>
## Phase Boundary

Self-referential FK support (two-pass insert), multiple-FK disambiguation (named builder functions from FK column), and end-to-end Postgres integration tests via Testcontainers covering the full codegen-to-runtime pipeline.

Requirements in scope: CODEGEN-07, CODEGEN-08, DSL-09, DSL-10, TEST-03

</domain>

<decisions>
## Implementation Decisions

### Self-referential FK DSL shape
- Builder function name is **inverted** for self-referential FKs: `parent_id` → `childCategory()` (child + table name), not `parent()`
- This avoids confusing direction — nesting creates a child record, so the function should read as "child"
- No limit on nesting depth — arbitrary self-referential nesting allowed
- Two-pass insert strategy: insert with NULL FK, then update after ID is generated

### Self-referential FK column constraints
- Self-referential FK columns **must be nullable** — two-pass insert requires inserting with NULL first
- NOT NULL self-referential FK columns are not supported
- Error raised **at runtime** (insert time) when the NULL insert hits the NOT NULL constraint — not at codegen time

### Multiple-FK builder naming
- Builder function names derived from **FK column name** with `_id` suffix stripped: `created_by` → `createdBy()`, `updated_by` → `updatedBy()`
- This rule applies to **all** FK-derived builder functions, not just disambiguated ones — one consistent derivation rule
- For typical FKs like `organization_id`, stripping `_id` gives `organization()` which matches current behavior — non-breaking change
- Each named builder populates the correct FK column based on which function was called

### Postgres integration test scope
- **Full re-validation** against real Postgres: root records, nested records, multiple same-type, multi-level nesting, self-referential FK, multiple FKs to same table, and a mixed graph combining all
- **Full pipeline** tests: Testcontainers Postgres → schema DDL → jOOQ codegen → our CodeGenerator → DSL execution → DB state assertions
- Tests live in a **new `:integration-tests` module** — keeps slow Testcontainers tests isolated from fast unit tests

### Claude's Discretion
- Two-pass insert implementation details (when to detect self-referential FKs in the record graph, update timing)
- jOOQ codegen integration within tests (programmatic jOOQ codegen or pre-compiled classes)
- Testcontainers lifecycle management (per-class vs shared container)
- How `MetadataExtractor` detects and flags self-referential FKs in the IR
- How `BuilderEmitter` implements the inverted naming rule for self-refs vs FK-column naming for non-self-refs

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Runtime contract (insert engine + builder base)
- `dsl-runtime/src/main/kotlin/com/example/declarativejooq/RecordBuilder.kt` — Base class all builders extend; `parentNode`/`parentFkField` pattern needs extension for self-ref two-pass
- `dsl-runtime/src/main/kotlin/com/example/declarativejooq/RecordGraph.kt` — Graph of record nodes; may need self-ref awareness for two-pass insert ordering
- `dsl-runtime/src/main/kotlin/com/example/declarativejooq/DslScope.kt` — Root-level execute block; insert engine logic lives here
- `dsl-runtime/src/main/kotlin/com/example/declarativejooq/RecordNode.kt` — Node in record graph; may need self-ref flag

### Codegen (IR + emitters to modify)
- `codegen/src/main/kotlin/com/example/declarativejooq/codegen/ir/ForeignKeyIR.kt` — FK IR model; `builderFunctionName` derivation needs to change to FK-column-based naming
- `codegen/src/main/kotlin/com/example/declarativejooq/codegen/ir/TableIR.kt` — Table IR; `isRoot` logic needs adjustment for self-referential tables (self-ref FK shouldn't make table non-root)
- `codegen/src/main/kotlin/com/example/declarativejooq/codegen/scanner/MetadataExtractor.kt` — Extracts FK metadata; needs self-ref detection and inverted naming logic
- `codegen/src/main/kotlin/com/example/declarativejooq/codegen/emitter/BuilderEmitter.kt` — Emits builder classes; generates child functions from inbound FKs

### Golden output reference
- `dsl-runtime/src/test/kotlin/com/example/declarativejooq/TestBuilders.kt` — Hand-written builders showing current pattern; Phase 4 extends this with self-ref and multi-FK patterns

### Existing test infrastructure
- `dsl-runtime/src/test/kotlin/com/example/declarativejooq/TestSchema.kt` — H2 schema + jOOQ classes; Postgres tests will need equivalent Postgres schema
- `codegen/src/test/kotlin/com/example/declarativejooq/codegen/CodeGeneratorTest.kt` — Existing codegen tests using Testcontainers Postgres for jOOQ class compilation

### Requirements
- `.planning/REQUIREMENTS.md` — CODEGEN-07, CODEGEN-08, DSL-09, DSL-10, TEST-03

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `RecordBuilder<R>`: Base class with `build()` → `RecordNode` — self-ref two-pass will need a second pass after initial insert
- `MetadataExtractor`: Already extracts single-column FKs and cross-links inbound FKs — needs self-ref detection added
- `BuilderEmitter`: Generates child functions per inbound FK — naming derivation needs to switch from table name to FK column name
- `CodeGeneratorTest`: Already uses Testcontainers Postgres for compiling jOOQ classes — pattern reusable for integration tests

### Established Patterns
- Individual `store()` per record (not batch) — decided in Phase 1 because batch doesn't return generated keys
- Deferred child blocks: `childBlocks: MutableList<(RecordNode) -> Unit>` executed in `buildWithChildren()` after parent node created
- FK column name → builder function: currently `toCamelCase(tableName)`, needs to change to `toCamelCase(fkColumnName)` with `_id` stripping
- `isRoot = outboundFKs.isEmpty()` — self-referential table has an outbound FK to itself, so this logic needs adjustment

### Integration Points
- New `:integration-tests` module added to `settings.gradle.kts`
- Integration tests depend on `:dsl-runtime`, `:codegen`, Testcontainers, and jOOQ codegen
- `DslScope.execute()` insert engine needs self-ref two-pass awareness

</code_context>

<specifics>
## Specific Ideas

- Self-ref naming example: `category { childCategory { childCategory { } } }` for parent → child → grandchild
- Multi-FK naming example: `task { createdBy { name = "Alice" }; updatedBy { name = "Bob" } }`
- Integration tests should be a new module to keep slow tests separate from unit tests
- Full pipeline test flow: Testcontainers Postgres → DDL → jOOQ codegen → CodeGenerator → DSL execute → assert DB state

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 04-edge-cases-and-integration*
*Context gathered: 2026-03-16*
