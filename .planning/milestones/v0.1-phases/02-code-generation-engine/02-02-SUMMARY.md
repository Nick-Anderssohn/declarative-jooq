---
phase: 02-code-generation-engine
plan: 02
subsystem: codegen
tags: [kotlinpoet, jooq, code-generation, emitter, ir, builder-pattern]

# Dependency graph
requires:
  - phase: 02-code-generation-engine
    plan: 01
    provides: TableIR/ColumnIR/ForeignKeyIR data classes, ClasspathScanner, MetadataExtractor
  - phase: 01-runtime-dsl-foundation
    provides: RecordBuilder, RecordGraph, RecordNode, DslScope, DslResult runtime types
provides:
  - BuilderEmitter: KotlinPoet TypeSpec generator for root and child builder classes
  - ResultEmitter: KotlinPoet TypeSpec generator for typed record accessor wrapper classes
  - DslScopeEmitter: KotlinPoet FunSpec generator for DslScope extension functions (root tables only)
  - DslResultEmitter: KotlinPoet TypeSpec generator for GeneratedDslResult typed result wrapper
  - CodeGenerator: Full pipeline orchestrator — scan -> extract -> emit -> write to disk or return source strings
affects:
  - 02-03 (Gradle plugin wraps CodeGenerator.generate())
  - 02-04 (integration test invokes full pipeline end-to-end)

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Root vs child builder discrimination: isRoot flag determines constructor shape (graph param vs recordGraph/parentNode/parentFkField)"
    - "hasChildren (inboundFKs.isNotEmpty()) gates childBlocks + buildWithChildren + child builder function emission"
    - "DslResultEmitter generates accessor for EVERY table (not just roots) — users may need child record results too"
    - "generateSource() returns List<Pair<String,String>> for kotlin-compile-testing without disk I/O"
    - "One FileSpec per table (builder + result + optional DslScope extension); separate FileSpec for GeneratedDslResult"

key-files:
  created:
    - codegen/src/main/kotlin/com/example/declarativejooq/codegen/emitter/BuilderEmitter.kt
    - codegen/src/main/kotlin/com/example/declarativejooq/codegen/emitter/ResultEmitter.kt
    - codegen/src/main/kotlin/com/example/declarativejooq/codegen/emitter/DslScopeEmitter.kt
    - codegen/src/main/kotlin/com/example/declarativejooq/codegen/emitter/DslResultEmitter.kt
    - codegen/src/main/kotlin/com/example/declarativejooq/codegen/CodeGenerator.kt
  modified: []

key-decisions:
  - "Root builders use private val graph: RecordGraph constructor parameter passed to RecordBuilder superclass as recordGraph; child builders use non-private recordGraph parameter"
  - "DslResultEmitter generates accessors for every table in IR list (not only roots) since callers may need child records"
  - "CodeGenerator.generateSource() returns (filename, source) pairs instead of writing to disk — enables kotlin-compile-testing without temp directories"

patterns-established:
  - "Emitter pattern: emit(tableIR, outputPackage) -> TypeSpec/FunSpec; orchestrator calls each emitter and assembles FileSpec"
  - "BuilderEmitter uses CodeBlock.of with %L for raw tableFieldRefExpression/childFieldExpression strings (not %T/%S which would escape them)"

requirements-completed: [CODEGEN-03, CODEGEN-04, CODEGEN-05, CODEGEN-06]

# Metrics
duration: 5min
completed: 2026-03-16
---

# Phase 2 Plan 02: Code Emitters and CodeGenerator Orchestrator Summary

**KotlinPoet-based emitters producing root/child builder classes, result accessor wrappers, DslScope extension functions, and a typed GeneratedDslResult — wired into a full scan-extract-emit-write pipeline via CodeGenerator**

## Performance

- **Duration:** ~5 min
- **Started:** 2026-03-16T06:22:09Z
- **Completed:** 2026-03-16T06:25:18Z
- **Tasks:** 2
- **Files modified:** 5

## Accomplishments
- BuilderEmitter generates root builders (private graph constructor, childBlocks, buildWithChildren, typed child builder functions per inbound FK) and child/intermediate builders (recordGraph/parentNode/parentFkField constructor, no childBlocks)
- ResultEmitter generates typed property accessor wrappers over UpdatableRecord with nullable getters for all columns
- DslScopeEmitter generates top-level DslScope extension functions calling buildWithChildren() and addRootNode()
- DslResultEmitter generates GeneratedDslResult with typed List<XResult> accessors for every table
- CodeGenerator orchestrates the full pipeline with both generate() (disk output) and generateSource() (in-memory string output for testing)

## Task Commits

Each task was committed atomically:

1. **Task 1: BuilderEmitter and ResultEmitter** - `df04e8d` (feat)
2. **Task 2: DslScopeEmitter, DslResultEmitter, and CodeGenerator orchestrator** - `25f4c26` (feat)

## Files Created/Modified
- `codegen/src/main/kotlin/com/example/declarativejooq/codegen/emitter/BuilderEmitter.kt` - Generates builder TypeSpec per TableIR for root and child builder patterns
- `codegen/src/main/kotlin/com/example/declarativejooq/codegen/emitter/ResultEmitter.kt` - Generates result TypeSpec with nullable property accessors wrapping UpdatableRecord
- `codegen/src/main/kotlin/com/example/declarativejooq/codegen/emitter/DslScopeEmitter.kt` - Generates DslScope extension FunSpec for root tables
- `codegen/src/main/kotlin/com/example/declarativejooq/codegen/emitter/DslResultEmitter.kt` - Generates GeneratedDslResult TypeSpec with per-table list accessors
- `codegen/src/main/kotlin/com/example/declarativejooq/codegen/CodeGenerator.kt` - Orchestrates scan -> extract -> emit -> write pipeline

## Decisions Made
- Root builders receive `graph: RecordGraph` as a private val and pass it to RecordBuilder as `recordGraph = graph`, matching the TestBuilders.kt golden pattern exactly
- DslResultEmitter generates accessors for every table (not only roots) since end-users may want to retrieve child record results after execution
- CodeGenerator provides both `generate()` (writes FileSpec to disk) and `generateSource()` (returns `List<Pair<String, String>>`) — the latter enables kotlin-compile-testing verification without a temp directory
- `%L` format specifier used in CodeBlock for raw tableFieldRefExpression/childFieldExpression strings to avoid KotlinPoet string escaping

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Full codegen pipeline operational: ClasspathScanner -> MetadataExtractor -> [Builder/Result/DslScope/DslResult]Emitter -> FileSpec output
- CodeGenerator.generateSource() method ready for kotlin-compile-testing integration in Plan 03 or 04
- All emitter classes follow the established IR -> KotlinPoet pattern and are individually testable

---
*Phase: 02-code-generation-engine*
*Completed: 2026-03-16*
