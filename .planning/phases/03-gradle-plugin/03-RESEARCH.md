# Phase 3: Gradle Plugin - Research

**Researched:** 2026-03-16
**Domain:** Gradle Plugin Development (java-gradle-plugin, TestKit, configuration cache)
**Confidence:** HIGH

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

None — all implementation decisions are left to Claude's discretion.

### Claude's Discretion

- Extension DSL shape — property names, defaults, required vs optional configuration (must expose at minimum: jOOQ class directory, output package, optional package filter to match `CodeGenerator.generate()` parameters)
- Plugin ID and publishing coordinates (group, artifact) for `gradlePlugin {}` block
- Task name (`generateDeclarativeJooqDsl` per success criteria), input/output declarations for up-to-date checking
- Source set wiring approach — how generated output directory gets added to test compile classpath automatically
- Configuration cache compatibility strategy
- Output directory location (e.g., `build/generated/declarative-jooq`)
- Gradle TestKit test structure and assertions

### Deferred Ideas (OUT OF SCOPE)

None — discussion stayed within phase scope.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| CODEGEN-01 | Gradle plugin with `apply plugin:` syntax and extension configuration for specifying jOOQ source directory and output package | Extension class with `Property<File>` / `DirectoryProperty` + `Property<String>` fields; `gradlePlugin {}` block; `maven-publish` for mavenLocal |
| TEST-02 | Gradle plugin integration tests via Gradle TestKit (GradleRunner) | TestKit dependency via `gradleTestKit()`; `withPluginClasspath()` auto-injection from `java-gradle-plugin`; `--configuration-cache` argument |
</phase_requirements>

---

## Summary

Phase 3 builds the Gradle plugin module (`gradle-plugin`) that bridges the existing `CodeGenerator` (Phase 2) into a user's build. The plugin is a standard binary Gradle plugin using the `java-gradle-plugin` plugin already applied to the scaffold. It exposes a DSL extension block, registers a task named `generateDeclarativeJooqDsl`, and automatically wires the output directory into the consuming project's test source set.

The main technical challenges are: (1) designing the extension using Gradle's lazy Property API for configuration-cache compatibility, (2) correctly wiring the task output directory to the test source set inside the plugin's `apply()` method, and (3) setting up TestKit functional tests with `withPluginClasspath()` and a second run verifying `--configuration-cache`.

The `java-gradle-plugin` plugin does most of the heavy lifting: it generates `plugin-under-test-metadata.properties` automatically so `withPluginClasspath()` works in tests, and it creates the plugin descriptor in the JAR. `maven-publish` adds the `publishToMavenLocal` task for the publishable-to-mavenLocal success criterion.

**Primary recommendation:** Use abstract extension class with `@Inject ObjectFactory` + `DirectoryProperty` / `Property<String>` fields; abstract task class with `@get:InputDirectory`, `@get:OutputDirectory`, `@get:Input` annotations; wire source sets in plugin `apply()` using `project.extensions.getByType(SourceSetContainer::class.java).getByName("test").kotlin.srcDir(outputDir)`; TestKit tests with JUnit5 `@TempDir` and two `GradleRunner` invocations (first run + second run with `--configuration-cache`).

---

## Standard Stack

### Core

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `java-gradle-plugin` (built-in) | Gradle 8.x | Plugin development scaffolding, descriptor generation, test metadata | Required for plugin development; already applied in scaffold |
| `maven-publish` (built-in) | Gradle 8.x | `publishToMavenLocal` task | Standard way to publish to mavenLocal |
| `gradleTestKit()` (built-in) | Gradle 8.x | TestKit dependency — `GradleRunner` API | Included via `gradleTestKit()` dependency declaration |
| `junit-jupiter` | 5.11.4 | JUnit5 test framework for TestKit tests | Already used in codegen module; matches project convention |

### Supporting

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `:codegen` project dependency | local | CodeGenerator class the task invokes | Required — this is what the plugin calls |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| `maven-publish` | `java-gradle-plugin` publishing alone | `java-gradle-plugin` + `maven-publish` together produce both the plugin marker artifact and the implementation artifact; needed for proper plugin resolution |
| Abstract task class | Open class with `@Internal` fields | Abstract + abstract properties is the Gradle-blessed configuration-cache compatible pattern; open classes with regular fields require manual lazy wiring |

