---
phase: 03-gradle-plugin
plan: 01
subsystem: infra
tags: [gradle, kotlin, gradle-plugin, maven-publish, java-gradle-plugin, codegen]

# Dependency graph
requires:
  - phase: 02-code-generation-engine
    provides: CodeGenerator.generate() API that the Gradle task invokes
provides:
  - com.example.declarative-jooq Gradle plugin applicable via `apply plugin:` syntax
  - DeclarativeJooqExtension DSL with classesDir, outputPackage, packageFilter
  - GenerateDeclarativeJooqDslTask with @InputDirectory/@OutputDirectory for up-to-date checking
  - DeclarativeJooqPlugin.apply() wires extension -> task -> test source set
  - Plugin publishable to mavenLocal
affects: [03-gradle-plugin-tests, any consuming Gradle project]

# Tech tracking
tech-stack:
  added: [java-gradle-plugin, maven-publish, gradleTestKit]
  patterns: [lazy task registration with convention(), afterEvaluate source set wiring, abstract managed properties for configuration cache safety]

key-files:
  created:
    - gradle-plugin/src/main/kotlin/com/example/declarativejooq/gradle/DeclarativeJooqExtension.kt
    - gradle-plugin/src/main/kotlin/com/example/declarativejooq/gradle/GenerateDeclarativeJooqDslTask.kt
    - gradle-plugin/src/main/kotlin/com/example/declarativejooq/gradle/DeclarativeJooqPlugin.kt
  modified:
    - gradle-plugin/build.gradle.kts

key-decisions:
  - "abstract class (not open) for Extension and Task so Gradle generates concrete managed subclass with Property backing"
  - "DirectoryProperty for classesDir (not Property<File>) — enables configuration cache serialization and proper up-to-date checking"
  - "tasks.register() (lazy) not tasks.create() (eager) — standard Gradle best practice to avoid unnecessary task configuration"
  - "convention() wiring from extension to task — values resolved at execution time, not configuration time"
  - "afterEvaluate for test source set wiring — handles plugin ordering when java/kotlin plugin may apply after this plugin"
  - "generateTask.flatMap { it.outputDir } creates a lazy Provider establishing an implicit task dependency on generateDeclarativeJooqDsl"

patterns-established:
  - "Gradle managed properties: abstract class with abstract val properties; Gradle generates concrete subclass"
  - "Configuration cache safety: no Project reference in @TaskAction; all inputs are abstract properties wired at configuration time"
  - "Source set wiring: testSourceSet.java.srcDir() works for both Java and Kotlin sources"

requirements-completed: [CODEGEN-01]

# Metrics
duration: 8min
completed: 2026-03-16
---

# Phase 3 Plan 01: Gradle Plugin Production Code Summary

**Gradle plugin `com.example.declarative-jooq` with extension DSL, Gradle-managed task invoking CodeGenerator, and automatic test source set wiring — publishable to mavenLocal**

## Performance

- **Duration:** 8 min
- **Started:** 2026-03-16T22:19:17Z
- **Completed:** 2026-03-16T22:27:00Z
- **Tasks:** 2
- **Files modified:** 4

## Accomplishments
- Full `gradle-plugin/build.gradle.kts` with `java-gradle-plugin`, `maven-publish`, `:codegen` dependency, JUnit 5 test setup
- `DeclarativeJooqExtension` abstract class with three Gradle managed properties (classesDir, outputPackage, packageFilter)
- `GenerateDeclarativeJooqDslTask` with properly annotated `@InputDirectory`, `@OutputDirectory`, `@Input`, `@Optional` properties invoking `CodeGenerator().generate()`
- `DeclarativeJooqPlugin` wiring extension to task via `convention()`, auto-wiring output dir to test source set via `afterEvaluate`
- Plugin descriptor generated at `gradle-plugin/build/pluginDescriptors/com.example.declarative-jooq.properties`
- `publishToMavenLocal` succeeds

## Task Commits

Each task was committed atomically:

1. **Task 1: Configure build.gradle.kts and create Extension class** - `109a40d` (feat)
2. **Task 2: Create Task class and Plugin class with source set wiring** - `d622d12` (feat)

**Plan metadata:** (docs commit follows)

## Files Created/Modified
- `gradle-plugin/build.gradle.kts` - Added maven-publish, gradlePlugin block, implementation(project(":codegen")), gradleTestKit(), JUnit 5
- `gradle-plugin/src/main/kotlin/com/example/declarativejooq/gradle/DeclarativeJooqExtension.kt` - Abstract extension class with DirectoryProperty and Property<String> fields
- `gradle-plugin/src/main/kotlin/com/example/declarativejooq/gradle/GenerateDeclarativeJooqDslTask.kt` - Task class with annotated managed properties, @TaskAction calling CodeGenerator
- `gradle-plugin/src/main/kotlin/com/example/declarativejooq/gradle/DeclarativeJooqPlugin.kt` - Plugin entry point wiring extension, task, and test source set

## Decisions Made
- Used abstract class pattern (not open) so Gradle generates a concrete managed subclass — no `@Inject ObjectFactory` needed
- `DirectoryProperty` for classesDir rather than `Property<File>` for proper configuration cache serialization
- `tasks.register()` lazy API to avoid unnecessary task configuration at configuration time
- `afterEvaluate` for test source set wiring to handle plugin application ordering
- `generateTask.flatMap { it.outputDir }` creates a lazy Provider establishing an implicit task dependency

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Plugin is compiled and published to mavenLocal, ready for Plan 02 TestKit integration tests
- All four source files compile without errors
- Plugin descriptor at `gradle-plugin/build/pluginDescriptors/com.example.declarative-jooq.properties` confirms correct registration

---
*Phase: 03-gradle-plugin*
*Completed: 2026-03-16*
