---
phase: 06-placeholder-objects
plan: 02
subsystem: codegen+runtime
tags: [kotlin, jooq, codegen, placeholder, builder-pattern]

# Dependency graph
requires:
  - phase: 06-01
    provides: PlaceholderRef, RecordGraph.addPlaceholderRef/registerNode/nodeForRecord
provides:
  - ForeignKeyIR.placeholderPropertyName field (FK col minus _id camelCase)
  - ForeignKeyIR.parentResultClassName, childResultClassName, childRecordClassName, childSourcePackage
  - RecordBuilder.var parentNode (deferred assignment), getOrBuildRecord() memoization
  - PendingPlaceholderRef data class at package level
  - RecordBuilder.pendingPlaceholderRefs list, build() registers node and transfers refs
  - DslScope extension functions returning typed Result objects
  - Child builder functions returning typed Result objects via eager record creation
  - Placeholder-accepting FK properties on builders wired to PendingPlaceholderRef
  - ResultEmitter generates Result.record as internal for cross-package generated code access
affects: [06-03-integration-tests, user-facing-api]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Eager record creation (getOrBuildRecord()) enables Result wrapping before deferred buildWithChildren()"
    - "var parentNode allows deferred parent assignment inside childBlocks lambda"
    - "placeholderPropertyName collision detection: FK columns whose camelCase name equals column property name suppress the raw column property; FK is resolved by TopologicalInserter"
    - "PendingPlaceholderRef held on builder, transferred to graph in build() after registerNode"

key-files:
  created: []
  modified:
    - codegen/src/main/kotlin/com/nickanderssohn/declarativejooq/codegen/ir/ForeignKeyIR.kt
    - codegen/src/main/kotlin/com/nickanderssohn/declarativejooq/codegen/scanner/MetadataExtractor.kt
    - dsl-runtime/src/main/kotlin/com/nickanderssohn/declarativejooq/RecordBuilder.kt
    - codegen/src/main/kotlin/com/nickanderssohn/declarativejooq/codegen/emitter/BuilderEmitter.kt
    - codegen/src/main/kotlin/com/nickanderssohn/declarativejooq/codegen/emitter/DslScopeEmitter.kt
    - codegen/src/main/kotlin/com/nickanderssohn/declarativejooq/codegen/emitter/ResultEmitter.kt
    - codegen/src/main/kotlin/com/nickanderssohn/declarativejooq/codegen/CodeGenerator.kt

key-decisions:
  - "FK columns whose placeholderPropertyName equals the column propertyName (e.g., created_by -> createdBy) suppress the raw Long? column property to avoid Kotlin conflicting declarations; the FK is resolved by TopologicalInserter at insert time"
  - "buildRecord() also skips placeholder-claimed columns so no type mismatch in record construction"
  - "placeholderPropertyName and builderFunctionName use the same toCamelCase(fkColumnName.removeSuffix(\"_id\")) convention, so they may coincide (organization_id case) or differ (created_by case)"

patterns-established:
  - "Placeholder property suppresses raw FK column property when names collide"
  - "Child builder Result wrapping: getOrBuildRecord() eagerly, childBlocks lambda defers buildWithChildren()"

requirements-completed: [PLCH-01, PLCH-02, PLCH-03]

# Metrics
duration: ~4min
completed: 2026-03-17
---

# Phase 6 Plan 02: Placeholder Objects Codegen Summary

**RecordBuilder memoization and codegen emitters generating Result-returning builder functions with placeholder FK properties wired to PendingPlaceholderRef**

## Performance

- **Duration:** ~4 min
- **Started:** 2026-03-17T20:51:47Z
- **Completed:** 2026-03-17T20:55:47Z
- **Tasks:** 3
- **Files modified:** 7

