---
phase: 01-runtime-dsl-foundation
plan: "03"
subsystem: testing
tags: [jooq, h2, kotlin, junit5, dsl, integration-tests]

# Dependency graph
requires:
  - phase: 01-01
    provides: RecordBuilder, RecordNode, RecordGraph, DslScope base classes
  - phase: 01-02
    provides: TopologicalInserter, TopologicalSorter, ResultAssembler, DslResult, execute()
provides:
  - H2 in-memory test schema with organization and app_user tables
  - Hand-written jOOQ OrganizationTable/AppUserTable (TableImpl subclasses) with identity columns and FK
  - OrganizationRecord/AppUserRecord (UpdatableRecordImpl subclasses) with no-arg constructors
  - OrganizationBuilder and AppUserBuilder simulating Phase 2 codegen output
  - DslScope.organization() extension function as root-level builder entry point
  - 8 integration tests covering DSL-01 through DSL-08 end-to-end
affects: [02-codegen, integration-tests, phase-1-verification]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "No-arg constructor on UpdatableRecordImpl subclasses — required for jOOQ reflective record factory"
    - "DATABASE_TO_UPPER=FALSE in H2 JDBC URL — preserves quoted identifier case matching jOOQ output"
    - "buildWithChildren() pattern — builds parent node first, then evaluates deferred child lambdas with parent node reference"
    - "Deferred child blocks (List<(RecordNode) -> Unit>) — allows child builders to reference the parent's built node"

key-files:
  created:
    - dsl-runtime/src/test/kotlin/com/example/declarativejooq/TestSchema.kt
    - dsl-runtime/src/test/kotlin/com/example/declarativejooq/TestBuilders.kt
    - dsl-runtime/src/test/kotlin/com/example/declarativejooq/DslExecutionTest.kt
  modified: []

key-decisions:
  - "No-arg constructor on record classes: jOOQ's reflective record factory (Tools.recordFactory) requires a no-arg constructor; record class must be declared after its table class to avoid forward-reference at class load time"
  - "DATABASE_TO_UPPER=FALSE: H2 by default uppercases identifier names; jOOQ generates quoted lowercase identifiers; adding DATABASE_TO_UPPER=FALSE to JDBC URL keeps names in declared case"
  - "Deferred child block pattern: child builder lambdas stored as List<(RecordNode) -> Unit> and executed after the parent node is built, ensuring the parent RecordNode exists before child builders reference it"

patterns-established:
  - "Pattern: Hand-written jOOQ TableImpl — private constructor + companion SINGLETON val, override getRecordType/getPrimaryKey/getIdentity/getReferences"
  - "Pattern: Hand-written jOOQ UpdatableRecordImpl — no-arg primary constructor calling super(TABLE_SINGLETON), typed property accessors via get/set on static field references"
  - "Pattern: Builder buildWithChildren() — builds self via build(), iterates deferred child blocks passing built node, returns node to caller for graph registration"

requirements-completed: [DSL-02, DSL-03, DSL-04]

# Metrics
duration: 4min
completed: 2026-03-16
---

# Phase 1 Plan 03: H2 Integration Tests Summary

**End-to-end DSL validation with hand-written jOOQ TableImpl/UpdatableRecordImpl classes and 8 passing integration tests covering the full execute() → FK resolution → DslResult pipeline**

## Performance

- **Duration:** 4 min
- **Started:** 2026-03-16T02:11:41Z
- **Completed:** 2026-03-16T02:15:58Z
- **Tasks:** 2
- **Files modified:** 3 (created)

## Accomplishments
- H2 in-memory test schema with `organization` (parent) and `app_user` (child with FK) tables
- Hand-written `OrganizationTable` and `AppUserTable` extending `TableImpl` with identity columns, primary keys, and FK reference
- Hand-written `OrganizationRecord` and `AppUserRecord` extending `UpdatableRecordImpl` with no-arg constructors (required by jOOQ reflection)
- `OrganizationBuilder` and `AppUserBuilder` simulating the output Phase 2 codegen will produce
- 8 integration tests covering DSL-01 through DSL-08 — all passing, including FK resolution, declaration order preservation, and generated key population

## Task Commits

