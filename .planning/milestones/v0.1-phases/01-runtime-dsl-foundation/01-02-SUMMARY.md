---
phase: 01-runtime-dsl-foundation
plan: 02
subsystem: database
tags: [jooq, kotlin, topological-sort, dsl, graph-algorithm]

# Dependency graph
requires:
  - phase: 01-runtime-dsl-foundation plan 01
    provides: RecordNode, RecordGraph, DslScope, DslResult, RecordBuilder, DeclarativeJooqDsl annotation
provides:
  - TopologicalSorter: Kahn's algorithm sort over FK dependency map, strips self-edges, detects cycles
  - TopologicalInserter: inserts all graph nodes in topological order with FK resolution via individual store()
  - ResultAssembler: wraps inserted records into DslResult preserving declaration order
  - execute() top-level DSL entry point
affects:
  - 01-runtime-dsl-foundation plan 03 (DslExecutionTest integration tests will exercise TopologicalInserter, ResultAssembler, execute())

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Kahn's algorithm for topological sort with self-edge stripping"
    - "Individual record.store() per insert (not batchInsert) to capture generated PKs"
    - "FK resolution inline before each child store() using parent record's PK field value"
    - "LinkedHashMap + sortedBy declarationIndex for declaration-order preservation"

key-files:
  created:
    - dsl-runtime/src/main/kotlin/com/example/declarativejooq/TopologicalSorter.kt
    - dsl-runtime/src/main/kotlin/com/example/declarativejooq/TopologicalInserter.kt
    - dsl-runtime/src/main/kotlin/com/example/declarativejooq/ResultAssembler.kt
    - dsl-runtime/src/main/kotlin/com/example/declarativejooq/Dsl.kt
    - dsl-runtime/src/test/kotlin/com/example/declarativejooq/TopologicalSorterTest.kt
  modified: []

key-decisions:
  - "Used individual store() calls per record rather than batchInsert() — batchInsert does not return generated keys via JDBC, breaking FK chain resolution"
  - "Sorted queue in Kahn's algorithm for deterministic output order across runs"
  - "FK resolution happens immediately before each child's store() call (not in a separate second pass)"

patterns-established:
  - "Pattern: Topological table ordering via Kahn's algorithm — tableGraph maps table name to set of table names it depends on"
  - "Pattern: FK resolution inline in insert loop — read parent PK via record.get(primaryKey.fields[0]), set on child via record.set(fkField, pkValue)"
  - "Pattern: Declaration order preservation — always sortedBy declarationIndex when assembling DslResult"

requirements-completed: [DSL-01, DSL-05, DSL-06, DSL-07, DSL-08]

# Metrics
duration: 2min
completed: 2026-03-16
---

# Phase 1 Plan 2: Topological Sort, Insert Engine, and execute() Entry Point Summary

**Kahn's algorithm topological sorter, FK-resolving insert engine using individual store() calls, and execute(dslContext) { } entry point wiring the full DSL pipeline**

## Performance

- **Duration:** 2 min
- **Started:** 2026-03-16T02:07:43Z
- **Completed:** 2026-03-16T02:09:32Z
- **Tasks:** 2
- **Files modified:** 5

## Accomplishments
- TopologicalSorter with Kahn's algorithm: strips self-edges, detects cycles with clear error, deterministic output
- 7 unit tests covering all sort behaviors (single table, linear chain, independent tables, self-edge, cycle, diamond)
- TopologicalInserter: groups records by topological table order, resolves FK from parent PK before each child insert
- ResultAssembler: wraps all nodes into DslResult using LinkedHashMap, sorted by declarationIndex
- execute() entry point: creates DslScope, runs user lambda, delegates to TopologicalInserter

## Task Commits

Each task was committed atomically:

1. **Task 1: TopologicalSorter with Kahn's algorithm and unit tests** - `fba9a2d` (feat + test, TDD)
2. **Task 2: TopologicalInserter, ResultAssembler, execute() entry point** - `5b19bed` (feat)

**Plan metadata:** _(final docs commit follows)_

_Note: Task 1 used TDD — tests written first (RED), then implementation (GREEN), single commit captures both._

## Files Created/Modified
- `dsl-runtime/src/main/kotlin/com/example/declarativejooq/TopologicalSorter.kt` - Kahn's algorithm sort with self-edge stripping and cycle detection
- `dsl-runtime/src/main/kotlin/com/example/declarativejooq/TopologicalInserter.kt` - FK-resolving insert engine, individual store() per record
- `dsl-runtime/src/main/kotlin/com/example/declarativejooq/ResultAssembler.kt` - wraps nodes into DslResult preserving declaration order
- `dsl-runtime/src/main/kotlin/com/example/declarativejooq/Dsl.kt` - top-level execute() entry point
- `dsl-runtime/src/test/kotlin/com/example/declarativejooq/TopologicalSorterTest.kt` - 7 unit tests for TopologicalSorter

## Decisions Made
- Individual `record.store()` per insert rather than `batchInsert()`: JDBC batch execution does not return generated keys, making FK chain resolution impossible. `store()` uses `getGeneratedKeys()` and auto-populates the identity field back into the record.
- Sorted starting queue in Kahn's algorithm for deterministic sort output (alphabetical tie-breaking when multiple tables have in-degree 0).
- FK resolution inline immediately before each child insert — not in a second pass — because the parent PK is only known after `store()` completes.
- Behavioral verification of TopologicalInserter/ResultAssembler/Dsl.kt deferred to Plan 01-03 integration tests by design (requires H2 test schema and hand-written builders not yet created).

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Core DSL engine complete: sort, insert, FK resolution, result assembly, and execute() entry point all implemented
- Plan 01-03 can now write integration tests (DslExecutionTest) that exercise TopologicalInserter and Dsl.kt against a real H2 database
- No blockers for Plan 01-03

---
*Phase: 01-runtime-dsl-foundation*
*Completed: 2026-03-16*
