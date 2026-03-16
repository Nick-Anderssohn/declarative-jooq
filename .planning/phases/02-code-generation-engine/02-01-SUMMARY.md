---
phase: 02-code-generation-engine
plan: 01
subsystem: codegen
tags: [classgraph, kotlinpoet, jooq, reflection, ir, scanner]

# Dependency graph
requires:
  - phase: 01-runtime-dsl-foundation
    provides: TestSchema.kt compiled classes (OrganizationTable, AppUserTable) used as scan input in tests
provides:
  - ClasspathScanner: finds TableImpl and UpdatableRecordImpl subclasses in compiled .class directories via ClassGraph
  - MetadataExtractor: loads table singletons via static field reflection, produces TableIR with columns, FKs, identity, cross-linked inboundFKs
  - TableIR, ColumnIR, ForeignKeyIR: intermediate representation data classes for code emission pipeline
  - codegen/build.gradle.kts: all dependencies declared (KotlinPoet 2.2.0, ClassGraph 4.8.181, jOOQ 3.19.16, kotlin-compile-testing, H2, JUnit)
affects:
  - 02-02 (code emitters use IR data classes and scanner/extractor as input pipeline)
  - 03-gradle-plugin (plugin wraps scanner+extractor+emitters)

# Tech tracking
tech-stack:
  added:
    - "com.squareup:kotlinpoet:2.2.0 — KotlinPoet for IR TypeName field"
    - "io.github.classgraph:classgraph:4.8.181 — ClassGraph for classpath scanning"
    - "com.github.tschuchortdev:kotlin-compile-testing:1.6.0 — compile-testing for future emitter tests"
  patterns:
    - "URLClassLoader with Thread.currentThread().contextClassLoader as parent — ensures jOOQ types resolve at scan time"
    - "Static field reflection for table singleton discovery — companion object val TABLE = TableClass() pattern, NOT INSTANCE field"
    - "Two-pass extraction: build all TableIRs first, then cross-link inboundFKs in second pass"
    - "ClassGraph scan in use{} block to ensure ScanResult is always closed"

key-files:
  created:
    - codegen/src/main/kotlin/com/example/declarativejooq/codegen/ir/TableIR.kt
    - codegen/src/main/kotlin/com/example/declarativejooq/codegen/ir/ColumnIR.kt
    - codegen/src/main/kotlin/com/example/declarativejooq/codegen/ir/ForeignKeyIR.kt
    - codegen/src/main/kotlin/com/example/declarativejooq/codegen/scanner/ClasspathScanner.kt
    - codegen/src/main/kotlin/com/example/declarativejooq/codegen/scanner/MetadataExtractor.kt
    - codegen/src/test/kotlin/com/example/declarativejooq/codegen/ScannerTest.kt
  modified:
    - codegen/build.gradle.kts

key-decisions:
  - "Static field reflection for singleton discovery (not INSTANCE): companion object fields in Kotlin are named by the val name (e.g., ORGANIZATION, APP_USER), not 'INSTANCE'"
  - "URLClassLoader parent is Thread.currentThread().contextClassLoader: ensures jOOQ API types from the test classpath resolve when loading user-compiled classes"
  - "Single-column FK filter: multi-column FKs skipped per plan (rare, disproportionate complexity)"
  - "Two-pass cross-linking: all TableIR objects built first, then inboundFKs populated in second pass to avoid ordering dependency"
  - "buildFieldRefMap uses identity comparison (===) to match TableField instances to declared Java fields on table class"

patterns-established:
  - "IR pipeline: ClasspathScanner -> MetadataExtractor -> List<TableIR> -> code emitters"
  - "camelCase/PascalCase helpers: toCamelCase and toPascalCase in MetadataExtractor are reusable for emitter naming"
  - "TableFieldRefExpression format: ClassName.CONSTANT_NAME.FIELD_NAME (e.g., AppUserTable.APP_USER.ORGANIZATION_ID)"

requirements-completed: [CODEGEN-02]

# Metrics
duration: 2min
completed: 2026-03-16
---

# Phase 2 Plan 01: Scanner and IR Pipeline Summary

**ClassGraph-based classpath scanner + reflection-driven IR extractor producing TableIR/ColumnIR/ForeignKeyIR from compiled jOOQ table classes, validated against dsl-runtime TestSchema**

## Performance

- **Duration:** ~2 min
- **Started:** 2026-03-16T06:18:47Z
- **Completed:** 2026-03-16T06:21:02Z
- **Tasks:** 2
- **Files modified:** 7

## Accomplishments
- Codegen module fully wired with all Phase 2 dependencies (KotlinPoet, ClassGraph, kotlin-compile-testing, H2, JUnit, jOOQ)
- ClasspathScanner finds TableImpl and UpdatableRecordImpl subclasses in .class directories using ClassGraph with optional package filter
- MetadataExtractor extracts complete IR: column names (snake -> camelCase), identity detection, nullable flags, KotlinPoet TypeName mapping, FK extraction, and inboundFK cross-linking
- 4 ScannerTest assertions pass against real compiled TestSchema classes from dsl-runtime

## Task Commits

Each task was committed atomically:

1. **Task 1: Build setup + IR data classes** - `957720f` (feat)
2. **Task 2: ClasspathScanner, MetadataExtractor, and ScannerTest** - `337aa19` (feat)

## Files Created/Modified
- `codegen/build.gradle.kts` - Added all dependencies: KotlinPoet, ClassGraph, jOOQ, kotlin-compile-testing, H2, JUnit, dsl-runtime project ref
- `codegen/src/main/kotlin/com/example/declarativejooq/codegen/ir/TableIR.kt` - Table IR with columns, outboundFKs, inboundFKs, isRoot
- `codegen/src/main/kotlin/com/example/declarativejooq/codegen/ir/ColumnIR.kt` - Column IR with KotlinPoet TypeName, identity, nullable, tableFieldRefExpression
- `codegen/src/main/kotlin/com/example/declarativejooq/codegen/ir/ForeignKeyIR.kt` - FK IR with child/parent table info and builderFunctionName
- `codegen/src/main/kotlin/com/example/declarativejooq/codegen/scanner/ClasspathScanner.kt` - ClassGraph-based scanner for TableImpl and UpdatableRecordImpl subclasses
- `codegen/src/main/kotlin/com/example/declarativejooq/codegen/scanner/MetadataExtractor.kt` - Reflection-based extractor producing complete TableIR list with cross-linked inboundFKs
- `codegen/src/test/kotlin/com/example/declarativejooq/codegen/ScannerTest.kt` - 4 tests validating scanner and extractor against TestSchema

## Decisions Made
- Static field reflection for singleton discovery (not INSTANCE): Kotlin companion objects use the property name (e.g., ORGANIZATION), not a fixed "INSTANCE" name
- URLClassLoader parent is Thread.currentThread().contextClassLoader so jOOQ API types from the running test classpath resolve correctly when loading user classes
- Two-pass extraction ensures all tables exist before cross-linking inboundFKs (avoids ordering dependency)
- Identity comparison (===) used in buildFieldRefMap to safely match TableField instances to declared Java fields

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- IR pipeline (scanner + extractor) is fully operational and tested
- TableIR, ColumnIR, ForeignKeyIR data classes provide all metadata needed by Plan 02 code emitters
- ScannerTest fixture pattern (using dsl-runtime compiled test classes as scan input) is reusable for emitter tests in Plan 02

---
*Phase: 02-code-generation-engine*
*Completed: 2026-03-16*
