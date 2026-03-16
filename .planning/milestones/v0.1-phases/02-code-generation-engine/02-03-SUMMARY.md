---
phase: 02-code-generation-engine
plan: 03
subsystem: testing
tags: [kotlin-compile-testing, h2, integration-test, codegen, jooq]

# Dependency graph
requires:
  - phase: 02-code-generation-engine/02-02
    provides: CodeGenerator.generateSource(), BuilderEmitter, ResultEmitter, DslScopeEmitter, DslResultEmitter
  - phase: 01-runtime-dsl-foundation
    provides: DslScope, RecordGraph, execute(), DslResult runtime classes
provides:
  - Compile-and-run integration tests proving generated code compiles via kotlin-compile-testing
  - Execution validation: generated builders insert records into H2 database
  - FK resolution validation: child records get correct parent FK values
  - Typed result validation: GeneratedDslResult.organizations() returns OrganizationResult with correct properties
affects:
  - phase 03 (gradle-plugin) — confirms the full codegen pipeline is functionally correct

# Tech tracking
tech-stack:
  added: []
  patterns:
    - TestHarness pattern: inline Kotlin source compiled alongside generated code so all runs in same classloader, reflection only used at test/harness boundary
    - -Xskip-metadata-version-check: allows kotlin-compile-testing (1.9.x compiler) to consume Kotlin 2.x compiled classes
    - Per-call unique H2 DB name (System.nanoTime()) for test isolation without cleanup SQL

key-files:
  created:
    - codegen/src/test/kotlin/com/example/declarativejooq/codegen/CodeGeneratorTest.kt
  modified:
    - codegen/src/main/kotlin/com/example/declarativejooq/codegen/CodeGenerator.kt
    - dsl-runtime/src/main/kotlin/com/example/declarativejooq/DslScope.kt

key-decisions:
  - "TestHarness compiled alongside generated sources: avoids cross-classloader reflection gymnastics, all code in same KotlinCompilation classloader"
  - "DslScope.recordGraph made public (was internal): generated extension functions compiled in a separate module cannot access internal members; necessary for kotlin-compile-testing isolation"
  - "classpaths += classDir in KotlinCompilation: inheritClassPath only includes main jar, not dsl-runtime test classes where AppUserRecord/AppUserTable live"
  - "CodeGenerator.addFkChildTableImports: FK child table class (AppUserTable in OrganizationBuilder) was referenced as raw string in KotlinPoet addStatement, not automatically imported; explicit import added"
  - "Unique H2 DB name per freshContext() call: avoids inter-test contamination since H2 DB_CLOSE_DELAY=-1 reuses same in-memory DB across calls to same URL"

patterns-established:
  - "TestHarness inline source: embed test orchestration as a SourceFile.kotlin() compiled together with generated sources"
  - "KotlinCompilation classpath augmentation: add test-only class directories explicitly when generated code depends on classes not in main jar"

requirements-completed: [TEST-01]

# Metrics
duration: 25min
completed: 2026-03-15
---

# Phase 2 Plan 3: CodeGeneratorTest Summary

**Compile-and-run integration tests using kotlin-compile-testing that prove the full codegen pipeline generates valid, executable Kotlin that inserts records and resolves FKs in H2**

## Performance

- **Duration:** ~25 min
- **Started:** 2026-03-15T06:30:00Z
- **Completed:** 2026-03-15T07:00:00Z
- **Tasks:** 1
- **Files modified:** 3 (1 created, 2 modified)

## Accomplishments
- 5-method CodeGeneratorTest validates: compilation, builder typed properties, DB execution, FK resolution, and typed DslResult accessors
- Fixed missing import bug in BuilderEmitter-generated code (AppUserTable not imported in OrganizationBuilder)
- Fixed DslScope.recordGraph visibility so generated code in separate modules can access it
- Full codegen test suite passes: 9 tests across ScannerTest and CodeGeneratorTest

## Task Commits

1. **Task 1: CodeGeneratorTest with compile-and-run validation** - `6889378` (feat)

**Plan metadata:** (docs commit to follow)

## Files Created/Modified
- `codegen/src/test/kotlin/com/example/declarativejooq/codegen/CodeGeneratorTest.kt` - 5-method integration test class with TestHarness pattern
- `codegen/src/main/kotlin/com/example/declarativejooq/codegen/CodeGenerator.kt` - Added addFkChildTableImports() helper and calls in both generate() and generateSource()
- `dsl-runtime/src/main/kotlin/com/example/declarativejooq/DslScope.kt` - Changed recordGraph from internal to public visibility

