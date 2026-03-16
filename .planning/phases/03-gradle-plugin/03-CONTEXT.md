# Phase 3: Gradle Plugin - Context

**Gathered:** 2026-03-15
**Status:** Ready for planning

<domain>
## Phase Boundary

A Gradle plugin that wires the Phase 2 `CodeGenerator` into a user's build via `apply plugin:` syntax, reads configuration from an extension block, runs generation as a registered task (`generateDeclarativeJooqDsl`), and wires the output directory into the `testImplementation` source set. Publishable to and consumable from `mavenLocal`.

Requirements in scope: CODEGEN-01, TEST-02

</domain>

<decisions>
## Implementation Decisions

### Claude's Discretion
- Extension DSL shape — property names, defaults, required vs optional configuration (must expose at minimum: jOOQ class directory, output package, optional package filter to match `CodeGenerator.generate()` parameters)
- Plugin ID and publishing coordinates (group, artifact) for `gradlePlugin {}` block
- Task name (`generateDeclarativeJooqDsl` per success criteria), input/output declarations for up-to-date checking
- Source set wiring approach — how generated output directory gets added to test compile classpath automatically
- Configuration cache compatibility strategy
- Output directory location (e.g., `build/generated/declarative-jooq`)
- Gradle TestKit test structure and assertions

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### CodeGenerator API (what the plugin invokes)
- `codegen/src/main/kotlin/com/example/declarativejooq/codegen/CodeGenerator.kt` — `generate(classDir: File, outputDir: File, outputPackage: String, packageFilter: String?)` is the entry point the plugin task must call

### Runtime contract (transitive dependency)
- `dsl-runtime/src/main/kotlin/com/example/declarativejooq/` — Runtime types that generated code imports; the consuming project needs `dsl-runtime` on its classpath

### Existing plugin scaffold
- `gradle-plugin/build.gradle.kts` — Current scaffold with `java-gradle-plugin` applied, needs dependencies and `gradlePlugin {}` block
- `settings.gradle.kts` — Already includes `:gradle-plugin` in multi-project build
- `build.gradle.kts` (root) — Kotlin 2.1.20, JVM target 11 across all subprojects

### Success criteria
- `.planning/ROADMAP.md` — Phase 3 success criteria: extension block config, task produces files, auto source set wiring, `--configuration-cache` test, mavenLocal publishable

### Project constraints
- `.planning/PROJECT.md` — mavenLocal only, Gradle plugin (not task), test data focus
- `.planning/REQUIREMENTS.md` — CODEGEN-01 (plugin with extension config), TEST-02 (Gradle TestKit tests)

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `CodeGenerator`: Fully implemented with `generate()` (writes to disk) and `generateSource()` (returns in-memory) — plugin task calls `generate()`
- `ClasspathScanner` + `MetadataExtractor`: Used internally by `CodeGenerator`, no direct plugin interaction needed
- `java-gradle-plugin` already applied in scaffold `build.gradle.kts`

### Established Patterns
- JVM target 11 set via `compilerOptions.jvmTarget` in root `build.gradle.kts` (applies to all subprojects)
- Kotlin 2.1.20 across the project
- `codegen` module depends on `dsl-runtime` — `gradle-plugin` will depend on `codegen` (which transitively pulls `dsl-runtime`)

### Integration Points
- `gradle-plugin/build.gradle.kts` needs: dependency on `:codegen`, `gradlePlugin {}` block, `maven-publish` plugin for mavenLocal
- Consuming project applies plugin, configures extension, runs task — output directory auto-added to test source sets
- Gradle TestKit tests create a temporary project with the plugin applied and verify task execution

</code_context>

<specifics>
## Specific Ideas

No specific requirements — open to standard approaches

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 03-gradle-plugin*
*Context gathered: 2026-03-15*