**Installation — gradle-plugin/build.gradle.kts additions:**
```kotlin
plugins {
    kotlin("jvm")
    `java-gradle-plugin`
    `maven-publish`
}

dependencies {
    implementation(project(":codegen"))
    testImplementation(gradleTestKit())
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
}

gradlePlugin {
    plugins {
        create("declarativeJooq") {
            id = "com.example.declarative-jooq"
            implementationClass = "com.example.declarativejooq.gradle.DeclarativeJooqPlugin"
        }
    }
}

tasks.test {
    useJUnitPlatform()
}
```

---

## Architecture Patterns

### Recommended Project Structure

```
gradle-plugin/
├── build.gradle.kts                        # gradlePlugin {}, maven-publish, dependencies
└── src/
    ├── main/kotlin/com/example/declarativejooq/gradle/
    │   ├── DeclarativeJooqPlugin.kt         # Plugin<Project> apply()
    │   ├── DeclarativeJooqExtension.kt      # abstract extension class
    │   └── GenerateDeclarativeJooqDslTask.kt # abstract DefaultTask
    └── test/kotlin/com/example/declarativejooq/gradle/
        └── DeclarativeJooqPluginFunctionalTest.kt
```

### Pattern 1: Abstract Extension Class (Configuration Cache Compatible)

**What:** Extension class uses abstract properties backed by Gradle's managed type system. Gradle injects values and handles serialization for the configuration cache.

**When to use:** Always for plugin extensions that need configuration-cache compatibility.

```kotlin
// Source: https://docs.gradle.org/current/userguide/part2_add_extension.html
abstract class DeclarativeJooqExtension {
    // Required: directory containing compiled jOOQ record classes
    abstract val classesDir: DirectoryProperty

    // Required: output package for generated DSL
    abstract val outputPackage: Property<String>

    // Optional: filter to restrict which packages are scanned
    abstract val packageFilter: Property<String>
}
```

The `abstract` keyword causes Gradle to generate a concrete subclass that implements each property backed by a `DefaultProperty`/`DefaultDirectoryProperty`. No `@Inject ObjectFactory` needed when the extension is created via `project.extensions.create<T>()`.

### Pattern 2: Abstract Task Class (Up-to-Date Checking + Configuration Cache)

**What:** Task extends `DefaultTask`, uses abstract properties annotated with `@get:Input` / `@get:InputDirectory` / `@get:OutputDirectory`. Gradle uses annotations for incremental build and cache key computation.

**When to use:** Any task that reads and writes files and should benefit from up-to-date checking.

```kotlin
// Source: https://docs.gradle.org/current/userguide/implementing_custom_tasks.html
abstract class GenerateDeclarativeJooqDslTask : DefaultTask() {

    @get:InputDirectory
    abstract val classesDir: DirectoryProperty

    @get:Input
    abstract val outputPackage: Property<String>

    @get:Input
    @get:Optional
    abstract val packageFilter: Property<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val classDirFile = classesDir.get().asFile
        val outputDirFile = outputDir.get().asFile
        val pkg = outputPackage.get()
        val filter = packageFilter.orNull

        com.example.declarativejooq.codegen.CodeGenerator()
            .generate(classDirFile, outputDirFile, pkg, filter)
    }
}
```

**Critical: do NOT reference `project` inside `@TaskAction`** — this breaks the configuration cache. All inputs must be declared as task properties and wired at configuration time.

### Pattern 3: Plugin apply() — Extension Registration + Task Registration + Source Set Wiring

**What:** The plugin's `apply()` method creates the extension, registers the task wired to the extension, and adds the output directory to the test source set.

**When to use:** This is the complete plugin apply() pattern.

