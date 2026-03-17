---
phase: 05-child-table-named-builder-functions
plan: 01
subsystem: codegen
tags: [kotlin, jooq, code-generation, naming, metadata]

requires: []
provides:
  - Two-pass builder function naming algorithm in MetadataExtractor
  - NAME-01: FK col stripped matches parent table name -> use child table name (appUser)
  - NAME-02: FK col stripped does not match parent table name -> use FK column name (createdBy)
  - NAME-03: Collision detection - two FKs producing same candidate both fall back to FK col name
  - NAME-04: Self-referential FK uses table name (category not childCategory)
  - Four naming-specific tests in ScannerTest covering all four naming rules
affects: [05-child-table-named-builder-functions]

tech-stack:
  added: []
  patterns:
    - "Two-pass FK naming: collect candidates in pass 1, detect collisions in pass 2"
    - "Local data class (RawFk) scoped inside lambda to hold intermediate FK data"

key-files:
  created: []
  modified:
    - codegen/src/main/kotlin/com/nickanderssohn/declarativejooq/codegen/scanner/MetadataExtractor.kt
    - codegen/src/test/kotlin/com/nickanderssohn/declarativejooq/codegen/ScannerTest.kt

key-decisions:
  - "Self-ref FKs use toCamelCase(tableName) - removes childCategory prefix entirely"
  - "Primary rule: exact snake_case comparison fkColumnName.removeSuffix(_id) == parentTableName determines child-table vs FK-col naming"
  - "Collision detection uses groupingBy + eachCount to find duplicates; collidingNames set drives fallback"
  - "RawFk local data class scoped inside map lambda avoids polluting public API"

patterns-established:
  - "Two-pass naming: compute candidates first, then resolve collisions - enables NAME-03 without lookahead"

requirements-completed: [NAME-01, NAME-02, NAME-03, NAME-04]

duration: 2min
completed: 2026-03-17
---

# Phase 5 Plan 1: Two-Pass Builder Function Naming Summary

**Two-pass FK naming algorithm in MetadataExtractor producing child-table-named builders (appUser) with FK-column fallback (createdBy) and self-ref table name (category), validated by four ScannerTest naming tests.**

## Performance

- **Duration:** ~2 min
- **Started:** 2026-03-17T04:16:50Z
- **Completed:** 2026-03-17T04:18:07Z
- **Tasks:** 2
- **Files modified:** 2

## Accomplishments
- Replaced inline single-pass naming (`"child" + toPascalCase`) with a two-pass algorithm
- Pass 1 computes candidate names per NAME-01/02/04 rules using exact snake_case comparison
- Pass 2 detects duplicate candidates via groupingBy and applies FK-column fallback per NAME-03
- Four new ScannerTest methods cover all four naming requirements

## Task Commits

Each task was committed atomically:

1. **Task 1: Implement two-pass naming algorithm in MetadataExtractor** - `4d22f50` (feat)
2. **Task 2: Add naming-specific unit tests to ScannerTest** - `831adf5` (test)

## Files Created/Modified
- `codegen/src/main/kotlin/com/nickanderssohn/declarativejooq/codegen/scanner/MetadataExtractor.kt` - Two-pass builder function naming replacing old inline single-pass logic
- `codegen/src/test/kotlin/com/nickanderssohn/declarativejooq/codegen/ScannerTest.kt` - Four new naming tests: childTableNameWins, fkColumnFallback, selfRefUsesTableName, collisionFallsBackToFkColumnNames

## Decisions Made
- Used a local `data class RawFk` scoped inside the map lambda to hold intermediate FK data without polluting the public API
- Collision detection uses `groupingBy { it.candidateName }.eachCount()` and filters for `> 1` to build a `collidingNames` set

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- MetadataExtractor now produces correct builderFunctionName values per all four NAME requirements
- Downstream: CodeGeneratorTest.kt and FullPipelineTest.kt test harness strings still reference old names (`organization { }`, `childCategory { }`) and will need updating in the next plan
- All ScannerTest tests (8 total: 4 existing + 4 new) pass

---
*Phase: 05-child-table-named-builder-functions*
*Completed: 2026-03-17*
