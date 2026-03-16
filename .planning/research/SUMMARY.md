# Project Research Summary

**Project:** declarative-jooq
**Domain:** Kotlin code-generated DSL library with Gradle plugin for jOOQ test data setup
**Researched:** 2026-03-15
**Confidence:** MEDIUM (no external tools available; findings from training knowledge through August 2025)

## Executive Summary

declarative-jooq is a Kotlin DSL library that eliminates manual test data setup in jOOQ-based projects. The library solves the FK wiring problem — the single biggest pain point in test data management — by introspecting jOOQ's own generated table metadata at build time, emitting a type-safe builder DSL, and resolving FK relationships automatically at runtime. The recommended approach is a three-module Gradle project: a `dsl-runtime` module (ships to users' test classpath), a `codegen` module (inspects jOOQ classes via reflection and emits Kotlin source via KotlinPoet), and a `gradle-plugin` module (thin wiring layer that invokes codegen as a build step). This strict module separation is non-negotiable: it prevents Gradle API leakage into testable code and keeps the runtime library free of code-generation dependencies.

The core architectural bet is code-generation over reflection. Unlike Instancio or EasyRandom, which use runtime reflection and lose type safety, this library generates a per-table DSL at build time, giving users compile-time errors for nonexistent columns, IDE autocomplete, and zero-surprise FK resolution. The codegen phase loads user-compiled jOOQ classes via an isolated `URLClassLoader`, extracts table/column/FK metadata from jOOQ's own API (`table.fields()`, `table.getReferences()`), builds a jOOQ-free internal IR, and feeds it to KotlinPoet for emission. The runtime phase executes a topological sort over the declared record graph, batches inserts by table, and refreshes records after insert to capture DB-generated defaults.

The two most critical risks are classloader isolation and the batch-insert-refresh problem. If user jOOQ classes are loaded into the Gradle daemon classloader rather than an isolated `URLClassLoader`, the result is classloader leaks, version conflicts, and corrupted daemon state — a rewrite-level mistake. If batch inserts are used naively, DB-generated primary keys are not returned, breaking FK resolution for child records. Both must be designed correctly before any code is written; retrofitting them is expensive. Self-referential FKs (tree structures) require a two-pass insert strategy (insert with null parent, then update) to avoid topological sort cycle detection failures.

## Key Findings

### Recommended Stack

The stack is well-defined with few controversial choices. Kotlin 2.0 with JVM 11 bytecode target is the right language choice for a library that must be consumable by Kotlin 1.9+ projects. KotlinPoet 1.17.x is the only viable code generation tool — string templates lack structural safety, JavaPoet generates Java not Kotlin, and KSP/KAPT are annotation-processor paradigms that don't apply here. The three-module Gradle multi-project structure mirrors what well-established Kotlin tooling projects (ktlint-gradle, detekt) use.

For testing, the research identifies a three-layer strategy: `kotlin-compile-testing` for codegen correctness (generate code, compile it in-test, assert behavior), Gradle TestKit for plugin integration (real Gradle build in temp dir), and H2 in Postgres compatibility mode plus optional Testcontainers for runtime DSL validation. The key insight is that string-matching tests for generated code are unacceptably brittle — only compile-and-run tests prove semantic correctness.

**Core technologies:**
- **Kotlin 2.0 / JVM 11**: Implementation language — stable K2 compiler, JVM 11 bytecode for maximum toolchain compatibility
- **KotlinPoet 1.17.x**: Code generation — only tool that handles Kotlin-specific syntax, nullable types, and import management correctly
- **jOOQ 3.18+ (compileOnly in runtime)**: Metadata surface — `Table.fields()`, `Table.getReferences()`, `ForeignKey` API used at codegen time; user brings their own version at runtime
- **Gradle 8.5+ with `java-gradle-plugin`**: Build integration — lazy task registration, `Property<T>` for configuration, `java-gradle-plugin` for plugin descriptor generation
- **JUnit 5 + Kotest assertions**: Test runner and assertion DSL — Kotest's Kotlin-idiomatic assertions are significantly more readable than raw JUnit for verifying generated code structure
- **kotlin-compile-testing 0.5.x**: Codegen test correctness — in-memory Kotlin compilation validates generated code semantics, not just string output
- **Gradle TestKit**: Plugin integration testing — `GradleRunner` against real builds in temp directories
- **H2 2.x (Postgres mode) + Testcontainers 1.19.x**: Runtime DSL integration tests — H2 for fast CI, Testcontainers for Postgres dialect edge cases

### Expected Features

The research is opinionated about MVP scope. Eight features constitute an unshippable product if absent; three are explicitly deferrable without blocking adoption; the remaining differentiators (self-referential FK, multiple-FK disambiguation) are high-value but not universally required.

