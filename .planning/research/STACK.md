# Technology Stack

**Project:** declarative-jooq
**Researched:** 2026-03-15
**Confidence note:** WebSearch and WebFetch were unavailable during this research session. Versions and rationale are drawn from training data (cutoff August 2025). Items flagged LOW confidence should be verified against Maven Central / official docs before pinning in build files.

---

## Recommended Stack

### Core Language

| Technology | Version | Purpose | Why |
|------------|---------|---------|-----|
| Kotlin | 2.0.x (target 1.9+ compat) | Implementation language | 2.0 is stable as of mid-2024, K2 compiler is production-ready. Project already constrains to 1.9+ compatibility; 2.0 is a strict superset for library authors targeting 1.9 consumers. |
| JVM target | 11 | Bytecode target | Java 11 is the practical floor for Gradle plugin hosts. Gradle 8.x runs on JVM 17+ but compiled plugin code needs JVM 11+ bytecode to stay compatible with older toolchains. |

### Code Generation

| Technology | Version | Purpose | Why |
|------------|---------|---------|-----|
| KotlinPoet | 1.17.x | Generate Kotlin DSL source files | Square's official Kotlin code generation library. Purpose-built for generating `.kt` files — handles imports, type names, nullable/non-nullable markers, lambda parameters, and operator functions correctly. Direct alternative (string templates) produces unmaintainable codegen and is error-prone on edge cases (escaping, imports). KSP codegen libraries (like `ksp-codegen`) are for annotation-processor-style generation at compile time; KotlinPoet is appropriate for our offline/Gradle-task generation pattern. |

**What NOT to use for code generation:**
- **String templates / `buildString`** — zero structural safety, import management is manual, produces unmaintainable codegen code
- **JavaPoet** — generates Java, not Kotlin; produces `.java` files that can't use Kotlin-specific syntax (`data class`, extension functions, `@DslMarker`)
- **KSP (Kotlin Symbol Processing)** — designed for annotation processors that run during the user's own compilation; not appropriate here since we are generating from already-compiled jOOQ classes, not source annotations
- **KAPT** — deprecated path for annotation processing; slow and requires source annotations; wrong tool

### jOOQ Integration

| Technology | Version | Purpose | Why |
|------------|---------|---------|-----|
| jOOQ | 3.18.x (open-source) | Metadata introspection at codegen time | Project constraint is 3.18+. The codegen phase loads user's jOOQ-generated classes via reflection and inspects `Table<R>` implementations. jOOQ's own `org.jooq.Table`, `org.jooq.TableField`, `org.jooq.ForeignKey`, and `org.jooq.UniqueKey` interfaces are the introspection surface. These are stable across 3.x. We depend on jOOQ as `compileOnly` in the runtime library (user brings their own) and as a concrete dependency in the codegen/Gradle plugin module. |

**jOOQ metadata introspection approach:**

jOOQ generated table classes (e.g., `public class User extends TableImpl<UserRecord>`) expose:
- `Table.fields()` — all `TableField<R, T>` instances with name and type
- `Table.getReferences()` — list of `ForeignKey<R, O>` objects
- `ForeignKey.getKey()` — the referenced `UniqueKey`
- `ForeignKey.getKey().getTable()` — the referenced table
- `ForeignKey.getFields()` — the FK columns (single-column FKs for our scope)

These are accessed via reflection by loading compiled jOOQ classes from the user-configured source directory on the Gradle plugin classpath. This is the correct approach — jOOQ's generator already produces rich metadata objects; we do not need to re-parse SQL or read `information_schema`.

**What NOT to use for introspection:**
- **Kotlin reflection (`KClass`)** — jOOQ classes are Java, use Java reflection directly; Kotlin reflection adds overhead with no benefit here
- **jOOQ's `Meta` / `DSLContext.meta()`** — that's for live database connection metadata; we're working from compiled classes, not a database
- **Re-reading the database schema** — requires a live DB connection at codegen time; defeats the purpose of working from generated classes

### Gradle Plugin