```kotlin
// Source: https://docs.gradle.org/current/userguide/implementing_gradle_plugins.html
class DeclarativeJooqPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        // 1. Create extension
        val extension = project.extensions.create<DeclarativeJooqExtension>("declarativeJooq")

        // 2. Determine output directory (convention)
        val outputDir = project.layout.buildDirectory.dir("generated/declarative-jooq")

        // 3. Register task, wire extension -> task inputs
        val generateTask = project.tasks.register<GenerateDeclarativeJooqDslTask>("generateDeclarativeJooqDsl") {
            classesDir.convention(extension.classesDir)
            outputPackage.convention(extension.outputPackage)
            packageFilter.convention(extension.packageFilter)
            outputDir.convention(outputDir)
            group = "declarative-jooq"
            description = "Generate declarative jOOQ DSL sources"
        }

        // 4. Wire output directory into test source set automatically
        // Use afterEvaluate to ensure java/kotlin plugin is applied first
        project.afterEvaluate {
            val sourceSets = project.extensions
                .findByType(SourceSetContainer::class.java)
            sourceSets?.getByName("test")?.let { testSourceSet ->
                // For Java source set (works for Kotlin too via java compatibility)
                testSourceSet.java.srcDir(generateTask.map { it.outputDir.get().asFile })
            }
        }
    }
}
```

**Note on Kotlin source sets:** If the consuming project applies the Kotlin JVM plugin, use `kotlin.srcDir()` on the Kotlin source set instead of `java.srcDir()`. However, `java.srcDir()` on the test source set is the safe cross-language default — Kotlin compiles all java srcDirs as well. For Kotlin-only projects this is sufficient. The `builtBy` task dependency is implicit when passing a `Provider<File>` mapped from the task.

### Pattern 4: TestKit Functional Test Structure

**What:** JUnit5 tests using `GradleRunner` that write a temporary project, apply the plugin, run the task, and assert on outputs.

**When to use:** All integration tests for the plugin.

```kotlin
// Source: https://docs.gradle.org/current/userguide/test_kit.html
class DeclarativeJooqPluginFunctionalTest {

    @field:TempDir
    lateinit var testProjectDir: File

    private lateinit var buildFile: File
    private lateinit var settingsFile: File

    @BeforeEach
    fun setup() {
        settingsFile = testProjectDir.resolve("settings.gradle.kts")
        buildFile = testProjectDir.resolve("build.gradle.kts")
        settingsFile.writeText("""rootProject.name = "test-project"""")
    }

    @Test
    fun `generateDeclarativeJooqDsl produces sources`() {
        // arrange: write a build that applies the plugin
        buildFile.writeText("""
            plugins {
                id("com.example.declarative-jooq")
                kotlin("jvm")
            }
            declarativeJooq {
                classesDir.set(file("fake-classes"))
                outputPackage.set("com.example.generated")
            }
        """.trimIndent())
        // create a minimal classesDir so the task has something to scan
        testProjectDir.resolve("fake-classes").mkdirs()

        val result = GradleRunner.create()
            .forwardOutput()
            .withPluginClasspath()           // uses plugin-under-test-metadata.properties
            .withProjectDir(testProjectDir)
            .withArguments("generateDeclarativeJooqDsl")
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":generateDeclarativeJooqDsl")?.outcome)
        val outputDir = testProjectDir.resolve("build/generated/declarative-jooq")
        assertTrue(outputDir.exists())
    }

    @Test
    fun `generateDeclarativeJooqDsl is configuration cache compatible`() {
        buildFile.writeText(/* same content as above */"")
        testProjectDir.resolve("fake-classes").mkdirs()

        fun runBuild() = GradleRunner.create()
            .forwardOutput()
            .withPluginClasspath()
            .withProjectDir(testProjectDir)
            .withArguments("generateDeclarativeJooqDsl", "--configuration-cache")
            .build()

        // First run: creates cache entry
        val first = runBuild()
        assertEquals(TaskOutcome.SUCCESS, first.task(":generateDeclarativeJooqDsl")?.outcome)

        // Second run: uses cache entry
        val second = runBuild()
        assertTrue(second.output.contains("Configuration cache entry reused"))
    }
}
```