**Must have (table stakes):**
- Code generator + Gradle plugin — without this, the entire library is inaccessible
- Generated DSL builders with typed field setters — the core value proposition
- Sensible column defaults (deterministic, not random) — tests should only specify what they care about
- Automatic single-column FK resolution from parent context — eliminates the primary pain point
- Nested child builder blocks — the natural mental model for declaring relational data
- Topological batch insert — parents before children, fewer DB round trips
- Record refresh after insert — captures DB-generated PKs and defaults
- Typed `DslResult` with ordered result lists per table — structured, typed access to inserted records

**Should have (competitive differentiators):**
- Self-referential FK support (hierarchical data) — common enough to be a meaningful gap if absent
- Multiple-FK-to-same-table disambiguation (`created_by`/`updated_by` patterns) — named setters rather than implicit resolution
- Multiple records of same type in one block — currently requires calling `execute {}` twice as workaround

**Defer (v2+):**
- Composite FK support — rare (~2-5% of schemas), disproportionate complexity
- Maven Central / Gradle Plugin Portal publishing — mavenLocal is sufficient for initial adoption
- Query/read DSL — intentionally out of scope per PROJECT.md
- Framework-specific integrations (Spring, Micronaut) — `DSLContext` parameter keeps the library framework-agnostic

**Anti-features (do not build):**
- Random/fake data generation — datafaker exists; randomness makes tests non-deterministic
- Trait/factory inheritance — Kotlin functions serve the same purpose without library complexity
- Annotation-based configuration — conflicts with the code-generation architecture

### Architecture Approach

The three-module architecture cleanly separates concerns along deployment boundaries. `dsl-runtime` ships to users and knows nothing about code generation. `codegen` is the intelligence layer — it contains the `ClasspathScanner` (URLClassLoader-based discovery), `MetadataExtractor` (jOOQ reflection), an internal IR (`TableModel`, `ColumnModel`, `ForeignKeyModel` — all pure Kotlin data classes with no jOOQ types), and `KotlinEmitter` (KotlinPoet-based). `gradle-plugin` is a thin wiring layer that reads extension config via lazy `Property<T>` and delegates to `codegen`. The IR decoupling is particularly important: it makes the emitter testable with hand-crafted models independent of jOOQ, and it isolates the codegen module from jOOQ API changes.

**Major components:**
1. **`dsl-runtime`** — runtime DSL engine: `DslScope` (builder execution), `RecordGraph` (DAG construction, FK resolution), `TopologicalInserter` (sort + batch insert + refresh), `ResultAssembler` (typed result construction)
2. **`codegen`** — build-time generator: `ClasspathScanner`, `MetadataExtractor`, IR models, `KotlinEmitter`
3. **`gradle-plugin`** — Gradle integration: `DeclarativeJooqExtension` (lazy config), `GenerateDeclarativeJooqDsl` task, source set wiring into `testImplementation`

### Critical Pitfalls

1. **Classloader isolation in Gradle plugin** — Always use an isolated `URLClassLoader` for scanning user jOOQ classes. Loading them into the Gradle daemon classloader causes classloader leaks, version conflicts, and `ClassCastException`. This is a rewrite-level mistake if not addressed upfront. Consider Worker API or `JavaExec` subprocess as the isolation boundary.

2. **Batch insert does not refresh records** — `DSLContext.batchInsert()` follows JDBC batch semantics and does not populate DB-generated values back into record objects. Design the refresh strategy before implementing insert logic: use `RETURNING` clause for PostgreSQL, or fall back to individual `record.store()` calls. This must be paired with the insert design, not retrofitted.

3. **Self-referential FK causes topological sort cycle** — Strip self-edges from the FK graph before sorting. Handle self-referential FKs via a two-pass strategy: insert with `null` parent first, then issue UPDATEs. This must be designed before the insert logic is written.

4. **KotlinPoet `%T` vs `%L` confusion** — All type references in KotlinPoet format strings must use `%T` with `ClassName`/`TypeName`, never `%L`. Using `%L` for class names bypasses import management, producing generated code with unresolved references. Enforce with compile-test from day one.

5. **Generated output directory not wired into source set** — The Gradle plugin must call `sourceSets["test"].kotlin.srcDir(generationTask.flatMap { it.outputDir })` in `apply()`. Generating files into `build/` is not sufficient; Kotlin compiler must be told about the directory.

## Implications for Roadmap

Based on research, the architecture's module dependency graph and the feature dependency chain both point to the same natural phase structure. The `dsl-runtime` module has no dependencies on `codegen`, so it can be built and tested independently. The `codegen` module can be validated with `kotlin-compile-testing` before a Gradle plugin exists. Only after both are proven should the Gradle plugin wire them together. A final integration phase validates the full chain end-to-end.