| Technology | Version | Purpose | Why |
|------------|---------|---------|-----|
| Gradle | 8.5+ (plugin API target) | Build integration | Gradle 8.x is current. The `java-gradle-plugin` plugin provides the `gradlePlugin { }` DSL for declaring plugin IDs and implementation classes. Use `gradle-plugin` as the plugin type (not a plain `jar`). |
| Gradle Plugin Dev (java-gradle-plugin) | built-in | Plugin scaffolding | The `java-gradle-plugin` Gradle plugin auto-wires `GradlePluginDevelopmentExtension`, generates plugin descriptor JARs, and adds `gradleApi()` to compile classpath automatically — no manual dependency declaration needed. |
| Kotlin Gradle Plugin (for build scripts) | 2.0.x | Kotlin DSL in build scripts | Use `kotlin("jvm")` for the plugin module's own `build.gradle.kts`. |

**Gradle plugin architecture for this project:**

Structure the plugin as a `DefaultTask` subclass with `@TaskAction`. Register it lazily using `project.tasks.register("generateDeclarativeJooq", GenerateDeclarativeDslTask::class.java)`. Expose configuration via a `project.extensions.create("declarativeJooq", DeclarativeJooqExtension::class.java)` extension block so users write:

```kotlin
declarativeJooq {
    sourceDirectory = file("build/generated-sources/jooq")
    outputDirectory = file("build/generated-sources/declarative-jooq")
    packageName = "com.example.testdata"
}
```

Wire the task to run after the user's jOOQ codegen task by hooking `dependsOn` or `mustRunAfter` in the plugin's `apply` block.

**What NOT to do for the Gradle plugin:**
- **`buildSrc` only** — `buildSrc` is fine for local use but the goal is a publishable plugin; use a proper multi-module project with a `:gradle-plugin` subproject
- **Using `project.afterEvaluate` broadly** — defers configuration unnecessarily; prefer lazy task registration with `tasks.register` and use `Provider<T>` for all inputs
- **Eager task creation (`tasks.create`)** — deprecated pattern in Gradle 8; always use `tasks.register` for lazy configuration
- **Putting business logic in the plugin's `apply` method** — `apply` wires tasks and extensions; codegen logic belongs in the `Task` class

### Testing

| Technology | Version | Purpose | Why |
|------------|---------|---------|-----|
| JUnit 5 (Jupiter) | 5.10.x | Unit and integration test runner | Standard for JVM. Gradle 8.x has first-class JUnit 5 support via `useJUnitPlatform()`. |
| Kotest | 5.9.x | Assertion library and test DSL | Kotest assertions (`shouldBe`, `shouldContain`, `shouldHaveSize`) are more readable than raw JUnit assertions for verifying generated code structure. Kotest can run on JUnit 5 engine, so no separate test runner needed. Alternative: Strikt — also good, lighter weight. |
| kotlin-compile-testing (KCT) | 0.4.x or 0.5.x (Jimfs-backed) | Compile and execute generated code in tests | KCT (by Tschuchort) provides `KotlinCompilation` that compiles in-memory Kotlin source. Critical for codegen tests: generate code, compile it in the test, then assert the compiled class behavior — not just string matching. String-matching tests are brittle; compile-and-run tests prove correctness. |
| Gradle TestKit | built-in with Gradle | Integration testing of the Gradle plugin | `GradleRunner` from `gradle-testkit` runs the plugin against a real project in a temp directory. Tests write a minimal `build.gradle.kts` + sample jOOQ classes, invoke the plugin task, and assert on generated output files. This is the standard approach for plugin integration tests. |
| H2 (in-memory DB) | 2.x | Integration tests for the runtime DSL library | Run end-to-end insert tests against H2 without requiring a real Postgres instance. H2's Postgres compatibility mode handles most jOOQ Postgres dialect queries. |
| Testcontainers (optional) | 1.19.x | Full integration tests with real Postgres | Use Testcontainers for a Postgres container in CI to validate behavior against a real dialect. Optional — H2 covers most cases; Testcontainers for FK enforcement and Postgres-specific behavior. |