Each task was committed atomically:

1. **Task 1: H2 test schema and hand-written jOOQ table/record classes** - `188b11c` (feat)
2. **Task 2: Hand-written DSL builders and integration tests** - `ba222b5` (feat)

## Files Created/Modified
- `dsl-runtime/src/test/kotlin/com/example/declarativejooq/TestSchema.kt` — OrganizationTable, AppUserTable, OrganizationRecord, AppUserRecord, TestSchema.createDslContext()
- `dsl-runtime/src/test/kotlin/com/example/declarativejooq/TestBuilders.kt` — OrganizationBuilder, AppUserBuilder, DslScope.organization() extension
- `dsl-runtime/src/test/kotlin/com/example/declarativejooq/DslExecutionTest.kt` — 8 integration tests covering full DSL flow

## Decisions Made
- **No-arg record constructors:** jOOQ's `Tools.recordFactory` instantiates record classes via reflection using a no-arg constructor. Record classes must declare `()` as primary constructor calling `super(TABLE_SINGLETON)`. Declaring record class after its table class avoids forward-reference at class load.
- **DATABASE_TO_UPPER=FALSE:** H2 by default uppercases unquoted identifiers. jOOQ with `SQLDialect.H2` generates quoted lowercase identifiers (e.g., `delete from "app_user"`). The fix is adding `DATABASE_TO_UPPER=FALSE` to the JDBC URL so names stay in the case they were declared in DDL.
- **Deferred child block pattern:** The `OrganizationBuilder.user { }` method stores a `(RecordNode) -> Unit` lambda rather than executing immediately. The parent node must exist before children can reference it as their `parentNode`, so `buildWithChildren()` first calls `build()` then iterates the stored lambdas, passing the built parent node.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed jOOQ reflective record construction failure**
- **Found during:** Task 2 (running integration tests)
- **Issue:** `OrganizationRecord(table: OrganizationTable)` had a table-parameter constructor; jOOQ's `Tools.recordFactory` requires a no-arg constructor — threw `NoSuchMethodException` at `record.store()` time
- **Fix:** Changed both record classes to no-arg primary constructors; moved table reference to static singleton call in `super()`; updated builders to call `OrganizationRecord()` and `AppUserRecord()`
- **Files modified:** TestSchema.kt, TestBuilders.kt
- **Verification:** All 8 integration tests pass
- **Committed in:** ba222b5 (Task 2 commit)

**2. [Rule 1 - Bug] Fixed H2 identifier case mismatch**
- **Found during:** Task 2 (running integration tests — `@BeforeEach cleanTables()`)
- **Issue:** H2 stored table names as uppercase (`APP_USER`) but jOOQ generated quoted lowercase SQL (`delete from "app_user"`) — table not found error
- **Fix:** Added `DATABASE_TO_UPPER=FALSE` to JDBC URL in `TestSchema.createDslContext()`
- **Files modified:** TestSchema.kt
- **Verification:** `@BeforeEach` cleanup succeeded, all integration tests pass
- **Committed in:** ba222b5 (Task 2 commit)

---

**Total deviations:** 2 auto-fixed (both Rule 1 — bugs)
**Impact on plan:** Both fixes were necessary for jOOQ compatibility. No scope creep.

## Issues Encountered
- H2 case-sensitivity: H2's default mode uppercases identifiers, conflicting with jOOQ's quoted identifier generation. Resolved with `DATABASE_TO_UPPER=FALSE`.
- jOOQ reflective record factory: Record classes must have no-arg constructors matching the pattern jOOQ codegen produces. Plan's code examples showed table-parameter constructors which don't match the reflection requirement.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Full DSL runtime validated end-to-end with real H2 database
- All DSL-01 through DSL-08 requirements covered by passing tests
- Hand-written builders establish the exact pattern Phase 2 codegen must reproduce
- Phase 2 (codegen) can proceed: MetadataExtractor + KotlinPoet builder generation + Gradle plugin
- Blocker to verify in Phase 2: H2 Postgres mode `RETURNING` clause (irrelevant now, confirmed `store()` works correctly)

---
*Phase: 01-runtime-dsl-foundation*
*Completed: 2026-03-16*
