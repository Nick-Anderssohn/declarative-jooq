# Roadmap: declarative-jooq

## Overview

Four phases that mirror the module dependency graph: build the runtime first (it has no external dependencies and defines the interface generated code must satisfy), then build the code generator (which produces code that calls the runtime), then build the Gradle plugin (a thin wiring layer over the generator), and finally validate the full pipeline end-to-end while adding the edge-case differentiators (self-referential FKs, multiple-FK disambiguation).

## Phases

**Phase Numbering:**
- Integer phases (1, 2, 3): Planned milestone work
- Decimal phases (2.1, 2.2): Urgent insertions (marked with INSERTED)

Decimal phases appear between their surrounding integers in numeric order.

- [ ] **Phase 1: Runtime DSL Foundation** - Three-module project scaffold + complete DSL runtime engine with topological insert, FK resolution, and typed results
- [ ] **Phase 2: Code Generation Engine** - ClasspathScanner, MetadataExtractor, IR models, and KotlinEmitter producing compilable DSL source
- [ ] **Phase 3: Gradle Plugin** - Plugin wiring, extension config, source set registration, and mavenLocal publishing
- [ ] **Phase 4: Edge Cases and Integration** - Self-referential FKs, multiple-FK disambiguation, and end-to-end Postgres integration tests

## Phase Details

### Phase 1: Runtime DSL Foundation
**Goal**: A working three-module project where the runtime DSL engine can accept a declarative record graph, insert records in topological order, resolve single-column FKs from parent context, and return a typed DslResult with records in declaration order.
**Depends on**: Nothing (first phase)
**Requirements**: PROJ-01, PROJ-02, PROJ-03, PROJ-04, DSL-01, DSL-02, DSL-03, DSL-04, DSL-05, DSL-06, DSL-07, DSL-08
**Success Criteria** (what must be TRUE):
  1. A hand-written test using manually constructed jOOQ record builders can call `execute(dslContext) { ... }`, declare root and nested records, and get back a typed DslResult with correctly populated PKs and FK values.
  2. Records are inserted in parent-before-child order (topological), and the database reflects the correct FK relationships.
  3. Records in DslResult appear in the same order they were declared in the DSL block.
  4. The `dsl-runtime` module has no compile dependency on KotlinPoet, Gradle APIs, or the `codegen` module.
  5. All three modules (`dsl-runtime`, `codegen`, `gradle-plugin`) exist as a Gradle multi-project build with correct inter-module dependency declarations.
**Plans:** 2/3 plans executed

Plans:
- [ ] 01-01-PLAN.md — Gradle project scaffold + core DSL runtime types
- [ ] 01-02-PLAN.md — Topological sort, insert engine, result assembler, execute() entry point
- [ ] 01-03-PLAN.md — H2 test schema, hand-written builders, integration tests

### Phase 2: Code Generation Engine
**Goal**: A standalone code generator that scans a directory of compiled jOOQ classes, extracts table/column/FK metadata through an internal IR, and emits compilable Kotlin DSL builder and result classes using KotlinPoet — testable entirely without Gradle.
**Depends on**: Phase 1
**Requirements**: CODEGEN-02, CODEGEN-03, CODEGEN-04, CODEGEN-05, CODEGEN-06, TEST-01
**Success Criteria** (what must be TRUE):
  1. Given a directory of compiled jOOQ record classes, the generator produces a DSL builder class per table with typed property setters and a result class per table with typed property accessors.
  2. Generated code compiles and executes correctly against `dsl-runtime` in a `kotlin-compile-testing` test (not just string-matching assertions).
  3. Nested FK builder functions are generated inside parent builders, with automatic FK resolution matching the declared relationship.
  4. A generated top-level `DslResult` class contains ordered lists of result objects per root table.
  5. The `codegen` module is independently testable — no Gradle daemon or plugin required to run codegen tests.
**Plans:** 3 plans

Plans:
- [ ] 02-01-PLAN.md — Build setup, IR data classes, ClasspathScanner, MetadataExtractor
- [ ] 02-02-PLAN.md — BuilderEmitter, ResultEmitter, DslScopeEmitter, DslResultEmitter, CodeGenerator
- [ ] 02-03-PLAN.md — Compile-and-run integration tests via kotlin-compile-testing

### Phase 3: Gradle Plugin
**Goal**: A Gradle plugin that wires the code generator into a user's build via `apply plugin: 'com.example.declarative-jooq'`, reads configuration from an extension block, runs generation as a registered task, and wires the output directory into the `testImplementation` source set.
**Depends on**: Phase 2
**Requirements**: CODEGEN-01, TEST-02
**Success Criteria** (what must be TRUE):
  1. A test project using `apply plugin:` syntax with an extension block configuring jOOQ source directory and output package runs `./gradlew generateDeclarativeJooqDsl` and produces DSL source files in the output directory.
  2. The generated source directory is automatically wired into the test compile classpath — no manual `srcDir` configuration needed in the consuming project.
  3. Gradle TestKit integration tests pass, including a test with `--configuration-cache` enabled.
  4. The plugin is publishable to and consumable from `mavenLocal`.
**Plans**: TBD

### Phase 4: Edge Cases and Integration
**Goal**: The full pipeline handles self-referential FK tables (two-pass insert), disambiguates multiple FKs from one table to the same target table (named builder functions), and passes end-to-end integration tests against a real Postgres database.
**Depends on**: Phase 3
**Requirements**: CODEGEN-07, CODEGEN-08, DSL-09, DSL-10, TEST-03
**Success Criteria** (what must be TRUE):
  1. A table with a self-referential FK (e.g., `category.parent_id → category.id`) can be declared in the DSL with children nested under parents, and the database reflects the correct parent-child relationships after insert.
  2. When two FKs from the same table point to the same target table (e.g., `created_by` and `updated_by` both reference `user.id`), the DSL exposes two named builder functions and each correctly populates the intended FK column.
  3. End-to-end integration tests using a real Postgres database (via Testcontainers) pass for the core scenarios: root records, nested records, multiple records of the same type in one block, and FK resolution.
**Plans**: TBD

## Progress

**Execution Order:**
Phases execute in numeric order: 1 → 2 → 3 → 4

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 1. Runtime DSL Foundation | 2/3 | In Progress|  |
| 2. Code Generation Engine | 0/3 | Not started | - |
| 3. Gradle Plugin | 0/TBD | Not started | - |
| 4. Edge Cases and Integration | 0/TBD | Not started | - |