## Decisions Made
- **TestHarness inline source:** Compile a `TestHarness` object alongside generated sources so DB operations run in the same KotlinCompilation classloader. Only the outermost invocation crosses the classloader boundary via reflection.
- **DslScope.recordGraph public:** kotlin-compile-testing uses its own compiler instance (Kotlin 1.9.x) and treats the generated code as a separate module. `internal` members are inaccessible across module boundaries even when using `inheritClassPath = true`. Changed to `public` since generated extension functions are explicitly designed to access the scope's record graph.
- **addFkChildTableImports in CodeGenerator:** KotlinPoet's `addStatement()` with raw string expressions doesn't track type references for auto-import. Root builders reference child table classes (e.g., `AppUserTable.APP_USER.ORGANIZATION_ID`) as raw strings — fix adds explicit imports in the FileSpec builder.
- **Unique H2 DB per test:** Used `System.nanoTime()` in JDBC URL to get a fresh database for each `freshContext()` call, avoiding DELETE cleanup between tests.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed missing AppUserTable import in generated OrganizationBuilder.kt**
- **Found during:** Task 1 (generatedCodeCompiles test)
- **Issue:** BuilderEmitter generates `parentFkField = AppUserTable.APP_USER.ORGANIZATION_ID` as a raw string in `addStatement()`. KotlinPoet does not auto-import classes used in raw code strings. The generated `OrganizationBuilder.kt` was missing `import com.example.declarativejooq.AppUserTable`.
- **Fix:** Added `addFkChildTableImports()` helper in `CodeGenerator` that adds explicit imports for all FK child table classes referenced in inbound FK expressions.
- **Files modified:** `codegen/src/main/kotlin/com/example/declarativejooq/codegen/CodeGenerator.kt`
- **Verification:** `generatedCodeCompiles` test passes; generated OrganizationBuilder.kt now compiles cleanly
- **Committed in:** `6889378` (Task 1 commit)

**2. [Rule 1 - Bug] Fixed DslScope.recordGraph internal visibility breaking cross-module access**
- **Found during:** Task 1 (generatedCodeCompiles test)
- **Issue:** `DslScope.recordGraph` was declared `internal`, but kotlin-compile-testing compiles generated code in a separate Kotlin module context. The generated `DslScope.organization()` extension function accesses `recordGraph`, which fails with "Cannot access 'recordGraph': it is internal in 'DslScope'" across module boundaries.
- **Fix:** Changed `internal val recordGraph` to `val recordGraph` (public) in `DslScope.kt`.
- **Files modified:** `dsl-runtime/src/main/kotlin/com/example/declarativejooq/DslScope.kt`
- **Verification:** All 5 CodeGeneratorTest methods pass; dsl-runtime test suite unaffected
- **Committed in:** `6889378` (Task 1 commit)

---

**Total deviations:** 2 auto-fixed (both Rule 1 - Bug)
**Impact on plan:** Both fixes were necessary for correctness. The import bug means generated code wouldn't compile in any real usage; the visibility fix ensures the library's public API supports the generated code pattern as designed.

## Issues Encountered
- kotlin-compile-testing 1.6.0 bundles Kotlin 1.9.24 compiler, but project uses Kotlin 2.1.x. This causes "Incompatible version" warnings and requires `-Xskip-metadata-version-check` to allow the 1.9.x compiler to consume 2.x metadata. This is the known limitation documented in the plan's research notes. All 5 tests pass despite this version mismatch.
- The `inheritClassPath = true` setting does not include `dsl-runtime`'s test classes (only the main jar). Added `classDir` explicitly to `classpaths` so the generated code can resolve `AppUserRecord`, `AppUserTable`, etc.

## Next Phase Readiness
- Full Phase 2 codegen pipeline validated end-to-end: scan → extract IR → emit → compile → execute
- Ready for Phase 3: Gradle plugin that wires the codegen pipeline into a build task
- DslScope.recordGraph is now public (was internal) — this is a conscious API decision for generated code access

---
*Phase: 02-code-generation-engine*
*Completed: 2026-03-15*