**`@field:TempDir` syntax:** In Kotlin, JUnit5's `@TempDir` must be annotated as `@field:TempDir` on `lateinit var` because the annotation targets the backing field (HIGH confidence — project uses Kotlin 2.1.20 + JUnit 5.11.4).

### Anti-Patterns to Avoid

- **Referencing `project` inside `@TaskAction`:** Breaks configuration cache. All data must reach the task through annotated properties wired at configuration time.
- **Using `task.doLast { project.sourceSets... }`:** Don't add source dirs from inside a task action. Do it in `plugin.apply()` via `afterEvaluate` or via `project.tasks.configureEach`.
- **Using `tasks.create()` instead of `tasks.register()`:** `create()` eagerly instantiates and configures all tasks even when they're not needed. Always use `register()`.
- **Storing `Project` reference in extension or task fields:** Non-serializable; breaks configuration cache. Use `layout.buildDirectory`, `ObjectFactory`, or `ProjectLayout` injected via `@Inject`.
- **Calling `extension.classesDir.get()` inside `register {}` block without `.convention()`:** Values not yet set at configuration time — use lazy wiring via `.convention()` or `.set()` with Providers.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Plugin descriptor in JAR | Custom META-INF/gradle-plugins writing | `java-gradle-plugin` plugin | Generates plugin descriptor and plugin-under-test-metadata.properties automatically |
| Test classpath injection | Manual classpath file writing | `withPluginClasspath()` | Reads plugin-under-test-metadata.properties generated by `java-gradle-plugin` |
| mavenLocal publishing | Custom file-copy tasks | `maven-publish` + `publishToMavenLocal` | Handles POM generation, artifact layout, plugin marker artifact |
| Up-to-date checking | Manual timestamp comparison | `@get:InputDirectory` / `@get:OutputDirectory` annotations | Gradle computes fingerprints automatically |
| Lazy property serialization | Custom `Serializable` wrappers | `Property<T>`, `DirectoryProperty` (Gradle managed types) | Configuration cache serializes all Gradle managed types automatically |

**Key insight:** The `java-gradle-plugin` + `maven-publish` combination handles all plugin packaging and publishing concerns. The plugin author only needs to write extension, task, and apply logic.

---

## Common Pitfalls

### Pitfall 1: `withPluginClasspath()` Fails Without `java-gradle-plugin`

**What goes wrong:** `GradleRunner.create().withPluginClasspath()` throws "Could not find plugin-under-test-metadata.properties" or the plugin is not found in the test build.

**Why it happens:** `withPluginClasspath()` looks for `plugin-under-test-metadata.properties` on the test runtime classpath — this file is generated by `java-gradle-plugin` at build time.

**How to avoid:** Ensure `java-gradle-plugin` is applied to `gradle-plugin/build.gradle.kts` (already the case in the scaffold). The file is generated automatically when the plugin module is compiled.

**Warning signs:** `ClassNotFoundException` for the plugin implementation class in TestKit tests.

### Pitfall 2: Configuration Cache Failure from `project` Access at Execution Time

**What goes wrong:** Build fails on second run with `--configuration-cache` with error "cannot serialize object of type 'DefaultProject'".

**Why it happens:** Storing a `Project` reference in a task field (directly or via a lambda closure that captures project).

**How to avoid:** All data the task needs must be modeled as annotated task properties (`@get:Input`, `@get:InputDirectory`, etc.) and wired from the extension at configuration time. No `project.xxx` calls inside `@TaskAction`.

**Warning signs:** Configuration cache problem report shows "cannot serialize" errors; second TestKit run fails.

### Pitfall 3: Source Set Wiring Before Plugin Applied

**What goes wrong:** `NoSuchElementException` or `UnknownDomainObjectException` when accessing the "test" source set in `plugin.apply()`.

**Why it happens:** The `java` / `kotlin("jvm")` plugin may not yet be applied when the declarative-jooq plugin's `apply()` runs. Source set container doesn't exist until the java plugin applies.

**How to avoid:** Wrap source set wiring in `project.afterEvaluate { }` OR use `project.plugins.withId("org.jetbrains.kotlin.jvm") { /* wire here */ }` to react to plugin application order-independently.

