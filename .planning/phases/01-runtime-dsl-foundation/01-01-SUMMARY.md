---
phase: 01-runtime-dsl-foundation
plan: 01
subsystem: infra
tags: [kotlin, gradle, jooq, dsl, builder-pattern]

# Dependency graph
requires: []
provides:
  - Three-module Gradle project scaffold (dsl-runtime, codegen, gradle-plugin)
  - Core DSL runtime types: RecordNode, RecordGraph, DslScope, DslResult, RecordBuilder, @DeclarativeJooqDsl
  - dsl-runtime compiles against compileOnly jOOQ 3.19.16 with no KotlinPoet or Gradle API leakage
affects:
  - 01-02 (TopologicalInserter and execute() entry point build on these types)
  - 01-03 (DSL entry point, tests use DslScope and RecordBuilder)
  - 02-codegen (generated code extends RecordBuilder and uses DslScope extension functions)

# Tech tracking
tech-stack:
  added:
    - Kotlin 2.1.20 (JVM target 11)
    - Gradle 8.12 (Kotlin DSL)
    - jOOQ 3.19.16 (compileOnly in dsl-runtime)
    - JUnit Jupiter 5.11.4 (test scope)
    - H2 2.3.232 (test scope)
  patterns:
    - "@DslMarker annotation class for type-safe nested Kotlin DSL builders"
    - "abstract RecordBuilder<R : UpdatableRecord<R>> with build() returning RecordNode"
    - "LinkedHashMap/MutableList for declaration-order preservation throughout"
    - "RecordGraph as ordered node collection with monotonic declarationIndex counter"
    - "compileOnly jOOQ to keep dsl-runtime dependency-light at runtime"

key-files:
  created:
    - settings.gradle.kts
    - build.gradle.kts
    - dsl-runtime/build.gradle.kts
    - codegen/build.gradle.kts
    - gradle-plugin/build.gradle.kts
    - dsl-runtime/src/main/kotlin/com/example/declarativejooq/DeclarativeJooqDsl.kt
    - dsl-runtime/src/main/kotlin/com/example/declarativejooq/RecordNode.kt
    - dsl-runtime/src/main/kotlin/com/example/declarativejooq/RecordGraph.kt
    - dsl-runtime/src/main/kotlin/com/example/declarativejooq/DslScope.kt
    - dsl-runtime/src/main/kotlin/com/example/declarativejooq/RecordBuilder.kt
    - dsl-runtime/src/main/kotlin/com/example/declarativejooq/DslResult.kt
  modified: []

key-decisions:
  - "JVM target 11 bytecode via compilerOptions.jvmTarget (not jvmToolchain) to use available JDK 21 without toolchain download"
  - "JavaCompile also set to sourceCompatibility/targetCompatibility 11 to satisfy Kotlin-Java JVM target consistency check"
  - "RecordNode.record stores UpdatableRecord<*> (not fieldValues map) — record carries its own field values, consistent with jOOQ CRUD API"
  - "DslResult uses LinkedHashMap<String, MutableList<...>> to preserve declaration order per DSL-08"

patterns-established:
  - "RecordBuilder.build() creates RecordNode and auto-adds to parentNode.children — caller does not manage children"
  - "RecordGraph.nextDeclarationIndex() is the single source of truth for ordering — all nodes get unique monotonic indices"
  - "DslScope.recordGraph is the graph accumulator during execute block evaluation"

requirements-completed: [PROJ-01, PROJ-02, PROJ-03, PROJ-04]

# Metrics
duration: 3min
completed: 2026-03-16
---

# Phase 1 Plan 01: Three-module Gradle scaffold with core DSL runtime types

**Compilable three-module Gradle project (dsl-runtime, codegen, gradle-plugin) with six core DSL types: @DeclarativeJooqDsl, RecordNode, RecordGraph, DslScope, RecordBuilder, DslResult — compileOnly jOOQ, no leakage.**

## Performance

- **Duration:** 3 min
- **Started:** 2026-03-16T02:02:11Z
- **Completed:** 2026-03-16T02:05:30Z
- **Tasks:** 2
- **Files modified:** 12

## Accomplishments