### Phase 1: DSL Runtime Foundation

**Rationale:** `dsl-runtime` has no external module dependencies and is testable with manually constructed jOOQ records. Building it first establishes the data structures that generated code will depend on, and the topological sort algorithm that `codegen` needs to understand to generate correct DSL structure.

**Delivers:** Working runtime engine — `DslScope`, `RecordGraph`, topological sort, `TopologicalInserter` (with batch insert + refresh strategy), `ResultAssembler`, typed `DslResult`. Validated against H2 with hand-written test builders.

**Addresses features:** Record refresh after insert, topological batch insert, typed DslResult with ordered result lists, FK resolution logic, nested child builder execution.

**Avoids pitfalls:** Batch insert refresh problem (Pitfall 4) must be solved here; `LinkedHashMap` for declaration order (Pitfall 12); self-referential FK two-pass strategy (Pitfall 2); no-PK table handling (Pitfall 11).

### Phase 2: Code Generation Engine

**Rationale:** With the runtime established, codegen can generate code that actually works. The IR design is validated against the runtime's interface. `kotlin-compile-testing` makes codegen correctness verifiable without Gradle.

**Delivers:** `ClasspathScanner`, `MetadataExtractor`, IR models (`TableModel`, `ColumnModel`, `ForeignKeyModel`), `KotlinEmitter` producing compilable and correct Kotlin DSL source.

**Uses stack:** KotlinPoet 1.17.x, Java reflection over jOOQ classes, `URLClassLoader` isolation.

**Implements architecture:** `codegen` module entirely. Internal IR as the decoupling boundary. KotlinEmitter consuming only IR (no jOOQ types).

**Avoids pitfalls:** Classloader isolation (Pitfall 1) — design URLClassLoader boundary first; KotlinPoet `%T` discipline (Pitfalls 3 and 13); nullable type mapping via `dataType.nullable()` (Pitfall 6); multiple-FK disambiguation via named setters (Pitfall 7).

### Phase 3: Gradle Plugin

**Rationale:** The Gradle plugin is a thin wiring layer; it is the last piece to build. Its value depends entirely on the codegen engine working correctly. Build it last so integration tests validate the real pipeline.

**Delivers:** `DeclarativeJooqExtension` (lazy `Property<T>` config), `GenerateDeclarativeJooqDsl` task, source set wiring, `mavenLocal` publishing. Full Gradle TestKit integration tests.

**Uses stack:** Gradle 8.5+ `java-gradle-plugin`, Gradle TestKit, lazy task registration.

**Avoids pitfalls:** Eager extension reads (Pitfall 5) — use `Property<T>` throughout; source set registration (Pitfall 9) — wire in `apply()`; configuration cache (Pitfall 8) — test with `--configuration-cache` from first run.

### Phase 4: End-to-End Integration and Edge Cases

**Rationale:** With all three modules working individually, validate the full pipeline against real databases. Also implements the deferrable differentiators that require cross-cutting changes (self-referential FK, multiple-FK disambiguation).

**Delivers:** Full integration test against Postgres via Testcontainers, self-referential FK support, multiple-FK-to-same-table named setters, documentation and usage examples.

**Addresses features:** Self-referential FK support (ARCHITECTURE.md anti-pattern 5 plus PITFALLS.md Pitfall 2), multiple-FK disambiguation (FEATURES.md differentiator, PITFALLS.md Pitfall 7), multiple records of same type in one block.

### Phase Ordering Rationale

- **Runtime before codegen:** Generated code imports from `dsl-runtime`; the runtime interface must be stable before codegen emits code that depends on it. Building runtime first also means codegen tests can compile and execute generated code against a real runtime.
- **Codegen before plugin:** The plugin is a thin wrapper; there is no value in building the wrapper before the thing being wrapped works.
- **Edge cases last:** Self-referential FKs and multiple-FK disambiguation require the full pipeline to be working before they can be tested meaningfully.
- **Classloader isolation is Phase 2 day-one:** The URLClassLoader design (Pitfall 1) must be the first thing resolved in the codegen phase — it is the architectural cornerstone of the scanner.

### Research Flags

Phases likely needing deeper research during planning:
- **Phase 2 (codegen):** URLClassLoader isolation boundary with Gradle Worker API vs `JavaExec` subprocess — the right approach depends on Gradle version compatibility and configuration cache interaction. Needs validation against current Gradle 8.x docs before implementation.
- **Phase 3 (Gradle plugin):** Configuration cache compatibility details for Gradle 8.x vs 9.x differ; specific serializable/non-serializable boundaries need verification against the version in use.
- **Phase 4:** Postgres `RETURNING` clause via jOOQ — confirm exact API for batch-with-returning in jOOQ 3.18+; H2 Postgres mode support for this needs verification.