**Warning signs:** `Extension of type 'SourceSetContainer' does not exist`.

### Pitfall 4: `@TempDir` Annotation Target in Kotlin

**What goes wrong:** JUnit5 `@TempDir` injection fails silently; field remains `null`.

**Why it happens:** Kotlin's annotation processing applies to the property by default, not the backing field. JUnit5 requires the annotation on the field.

**How to avoid:** Use `@field:TempDir` in Kotlin:
```kotlin
@field:TempDir
lateinit var testProjectDir: File
```

**Warning signs:** `NullPointerException` or `UninitializedPropertyAccessException` in test setup.

### Pitfall 5: Plugin Marker Artifact Missing for mavenLocal Consumption

**What goes wrong:** Consuming project using `plugins { id("com.example.declarative-jooq") }` fails to resolve plugin from mavenLocal.

**Why it happens:** Plugin resolution requires a "plugin marker" POM (`com.example.declarative-jooq:com.example.declarative-jooq.gradle.plugin`) in addition to the implementation JAR. `maven-publish` + `java-gradle-plugin` together publish both.

**How to avoid:** Apply both `java-gradle-plugin` and `maven-publish`. The combination automatically creates the plugin marker artifact. Run `./gradlew publishToMavenLocal`.

**Warning signs:** `Plugin with id 'com.example.declarative-jooq' not found` in consuming project despite JAR existing in mavenLocal.

### Pitfall 6: CodeGenerator URLClassLoader Isolation in Task

**What goes wrong:** `ClassNotFoundException` for jOOQ types when running the plugin task against a user's jOOQ class directory.

**Why it happens:** `CodeGenerator` uses `URLClassLoader` (established in Phase 2) where parent must be set to `Thread.currentThread().contextClassLoader` — the same requirement applies when run from a Gradle task. The Gradle worker/daemon classloader may not include jOOQ classes from the consuming project.

**How to avoid:** The task must pass `classesDir.get().asFile` to `CodeGenerator.generate()` which internally constructs the URLClassLoader. The `CodeGenerator` already handles this isolation correctly per Phase 2 decisions — no special handling needed in the task beyond passing the directory.

---

## Code Examples

Verified patterns from official sources:

### Extension Class (Abstract — Gradle Managed)
```kotlin
// Source: https://docs.gradle.org/current/userguide/part2_add_extension.html
abstract class DeclarativeJooqExtension {
    abstract val classesDir: DirectoryProperty
    abstract val outputPackage: Property<String>
    @get:Optional
    abstract val packageFilter: Property<String>
}
```

### Task Class (Configuration Cache Compatible)
```kotlin
// Source: https://docs.gradle.org/current/userguide/implementing_custom_tasks.html
abstract class GenerateDeclarativeJooqDslTask : DefaultTask() {
    @get:InputDirectory  abstract val classesDir: DirectoryProperty
    @get:Input           abstract val outputPackage: Property<String>
    @get:Input @get:Optional abstract val packageFilter: Property<String>
    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        CodeGenerator().generate(
            classesDir.get().asFile,
            outputDir.get().asFile,
            outputPackage.get(),
            packageFilter.orNull
        )
    }
}
```

### gradlePlugin Block in build.gradle.kts
```kotlin
// Source: https://docs.gradle.org/current/userguide/custom_plugins.html
gradlePlugin {
    plugins {
        create("declarativeJooq") {
            id = "com.example.declarative-jooq"
            implementationClass = "com.example.declarativejooq.gradle.DeclarativeJooqPlugin"
        }
    }
}
```

### Source Set Wiring in apply()
```kotlin
// Source: https://docs.gradle.org/current/userguide/java_plugin.html
project.afterEvaluate {
    project.extensions.findByType(SourceSetContainer::class.java)
        ?.getByName("test")
        ?.java
        ?.srcDir(generateTask.flatMap { it.outputDir.map { d -> d.asFile } })
}
```

