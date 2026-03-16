---
phase: 04-edge-cases-and-integration
plan: 01
subsystem: codegen
tags: [jooq, kotlin, codegen, kotlinpoet, h2, self-referential-fk, multi-fk]

# Dependency graph
requires:
  - phase: 03-gradle-plugin
    provides: end-to-end codegen pipeline and test infrastructure
provides:
  - Self-referential FK detection via isSelfReferential flag in ForeignKeyIR
  - FK-column-based builder function naming (strips _id suffix)
  - Two-pass insert in TopologicalInserter for self-ref FK resolution
  - childCategory() style DSL for self-referential tables
  - createdBy()/updatedBy() style DSL for multi-FK disambiguation
  - Category and Task tables in test schema covering edge cases
  - Compile-and-run tests proving self-ref and multi-FK insertion against H2
affects:
  - future schema extensions using self-referential or multi-FK patterns

# Tech tracking
tech-stack:
  added: []
  patterns:
    - Self-ref root tables use child-style constructor (nullable parentNode/parentFkField) to serve dual role
    - buildWithChildren() generated on ALL builders (not just those with children) so nested multi-level FK chains always work
    - Two-pass insert: self-ref records inserted with NULL FK on first pass, updated in second pass after PKs known

key-files:
  created:
    - .planning/phases/04-edge-cases-and-integration/04-01-SUMMARY.md
  modified:
    - codegen/src/main/kotlin/com/example/declarativejooq/codegen/ir/ForeignKeyIR.kt
    - codegen/src/main/kotlin/com/example/declarativejooq/codegen/scanner/MetadataExtractor.kt
    - codegen/src/main/kotlin/com/example/declarativejooq/codegen/emitter/BuilderEmitter.kt
    - codegen/src/main/kotlin/com/example/declarativejooq/codegen/emitter/DslScopeEmitter.kt
    - dsl-runtime/src/main/kotlin/com/example/declarativejooq/RecordNode.kt
    - dsl-runtime/src/main/kotlin/com/example/declarativejooq/RecordBuilder.kt
    - dsl-runtime/src/main/kotlin/com/example/declarativejooq/TopologicalInserter.kt
    - dsl-runtime/src/test/kotlin/com/example/declarativejooq/TestSchema.kt
    - codegen/src/test/kotlin/com/example/declarativejooq/codegen/CodeGeneratorTest.kt
    - codegen/src/test/kotlin/com/example/declarativejooq/codegen/ScannerTest.kt

key-decisions:
  - "Self-ref root tables use child-style constructor (recordGraph, parentNode?, parentFkField?, isSelfReferential) instead of root-style (graph) — allows same class to be used at top-level and as self-ref child"
  - "buildWithChildren() generated on ALL builders unconditionally — eliminates bug where intermediate builders with children were called with build() instead of buildWithChildren()"
  - "FK-column-based naming breaks existing test harness: organization_id -> organization (not appUser) — TestHarness updated to reflect new semantics"
  - "DslScopeEmitter emits null-parent constructor call for self-ref root tables"

patterns-established:
  - "Self-referential table pattern: isRoot=true (outboundFKs.none { !it.isSelfReferential }), child-style constructor, childXxx() function naming"
  - "Multi-FK disambiguation: FK column name with _id stripped becomes builder function name (created_by -> createdBy)"
  - "buildWithChildren() on all builders ensures any depth of FK nesting works correctly"

requirements-completed: [CODEGEN-07, CODEGEN-08, DSL-09, DSL-10]

# Metrics
duration: 22min
completed: 2026-03-16
---

# Phase 4 Plan 1: Self-Referential FK and Multi-FK Disambiguation Summary

**Self-ref FK two-pass insert (Electronics->Phones->Smartphones) and multi-FK named builders (createdBy/updatedBy) with compile-and-run H2 validation**

## Performance

- **Duration:** ~22 min
- **Started:** 2026-03-16T23:02:36Z
- **Completed:** 2026-03-16T23:24:21Z
- **Tasks:** 2
- **Files modified:** 10

## Accomplishments

- Added `isSelfReferential` flag to ForeignKeyIR, propagated through MetadataExtractor → BuilderEmitter → RecordNode → TopologicalInserter
- Implemented FK-column-based builder function naming: `organization_id` → `organization`, `created_by` → `createdBy`, `updated_by` → `updatedBy`
- Two-pass insert in TopologicalInserter: self-ref records inserted with NULL FK first, then UPDATEd after parent PK is known
- Self-referential root tables (category) use child-style constructor to serve both as top-level DSL entry point and as self-ref child builder
- `buildWithChildren()` generated on ALL builders unconditionally, fixing latent bug where intermediate builders with children called `build()` instead
- Extended TestSchema with CategoryTable (self-ref) and TaskTable (two FKs to app_user)
- New tests: 3-level self-ref chain verification, multi-FK method name assertion, multi-FK insert validation

## Task Commits

1. **Task 1: IR, MetadataExtractor, and runtime contract changes** - `b7650dc` (feat)
2. **Task 2: BuilderEmitter changes, extended test schema, and compile-and-run tests** - `1e74702` (feat)

## Files Created/Modified