**What NOT to use for testing:**
- **MockK for jOOQ objects** — jOOQ classes are complex with many interdependencies; use real jOOQ table instances (construct minimal `TableImpl` subclasses) rather than mocks; mocks for jOOQ objects are extremely brittle
- **String comparison for generated code** — use `kotlin-compile-testing` to compile and run; string tests break on formatting changes and don't catch semantic errors
- **Spek** — effectively unmaintained as of 2024; use Kotest if you want a behavior-style DSL

### Build Structure

| Technology | Version | Purpose | Why |
|------------|---------|---------|-----|
| Gradle multi-project build | 8.5+ | Separate runtime lib, codegen engine, Gradle plugin | Three concerns, three modules: `:lib` (runtime DSL), `:codegen` (KotlinPoet-based generator), `:gradle-plugin` (Gradle task/extension wrapper around `:codegen`). This separation means the runtime library does not pull in KotlinPoet as a transitive dependency, and the codegen engine can be tested independently of Gradle. |
| gradle-plugin-publish plugin | 1.2.x | Plugin Portal publishing (future) | Not needed yet (mavenLocal only), but design with it in mind. Using `java-gradle-plugin` + `gradle-plugin-publish` is the standard path to Gradle Plugin Portal. |
| `maven-publish` | built-in | mavenLocal publishing | `publishToMavenLocal` task for local consumption. |

---

## Alternatives Considered

| Category | Recommended | Alternative | Why Not |
|----------|-------------|-------------|---------|
| Code generation | KotlinPoet | String templates | No structural safety, manual import management, brittle on edge cases |
| Code generation | KotlinPoet | JavaPoet | Generates Java, can't produce idiomatic Kotlin DSL syntax |
| Code generation | KotlinPoet | KSP / KAPT | Wrong paradigm — those run during the user's compilation pass; we generate from compiled classes |
| Assertion library | Kotest | AssertJ | AssertJ is Java-first, lacks Kotlin idioms; Kotest assertions are idiomatic |
| Assertion library | Kotest | Strikt | Both are fine; Kotest has broader ecosystem and test DSL support |
| Compile-test | kotlin-compile-testing | String matching | String matching doesn't validate that generated code is valid Kotlin or that it behaves correctly |
| DB for integration tests | H2 (primary) + Testcontainers (optional) | Always real Postgres | H2 is faster for CI; Testcontainers is heavier but needed for dialect-specific edge cases |
| Plugin testing | Gradle TestKit | Unit testing plugin class directly | Plugin logic is inherently coupled to Gradle's project model; TestKit's `GradleRunner` tests the full lifecycle |
| Kotlin reflection | Java reflection (`Class<*>`, `getDeclaredFields`) | `KClass`, `memberProperties` | jOOQ classes are compiled Java; Java reflection is direct, no overhead, and the jOOQ API surface we need (table fields, FK metadata) is Java-typed |

---

## Module Layout

```
declarative-jooq/
  settings.gradle.kts          # include(":lib", ":codegen", ":gradle-plugin")
  build.gradle.kts             # root conventions
  lib/                         # runtime DSL (depends on jOOQ as compileOnly)
    build.gradle.kts
    src/main/kotlin/...
    src/test/kotlin/...        # H2 + Testcontainers tests
  codegen/                     # KotlinPoet-based generator (depends on jOOQ + KotlinPoet)
    build.gradle.kts
    src/main/kotlin/...
    src/test/kotlin/...        # kotlin-compile-testing tests
  gradle-plugin/               # Gradle plugin wrapper (depends on :codegen, gradleApi())
    build.gradle.kts
    src/main/kotlin/...
    src/test/kotlin/...        # Gradle TestKit integration tests
```

---

## Key Version Constraints