### TestKit Test Skeleton
```kotlin
// Source: https://docs.gradle.org/current/userguide/test_kit.html
class DeclarativeJooqPluginFunctionalTest {
    @field:TempDir lateinit var testProjectDir: File

    @Test
    fun `task succeeds and outputs files`() {
        testProjectDir.resolve("settings.gradle.kts")
            .writeText("""rootProject.name = "test-project"""")
        testProjectDir.resolve("build.gradle.kts").writeText("""
            plugins { id("com.example.declarative-jooq") }
            declarativeJooq {
                classesDir.set(file("jooq-classes"))
                outputPackage.set("com.example.gen")
            }
        """.trimIndent())
        testProjectDir.resolve("jooq-classes").mkdirs()

        val result = GradleRunner.create()
            .forwardOutput()
            .withPluginClasspath()
            .withProjectDir(testProjectDir)
            .withArguments("generateDeclarativeJooqDsl")
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":generateDeclarativeJooqDsl")?.outcome)
    }
}
```

### Consumer build.gradle.kts (for test fixture project)
```kotlin
// For TestKit fixture — also documents the user-facing API
plugins {
    kotlin("jvm") version "2.1.20"
    id("com.example.declarative-jooq")
}

repositories { mavenLocal(); mavenCentral() }

declarativeJooq {
    classesDir.set(file("path/to/jooq/classes"))
    outputPackage.set("com.example.generated")
    // packageFilter.set("com.example.jooq")  // optional
}
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `tasks.create()` | `tasks.register()` | Gradle 4.9 | Avoids eager task instantiation; use `register` always |
| Open extension class with `var` fields | Abstract extension with `Property<T>` / `DirectoryProperty` | Gradle 6.x | Configuration cache requires Gradle managed types |
| `@Input` on regular field | `@get:Input` on abstract property getter | Gradle 6.x + Kotlin | Kotlin annotation targeting requires `get:` prefix |
| `compile`/`testCompile` configurations | `implementation`/`testImplementation` | Gradle 7.0 | Old configs removed |
| `apply plugin: 'java-gradle-plugin'` (Groovy) | `\`java-gradle-plugin\`` (Kotlin DSL) | Gradle 6.x | Kotlin DSL idiomatic syntax |

**Deprecated/outdated:**
- `task.doFirst`/`doLast` for all logic: Still valid for side effects but not for configuration-cache-compatible task actions — use `@TaskAction` in an abstract class.
- Groovy closures in plugin apply: Project uses Kotlin 2.1.20 throughout; write plugin in Kotlin.

---

## Open Questions

1. **Kotlin source set wiring vs Java source set wiring**
   - What we know: `testSourceSet.java.srcDir(dir)` works for both Java and Kotlin sources in most cases because the Kotlin compiler also picks up directories listed in `java.srcDirs`. The `kotlin.srcDir()` API on `KotlinSourceSet` is more explicit but requires the Kotlin Gradle plugin to be applied before accessing it.
   - What's unclear: Whether the consuming project will always apply `kotlin("jvm")` — the success criteria uses a Kotlin-based project, so `kotlin.srcDir()` should be safe.
   - Recommendation: Use `testSourceSet.java.srcDir(...)` as the default (works regardless of language); document that Kotlin projects may optionally use `kotlin.srcDir()` for explicitness.

2. **Real jOOQ class directory in TestKit tests**
   - What we know: `CodeGenerator.generate()` scans the `classesDir` for classes extending `UpdatableRecordImpl`. An empty directory produces no output files.
   - What's unclear: Whether the TestKit tests should use real compiled jOOQ classes (complex fixture setup) or verify the task runs without error on empty/minimal input.
   - Recommendation: Have two test tiers: (1) a smoke test with an empty classesDir verifying the task runs to completion, (2) an integration test pointing at `dsl-runtime/build/classes/kotlin/test` (same pattern as `CodeGeneratorTest`) to verify actual file generation. The smoke test is sufficient for `--configuration-cache` verification.