- `codegen/src/main/kotlin/.../ir/ForeignKeyIR.kt` - Added `isSelfReferential: Boolean = false`
- `codegen/src/main/kotlin/.../scanner/MetadataExtractor.kt` - FK-column naming, self-ref detection, isRoot adjustment
- `codegen/src/main/kotlin/.../emitter/BuilderEmitter.kt` - Self-ref constructor pattern, universal buildWithChildren()
- `codegen/src/main/kotlin/.../emitter/DslScopeEmitter.kt` - Null-parent constructor call for self-ref root tables
- `dsl-runtime/src/main/kotlin/.../RecordNode.kt` - Added `isSelfReferential: Boolean = false`
- `dsl-runtime/src/main/kotlin/.../RecordBuilder.kt` - Added `isSelfReferential: Boolean = false`, pass through to RecordNode
- `dsl-runtime/src/main/kotlin/.../TopologicalInserter.kt` - Two-pass insert with second pass for self-ref FK updates
- `dsl-runtime/src/test/kotlin/.../TestSchema.kt` - Added CategoryTable, CategoryRecord, TaskTable, TaskRecord; DDL for both
- `codegen/src/test/kotlin/.../CodeGeneratorTest.kt` - New edge case harness, three new test methods
- `codegen/src/test/kotlin/.../ScannerTest.kt` - Updated table count assertions from 2 to 4

## Decisions Made

- **Self-ref root builder uses child-style constructor**: Rather than adding a secondary constructor or generalized constructor, self-ref tables that are root get the child-style constructor with optional parent params. The DslScope extension function calls it with `parentNode = null, parentFkField = null`. This avoids constructor proliferation.
- **buildWithChildren() on all builders**: Previously only builders with inbound FKs got `buildWithChildren()`. This caused a bug where AppUserBuilder (which gained children when task table was added) was called with `build()` from OrganizationBuilder's child function. Making it universal eliminates this class of bugs.
- **FK-column naming is a breaking change for existing test harness**: The plan claimed `organization_id` → `organization` naming was non-breaking but it changes function name from `appUser` (derived from child table name) to `organization` (derived from FK column). TestHarness updated to use `organization {}` to match new semantics.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] buildWithChildren() generated on ALL builders, not just those with children**
- **Found during:** Task 2 (multipleFkToSameTableInserts test)
- **Issue:** AppUserBuilder gained children (createdBy/updatedBy) from task table addition, but OrganizationBuilder's `organization {}` child function called `builder.build()` not `builder.buildWithChildren()`. Task records were never inserted (0 rows found).
- **Fix:** Generate `childBlocks` and `buildWithChildren()` on ALL builders unconditionally. Always call `buildWithChildren()` from parent child-builder functions.
- **Files modified:** BuilderEmitter.kt
- **Verification:** multipleFkToSameTableInserts test passes with 1 task row inserted
- **Committed in:** 1e74702

**2. [Rule 1 - Bug] Self-ref root builder constructor incompatibility**
- **Found during:** Task 2 (generatedCodeCompiles test)
- **Issue:** CategoryBuilder was root-style (takes `graph`), but child function tried to instantiate it with `(recordGraph, parentNode, parentFkField, isSelfReferential)` — mismatch.
- **Fix:** Self-ref root tables use child-style constructor with nullable parentNode/parentFkField. DslScopeEmitter emits null-parent call.
- **Files modified:** BuilderEmitter.kt, DslScopeEmitter.kt
- **Verification:** generatedCodeCompiles and selfReferentialFkInserts tests pass
- **Committed in:** 1e74702

**3. [Rule 1 - Bug] FK-column naming breaks existing TestHarness**
- **Found during:** Task 1 verification (existing tests failed)
- **Issue:** Plan stated naming change was non-breaking but `app_user.organization_id` → `organization` (FK column) differs from old `appUser` (child table name). TestHarness used `appUser {}`.
- **Fix:** Updated TestHarness in CodeGeneratorTest to use `organization {}` function name.
- **Files modified:** CodeGeneratorTest.kt
- **Verification:** All 5 original tests pass
- **Committed in:** b7650dc

**4. [Rule 1 - Bug] ScannerTest hardcoded table count of 2**
- **Found during:** Task 2 (ScannerTest failures)
- **Issue:** ScannerTest asserted exactly 2 table classes; adding CategoryTable and TaskTable caused it to find 4.
- **Fix:** Updated assertions to expect 4 tables and added assertions for CategoryTable/TaskTable presence.
- **Files modified:** ScannerTest.kt
- **Verification:** ScannerTest passes
- **Committed in:** 1e74702

---

**Total deviations:** 4 auto-fixed (4 Rule 1 bugs)
**Impact on plan:** All fixes necessary for correctness. The self-ref builder constructor fix and buildWithChildren() universality fix are genuine design improvements that prevent future bugs.

## Issues Encountered

- KotlinPoet `callSuperConstructor` can't be used in secondary constructors (they delegate to primary via `this()`). Resolved by changing self-ref root tables to use child-style primary constructor rather than adding a secondary constructor.

## Next Phase Readiness

- All edge cases (self-ref FK, multi-FK) now supported end-to-end with H2 validation
- Phase 4 plan 1 complete — ready for integration testing or additional edge cases if any remain

---
*Phase: 04-edge-cases-and-integration*
*Completed: 2026-03-16*