## Accomplishments
- ForeignKeyIR gained 5 new fields: placeholderPropertyName, parentResultClassName, childResultClassName, childRecordClassName, childSourcePackage
- MetadataExtractor computes all new fields from FK column names and table metadata
- RecordBuilder rewritten: var parentNode for deferred assignment, getOrBuildRecord() memoization, PendingPlaceholderRef data class, pendingPlaceholderRefs list, build() registers node and transfers placeholder refs
- DslScope extension functions now return typed Result objects (val org = organization { })
- Child builder functions return typed Result objects using eager getOrBuildRecord() + deferred buildWithChildren()
- Placeholder-accepting properties for outbound FKs wired to PendingPlaceholderRef
- Result.record changed from private to internal for generated code access
- All 16 codegen tests and all 8 dsl-runtime tests pass

## Task Commits

Each task was committed atomically:

1. **Task 1: Add placeholderPropertyName, parentResultClassName, child* fields to ForeignKeyIR** - `3c06327` (feat)
2. **Task 2: Rewrite RecordBuilder with memoization, var parentNode, PendingPlaceholderRef** - `3e228e1` (feat)
3. **Task 3: Update codegen emitters for placeholder support** - `85904db` (feat)

## Files Created/Modified
- `codegen/src/main/kotlin/com/nickanderssohn/declarativejooq/codegen/ir/ForeignKeyIR.kt` - Added 5 new fields for placeholder codegen
- `codegen/src/main/kotlin/com/nickanderssohn/declarativejooq/codegen/scanner/MetadataExtractor.kt` - Computes all new ForeignKeyIR fields; RawFk gains placeholderPropertyName
- `dsl-runtime/src/main/kotlin/com/nickanderssohn/declarativejooq/RecordBuilder.kt` - Full rewrite: var parentNode, getOrBuildRecord(), PendingPlaceholderRef, build() with registerNode + placeholder transfer
- `codegen/src/main/kotlin/com/nickanderssohn/declarativejooq/codegen/emitter/BuilderEmitter.kt` - Child functions return Result; placeholder properties for outbound FKs; collision handling for same-name FK columns
- `codegen/src/main/kotlin/com/nickanderssohn/declarativejooq/codegen/emitter/DslScopeEmitter.kt` - Root functions return Result type
- `codegen/src/main/kotlin/com/nickanderssohn/declarativejooq/codegen/emitter/ResultEmitter.kt` - record property changed to internal
- `codegen/src/main/kotlin/com/nickanderssohn/declarativejooq/codegen/CodeGenerator.kt` - PendingPlaceholderRef and TableField imports for outbound-FK tables

## Decisions Made
- FK columns whose placeholderPropertyName equals the column propertyName (e.g., `created_by` -> `createdBy`) suppress the raw `Long?` column property and skip its `record.set()` call in `buildRecord()`. This avoids Kotlin conflicting declarations. The FK value is resolved by TopologicalInserter at insert time.
- This is consistent with Phase 6 design intent: placeholder assignment is the primary mechanism for these FK columns.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Conflicting declarations when FK column propertyName equals placeholderPropertyName**
- **Found during:** Task 3 — codegen test run
- **Issue:** For FK columns like `created_by` (no `_id` suffix), the column property `var createdBy: Long?` and the placeholder property `var createdBy: AppUserResult?` both generate in the same builder class, causing Kotlin "Conflicting declarations" compilation errors
- **Fix:** Compute `placeholderClaimedNames` set from outbound FK placeholder names; skip raw column properties whose name is in this set; skip their `record.set()` calls in `buildRecord()`
- **Files modified:** `BuilderEmitter.kt`
- **Commit:** included in `85904db`

## Issues Encountered

None beyond the auto-fixed bug above.

## User Setup Required

None.

## Next Phase Readiness
- Generated builders now return Result objects: `val alice = appUser { name = "Alice" }` compiles and runs
- Placeholder assignment syntax `task.createdBy = alice` is generated for all outbound FK properties
- PendingPlaceholderRef is transferred to the graph in build(), ready for TopologicalInserter resolution
- Plan 03 (integration tests) can now write tests using placeholder capture and assignment syntax

---
*Phase: 06-placeholder-objects*
*Completed: 2026-03-17*