---

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 5.11.4 |
| Config file | none — `useJUnitPlatform()` in `tasks.test` |
| Quick run command | `./gradlew :gradle-plugin:test` |
| Full suite command | `./gradlew :gradle-plugin:test` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| CODEGEN-01 | Plugin applies, extension block configures, task runs, files appear in output dir | functional (TestKit) | `./gradlew :gradle-plugin:test` | Wave 0 |
| CODEGEN-01 | Output directory auto-wired to test source set (no manual srcDir in consuming project) | functional (TestKit) — check compilation succeeds | `./gradlew :gradle-plugin:test` | Wave 0 |
| CODEGEN-01 | Plugin publishable to mavenLocal (publishToMavenLocal succeeds) | smoke — `./gradlew :gradle-plugin:publishToMavenLocal` | `./gradlew :gradle-plugin:publishToMavenLocal` | Wave 0 |
| TEST-02 | GradleRunner test: task produces DSL files, `TaskOutcome.SUCCESS` | functional (TestKit) | `./gradlew :gradle-plugin:test` | Wave 0 |
| TEST-02 | GradleRunner test: second run with `--configuration-cache` uses cache entry | functional (TestKit) | `./gradlew :gradle-plugin:test` | Wave 0 |

### Sampling Rate

- **Per task commit:** `./gradlew :gradle-plugin:test`
- **Per wave merge:** `./gradlew :gradle-plugin:test`
- **Phase gate:** Full suite green + `publishToMavenLocal` succeeds before `/gsd:verify-work`

### Wave 0 Gaps

- [ ] `gradle-plugin/src/test/kotlin/com/example/declarativejooq/gradle/DeclarativeJooqPluginFunctionalTest.kt` — covers CODEGEN-01, TEST-02
- [ ] Test fixture project files (written by test code to `@TempDir`) — no separate file needed, tests create them dynamically
- [ ] No new test framework install needed — `gradleTestKit()` + `junit-jupiter:5.11.4` already used in sibling modules

---

## Sources

### Primary (HIGH confidence)

- [Gradle Binary Plugins](https://docs.gradle.org/current/userguide/implementing_gradle_plugins_binary.html) — extension + task + gradlePlugin block patterns
- [Gradle Implementing Plugins](https://docs.gradle.org/current/userguide/implementing_gradle_plugins.html) — lazy properties, configuration cache best practices
- [Gradle Part 2: Add an Extension](https://docs.gradle.org/current/userguide/part2_add_extension.html) — abstract extension + convention wiring
- [Gradle Custom Tasks](https://docs.gradle.org/current/userguide/implementing_custom_tasks.html) — abstract task, @get:Input annotations
- [Gradle TestKit](https://docs.gradle.org/current/userguide/test_kit.html) — GradleRunner, withPluginClasspath, configuration cache testing
- [Gradle Testing Plugins](https://docs.gradle.org/current/userguide/testing_gradle_plugins.html) — functional test structure, @TempDir pattern
- [Gradle Configuration Cache Requirements](https://docs.gradle.org/current/userguide/configuration_cache_requirements.html) — what is forbidden at execution time
- [Gradle Java Plugin](https://docs.gradle.org/current/userguide/java_plugin.html) — source set srcDir API

### Secondary (MEDIUM confidence)

- [Hemaks: Developing Gradle Plugins with Kotlin](https://hemaks.org/posts/developing-gradle-plugins-with-kotlin-a-step-by-step-guide/) — verified against official docs; full plugin + TestKit example
- [Gradle Part 6: Functional Test](https://docs.gradle.org/current/userguide/part6_functional_test.html) — GradleRunner + @TempDir verified pattern

### Tertiary (LOW confidence)

- Phase 2 project decisions (STATE.md) — URLClassLoader parent must be contextClassLoader; applies equally to plugin task context

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — `java-gradle-plugin`, `maven-publish`, `gradleTestKit()` are official Gradle built-ins; versions confirmed from existing project files
- Architecture: HIGH — Abstract extension + abstract task pattern confirmed via official Gradle docs; configuration cache requirements confirmed
- Pitfalls: HIGH for items 1–5 (confirmed via official docs and project history); MEDIUM for item 6 (inferred from Phase 2 URLClassLoader decision)

**Research date:** 2026-03-16
**Valid until:** 2026-09-16 (stable Gradle APIs; re-verify if upgrading beyond Gradle 8.x)