- Three-module Gradle 8.12 Kotlin DSL project compiles and `./gradlew projects` lists all three subprojects
- Six core DSL runtime type files created and verified with `./gradlew :dsl-runtime:compileKotlin` — BUILD SUCCESSFUL
- dsl-runtime compile classpath confirmed to contain only jOOQ 3.19.16 (no KotlinPoet, no Gradle API)

## Task Commits

Each task was committed atomically:

1. **Task 1: Create three-module Gradle project scaffold** - `3bdafce` (chore)
2. **Task 2: Create core DSL runtime types** - `241fde2` (feat)

## Files Created/Modified

- `settings.gradle.kts` - rootProject name + include three subprojects
- `build.gradle.kts` - root: kotlin("jvm") 2.1.20, shared subproject config with JVM 11 target
- `dsl-runtime/build.gradle.kts` - compileOnly jOOQ 3.19.16, testImpl H2 + JUnit 5
- `codegen/build.gradle.kts` - Phase 1 scaffold only (empty)
- `gradle-plugin/build.gradle.kts` - Phase 1 scaffold with java-gradle-plugin
- `gradle/wrapper/gradle-wrapper.properties` - Gradle 8.12
- `dsl-runtime/src/main/kotlin/com/example/declarativejooq/DeclarativeJooqDsl.kt` - @DslMarker annotation
- `dsl-runtime/src/main/kotlin/com/example/declarativejooq/RecordNode.kt` - single record representation
- `dsl-runtime/src/main/kotlin/com/example/declarativejooq/RecordGraph.kt` - ordered node collection
- `dsl-runtime/src/main/kotlin/com/example/declarativejooq/DslScope.kt` - execute block receiver
- `dsl-runtime/src/main/kotlin/com/example/declarativejooq/RecordBuilder.kt` - abstract table builder base
- `dsl-runtime/src/main/kotlin/com/example/declarativejooq/DslResult.kt` - LinkedHashMap result container

## Decisions Made

- JVM target 11 bytecode achieved via `compilerOptions.jvmTarget` (not `jvmToolchain`) because only JDK 21 is locally installed; `jvmToolchain(11)` would trigger a toolchain download that failed. Combined with `JavaCompile.sourceCompatibility/targetCompatibility = "11"` to satisfy Kotlin-Java JVM target consistency validation.
- RecordNode stores an `UpdatableRecord<*>` reference rather than a `LinkedHashMap<Field<*>, Any?>` of field values. This design lets builders use jOOQ's typed field setters directly on the record object, which is closer to generated code behavior and avoids a parallel value-copying step before insert.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Fixed JVM toolchain error preventing compilation**

- **Found during:** Task 2 (Create core DSL runtime types — first compile attempt)
- **Issue:** Root `build.gradle.kts` used `jvmToolchain(11)` which triggered Gradle toolchain auto-provisioning. JDK 11 was not locally installed and no toolchain repository was configured, causing `BUILD FAILED: No locally installed toolchains match`.
- **Fix:** Replaced `jvmToolchain(11)` with `compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }` and added `tasks.withType<JavaCompile> { sourceCompatibility = "11"; targetCompatibility = "11" }` to maintain consistency between Kotlin and Java compile tasks.
- **Files modified:** `build.gradle.kts`
- **Verification:** `./gradlew :dsl-runtime:compileKotlin` — BUILD SUCCESSFUL
- **Committed in:** `241fde2` (Task 2 commit)

---

**Total deviations:** 1 auto-fixed (1 blocking)
**Impact on plan:** Essential — the project could not compile without this fix. JVM 11 bytecode target is preserved; only the toolchain mechanism changed.

## Issues Encountered

None beyond the toolchain issue documented above.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Plan 01-02 (TopologicalInserter) can proceed immediately — RecordNode, RecordGraph, and DslScope are all defined
- Plan 01-03 (execute() entry point + integration tests) can proceed — RecordBuilder and DslResult contracts are established
- The `dsl-runtime/src/test/kotlin/com/example/declarativejooq/` directory exists and is ready for test files

---
*Phase: 01-runtime-dsl-foundation*
*Completed: 2026-03-16*