Phases with standard patterns (skip research-phase):
- **Phase 1 (DSL runtime):** Topological sort, builder pattern, and jOOQ record insert/refresh are well-documented patterns with stable APIs. No research phase needed.
- **Phase 3 (Gradle plugin scaffolding):** `java-gradle-plugin` + `Property<T>` lazy configuration is official Gradle documentation with stable patterns since Gradle 7.

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | MEDIUM | Core tools (KotlinPoet, jOOQ FK API, Gradle `java-gradle-plugin`) are HIGH confidence. Specific library versions (KotlinPoet 1.17, kotlin-compile-testing 0.5) should be verified on Maven Central before pinning — no external tools were available during research. |
| Features | MEDIUM-HIGH | jOOQ-specific constraints are HIGH confidence. Ecosystem comparison (Instancio, FactoryBot) is MEDIUM — stable libraries, well-documented feature sets. |
| Architecture | MEDIUM | Three-module structure and URLClassLoader pattern are HIGH confidence (established JVM tooling patterns). jOOQ static singleton field naming convention (`TABLE_NAME.uppercase()`) for reflection is MEDIUM — verify against generated output in the actual jOOQ version used. |
| Pitfalls | HIGH | All pitfalls are grounded in documented API behaviors: jOOQ batch insert semantics, Gradle classloader model, KotlinPoet format specifiers, JDBC `executeBatch()` behavior. These are stable, long-standing characteristics. |

**Overall confidence:** MEDIUM-HIGH

### Gaps to Address

- **jOOQ static instance field naming:** The reflection approach assumes `tableClass.getField(tableName.uppercase())` to get the table singleton. Verify the exact field name pattern in jOOQ 3.18/3.19 generated output before implementing `MetadataExtractor`. Different jOOQ versions or configurations may use different naming conventions.
- **kotlin-compile-testing current version:** Version 0.5.x is approximate (training data). Verify the latest stable release on GitHub before pinning. This library is actively maintained and versions may have moved.
- **H2 Postgres mode + jOOQ `RETURNING`:** H2 2.x in Postgres compatibility mode may not fully support `RETURNING` in all cases jOOQ uses it. Validate during Phase 1 integration tests before committing to the refresh strategy.
- **Gradle Worker API vs URLClassLoader:** The research notes two approaches for classloader isolation (Worker API subprocess vs. isolated `URLClassLoader`). The Worker API is safer for configuration cache compatibility but more complex. This decision should be made at the start of Phase 2, not deferred.
- **KotlinPoet version compatibility with Kotlin 2.0:** KotlinPoet 1.17.x should support Kotlin 2.0 generated code, but verify there are no known issues with K2 compiler and KotlinPoet output before starting codegen work.

## Sources

### Primary (HIGH confidence)
- jOOQ 3.x API documentation (training knowledge): `Table.fields()`, `Table.getReferences()`, `ForeignKey`, `UpdatableRecord.refresh()`, `DSLContext.batchInsert()`, `Table.getPrimaryKey()`, `TableField.dataType.nullable()`
- KotlinPoet documentation: `FileSpec`, `TypeSpec`, `FunSpec`, `CodeBlock` format specifiers (`%T`, `%N`, `%L`, `%S`), `ClassName`, `TypeName`, import management
- Gradle Plugin Development Guide: `java-gradle-plugin`, `Property<T>`, `DirectoryProperty`, lazy task registration (`tasks.register`), `@InputFiles`/`@OutputDirectory` annotations, configuration cache constraints
- Gradle TestKit guide: `GradleRunner` integration testing
- Test Data Builder pattern (Nat Pryce, Steve Freeman): well-established literature

### Secondary (MEDIUM confidence)
- jOOQ generated code structure (static singleton fields, `TableImpl` subclasses, FK metadata fields): training knowledge of jOOQ 3.18 codegen output — stable but unverified against specific version
- URLClassLoader isolation pattern: standard JVM tooling pattern used by build tools universally
- kotlin-compile-testing: GitHub repo behavior and version range — approximate, verify current release
- Instancio, EasyRandom, FactoryBot feature sets: stable libraries, training knowledge through Aug 2025

### Tertiary (LOW confidence)
- kotlin-compile-testing 0.5.x version: verify on GitHub before pinning
- KotlinPoet 1.17.x version: verify on Maven Central before pinning
- H2 Postgres mode `RETURNING` support: validate empirically during Phase 1 integration tests

---
*Research completed: 2026-03-15*
*Ready for roadmap: yes*
