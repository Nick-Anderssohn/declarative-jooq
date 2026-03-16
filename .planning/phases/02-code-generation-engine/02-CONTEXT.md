# Phase 2: Code Generation Engine - Context

**Gathered:** 2026-03-15
**Status:** Ready for planning

<domain>
## Phase Boundary

A standalone code generator that scans a directory of compiled jOOQ classes, extracts table/column/FK metadata through an internal IR, and emits compilable Kotlin DSL builder and result classes using KotlinPoet. Testable entirely without Gradle — the Gradle plugin wiring is Phase 3.

Requirements in scope: CODEGEN-02, CODEGEN-03, CODEGEN-04, CODEGEN-05, CODEGEN-06, TEST-01

</domain>

<decisions>
## Implementation Decisions

### Classpath scanning strategy
- Scan a user-specified directory of compiled `.class` files using a `URLClassLoader`
- Find both `UpdatableRecordImpl` subclasses (records) and `TableImpl` subclasses (table metadata) by walking the package tree
- Table singletons discovered via their static `INSTANCE` field or Kotlin object instance — not derived by naming convention
- FK metadata extracted from `Table.getReferences()` (canonical source: primary keys, foreign keys, identity columns, all fields)
- Optional package filter to restrict scanning scope — scans entire directory by default

### IR model design
- Claude's Discretion — the intermediate representation between scanning and code emission is an internal concern

### Generated API shape
- Claude's Discretion — the emitted code must match the contract established by `TestBuilders.kt` (Phase 1 hand-written builders), but exact KotlinPoet approach is implementation detail

### Test validation
- Must use kotlin-compile-testing for compile-and-run validation per TEST-01 — not string matching or snapshot tests

### Claude's Discretion
- IR model structure and field types
- KotlinPoet code generation patterns
- How to handle edge cases in jOOQ class loading (different jOOQ versions, custom naming strategies)
- Internal error handling and reporting during scan/generation

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Runtime contract (what codegen must target)
- `dsl-runtime/src/main/kotlin/com/example/declarativejooq/RecordBuilder.kt` — Base class all generated builders extend; takes Table<R>, parentNode, parentFkField, RecordGraph
- `dsl-runtime/src/main/kotlin/com/example/declarativejooq/DslScope.kt` — Root-level extension functions go here
- `dsl-runtime/src/main/kotlin/com/example/declarativejooq/DslResult.kt` — Current result type; generated result classes wrap UpdatableRecord<*> with typed accessors
- `dsl-runtime/src/main/kotlin/com/example/declarativejooq/RecordNode.kt` — Node type used in record graph construction
- `dsl-runtime/src/main/kotlin/com/example/declarativejooq/RecordGraph.kt` — Graph that builders register nodes into

### Golden output reference (what generated code should approximate)
- `dsl-runtime/src/test/kotlin/com/example/declarativejooq/TestBuilders.kt` — Hand-written builders showing exact pattern: OrganizationBuilder (root), AppUserBuilder (child with FK), DslScope.organization() extension, buildWithChildren() deferred child pattern

### Test infrastructure (reusable in codegen tests)
- `dsl-runtime/src/test/kotlin/com/example/declarativejooq/TestSchema.kt` — H2 schema + jOOQ table/record classes that can serve as scan input for codegen tests

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `RecordBuilder<R>`: Abstract base class — generated builders extend this with table-specific `buildRecord()` and child builder functions
- `DslScope`: Extension function host — generated root-level functions are `DslScope.tableName()` extensions
- `TestSchema.kt`: Hand-written jOOQ table/record classes that can be compiled and used as scanner input for codegen tests

### Established Patterns
- Deferred child blocks pattern: Parent builders collect `(RecordNode) -> Unit` lambdas, execute them in `buildWithChildren()` after parent node is created
- FK field reference: Child builders receive `parentFkField` (e.g., `AppUserTable.APP_USER.ORGANIZATION_ID`) at construction time
- Property setters: Generated builders use mutable `var` properties (not constructor params), set via `record.set(field, value)` in `buildRecord()`

### Integration Points
- `codegen/build.gradle.kts`: Empty scaffold — needs KotlinPoet dependency, kotlin-compile-testing test dependency
- Generated code output: Kotlin source files that `import com.example.declarativejooq.*` from dsl-runtime
- `codegen` module depends on `dsl-runtime` (for runtime types) but generated code has no dependency on codegen itself

</code_context>

<specifics>
## Specific Ideas

No specific requirements — open to standard approaches

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 02-code-generation-engine*
*Context gathered: 2026-03-15*