| Library | Minimum | Recommended | Notes |
|---------|---------|-------------|-------|
| Kotlin | 1.9.0 | 2.0.21 | Project constraint; use 2.0 for development, publish with 1.9 API compatibility target |
| jOOQ | 3.18.0 | 3.19.x | Project constraint; 3.19 adds some useful `Table` API improvements |
| KotlinPoet | 1.14.0 | 1.17.x | 1.14+ required for stable `FileSpec.builder` context receivers; 1.17 is current stable |
| Gradle API | 8.0 | 8.5+ | 8.0 is practical minimum for lazy configuration APIs used here |
| JUnit 5 | 5.9.0 | 5.10.x | 5.10 adds `@EnabledInNativeImage` and other refinements |
| Kotest | 5.8.0 | 5.9.x | 5.9 supports Kotlin 2.0 |
| kotlin-compile-testing | 0.4.0 | 0.5.x | Check GitHub for latest; actively maintained |
| H2 | 2.2.0 | 2.3.x | 2.x required for modern SQL compatibility |
| Testcontainers | 1.18.0 | 1.19.x | 1.19 adds `@Testcontainers` JUnit 5 extension improvements |

---

## Installation

```kotlin
// lib/build.gradle.kts
dependencies {
    compileOnly("org.jooq:jooq:3.19.x")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.x")
    testImplementation("io.kotest:kotest-assertions-core:5.9.x")
    testImplementation("com.h2database:h2:2.3.x")
    testImplementation("org.jooq:jooq:3.19.x")  // concrete in test scope
}

// codegen/build.gradle.kts
dependencies {
    implementation(project(":lib"))
    implementation("com.squareup:kotlinpoet:1.17.x")
    implementation("org.jooq:jooq:3.19.x")
    testImplementation("com.github.tschuchortdev:kotlin-compile-testing:0.5.x")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.x")
    testImplementation("io.kotest:kotest-assertions-core:5.9.x")
}

// gradle-plugin/build.gradle.kts
plugins {
    `java-gradle-plugin`
    `kotlin("jvm")`
}
dependencies {
    implementation(project(":codegen"))
    testImplementation(gradleTestKit())
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.x")
    testImplementation("io.kotest:kotest-assertions-core:5.9.x")
}
gradlePlugin {
    plugins {
        create("declarativeJooq") {
            id = "io.github.yourname.declarative-jooq"
            implementationClass = "com.example.DeclarativeJooqPlugin"
        }
    }
}
```

---

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| KotlinPoet as the right tool | HIGH | No viable alternative for generating idiomatic Kotlin; has been the standard since ~2019 |
| KotlinPoet 1.17.x version | MEDIUM | Version is based on training data through Aug 2025; verify current release on Maven Central before pinning |
| jOOQ metadata introspection via Table/TableField/ForeignKey APIs | HIGH | These interfaces are stable across all 3.x versions; documented and widely used |
| Gradle java-gradle-plugin pattern | HIGH | Official Gradle recommended approach, stable for years |
| kotlin-compile-testing version | LOW | Actively developed; version 0.4/0.5 range is approximate; check GitHub for latest stable tag |
| Kotest version | MEDIUM | 5.9.x based on training data; verify on Maven Central |
| H2 Postgres compat mode for tests | MEDIUM | H2 2.x Postgres mode covers most jOOQ dialect features but not all; some FK enforcement behaviors differ |
| Multi-module layout | HIGH | Standard pattern for Gradle plugin projects with separate runtime/codegen concerns |

---

## Sources

- Training knowledge through August 2025 (verification with Context7/WebSearch/WebFetch was unavailable during this session)
- Official KotlinPoet documentation: https://square.github.io/kotlinpoet/
- Gradle Plugin Development Guide: https://docs.gradle.org/current/userguide/java_gradle_plugin.html
- jOOQ manual (Table API): https://www.jooq.org/doc/latest/manual/
- kotlin-compile-testing GitHub: https://github.com/tschuchortdev/kotlin-compile-testing
- Kotest documentation: https://kotest.io/docs/assertions/assertions.html
- Gradle TestKit guide: https://docs.gradle.org/current/userguide/test_kit.html
