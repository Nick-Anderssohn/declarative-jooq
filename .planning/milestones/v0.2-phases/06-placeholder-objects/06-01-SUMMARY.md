---
phase: 06-placeholder-objects
plan: 01
subsystem: database
tags: [kotlin, jooq, topological-sort, fk-resolution, placeholder]

# Dependency graph
requires:
  - phase: 05-child-table-named-builders
    provides: RecordNode, RecordGraph, TopologicalInserter, TopologicalSorter infrastructure
provides:
  - PlaceholderRef data class on RecordNode with targetNode and fkField
  - RecordNode.placeholderRefs mutable list for FK tracking
  - RecordGraph.addPlaceholderRef for registering cross-tree references
  - RecordGraph.registerNode/nodeForRecord for record-to-node lookup
  - TopologicalInserter placeholder FK resolution after parent FK (override semantics)
  - TopologicalInserter cross-tree dependency edges from placeholder refs
  - TopologicalSorter cycle error message mentions placeholder references
affects: [06-02-code-generation, 06-03-integration-tests]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "PlaceholderRef stores targetNode+fkField; iteration happens in TopologicalInserter insert loop"
    - "Placeholder FK overrides parent-context FK for the same field (last-write wins, placeholder applied after parent)"
    - "Cross-tree edges added to table graph in buildTableGraph via graph.placeholderRefs iteration"

key-files:
  created: []
  modified:
    - dsl-runtime/src/main/kotlin/com/nickanderssohn/declarativejooq/RecordNode.kt
    - dsl-runtime/src/main/kotlin/com/nickanderssohn/declarativejooq/RecordGraph.kt
    - dsl-runtime/src/main/kotlin/com/nickanderssohn/declarativejooq/TopologicalInserter.kt
    - dsl-runtime/src/main/kotlin/com/nickanderssohn/declarativejooq/TopologicalSorter.kt

key-decisions:
  - "PlaceholderRef placed on the referencing node (not the placeholder node) — aligns with FK ownership"
  - "buildTableGraph accepts RecordGraph to access global placeholder refs for cross-tree edges"
  - "Placeholder FK resolution runs after parent FK so placeholder value wins on field collision (override semantics per PLCH-04)"
  - "RecordBuilder.kt intentionally NOT modified — will be fully rewritten in Plan 02 Task 2"

patterns-established:
  - "PlaceholderRef pattern: data class on referencing node, iterated in inserter after parent FK resolution"
  - "Cross-tree edges: added via graph.placeholderRefs in buildTableGraph, transparent to TopologicalSorter"

requirements-completed: [PLCH-01, PLCH-02, PLCH-04]

# Metrics
duration: 8min
completed: 2026-03-17
---

# Phase 6 Plan 01: Placeholder Objects Runtime Foundation Summary

**PlaceholderRef tracking on RecordNode and cross-tree FK resolution in TopologicalInserter with override semantics**

## Performance

- **Duration:** ~8 min
- **Started:** 2026-03-17T20:42:00Z
- **Completed:** 2026-03-17T20:50:00Z
- **Tasks:** 2
- **Files modified:** 4

## Accomplishments
- Added PlaceholderRef data class and placeholderRefs list to RecordNode
- Added addPlaceholderRef, registerNode, nodeForRecord to RecordGraph for generated code integration
- TopologicalInserter resolves placeholder FKs at insert time with override semantics (placeholder wins over parent-context for same field)
- Cross-tree dependency edges from placeholder refs are incorporated into topological sort
- Cycle detection error message now mentions placeholder references for clarity

## Task Commits

Each task was committed atomically:

1. **Task 1: Add PlaceholderRef to RecordNode and registration to RecordGraph** - `b6e5d83` (feat)
2. **Task 2: Update TopologicalInserter and TopologicalSorter for placeholder resolution and cross-tree edges** - `84bef78` (feat)

## Files Created/Modified
- `dsl-runtime/src/main/kotlin/com/nickanderssohn/declarativejooq/RecordNode.kt` - Added PlaceholderRef data class and placeholderRefs list
- `dsl-runtime/src/main/kotlin/com/nickanderssohn/declarativejooq/RecordGraph.kt` - Added addPlaceholderRef, registerNode, nodeForRecord, global placeholder refs list
- `dsl-runtime/src/main/kotlin/com/nickanderssohn/declarativejooq/TopologicalInserter.kt` - buildTableGraph accepts RecordGraph; insert loop resolves placeholder FKs after parent FK
- `dsl-runtime/src/main/kotlin/com/nickanderssohn/declarativejooq/TopologicalSorter.kt` - Cycle error message updated to mention placeholder references

## Decisions Made
- PlaceholderRef stored on the referencing node (the one with the FK field), not on the placeholder node — consistent with FK ownership semantics
- buildTableGraph signature extended to accept RecordGraph rather than extracting placeholder refs separately
- RecordBuilder.kt was intentionally left unchanged per plan directive — Plan 02 Task 2 will do a full rewrite

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- Runtime infrastructure for placeholder references is complete
- Plan 02 can now add registerNode to RecordBuilder.build() and implement generated placeholder setters that call addPlaceholderRef
- All 8 existing DslExecutionTest tests pass — no regression from these changes

---
*Phase: 06-placeholder-objects*
*Completed: 2026-03-17*
