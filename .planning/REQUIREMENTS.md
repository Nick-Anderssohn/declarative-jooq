# Requirements: declarative-jooq

**Defined:** 2026-03-15
**Core Value:** Eliminate boilerplate and manual FK wiring when creating test data — declare what records you want and how they relate, and the library handles insertion order, FK assignment, and result assembly.

## v1 Requirements

Requirements for initial release. Each maps to roadmap phases.

### Code Generation

- [ ] **CODEGEN-01**: Gradle plugin with `apply plugin:` syntax and extension configuration for specifying jOOQ source directory and output package
- [ ] **CODEGEN-02**: Recursive scan of a configured directory for jOOQ-generated record classes (classes extending UpdatableRecordImpl)
- [ ] **CODEGEN-03**: Generate a DSL builder class per table with typed property setters matching jOOQ record fields
- [ ] **CODEGEN-04**: Generate a result class per table with typed property accessors and the underlying jOOQ record
- [ ] **CODEGEN-05**: Generate a top-level `DslResult` class containing ordered lists of result objects per root table
- [ ] **CODEGEN-06**: Generate nested builder functions for single-column FK relationships (child builders inside parent builders)
- [ ] **CODEGEN-07**: Generate builder support for self-referential FKs (e.g., category.parent_id → category.id)
- [ ] **CODEGEN-08**: Generate disambiguated builder functions when multiple FKs from one table point to the same target table (e.g., created_by/updated_by → user)

### Runtime DSL

- [ ] **DSL-01**: `execute(dslContext) { ... }` entry point that creates all declared records and returns a typed `DslResult`
- [ ] **DSL-02**: Root table builder functions at the top level of the execute block (tables with no required FKs)
- [ ] **DSL-03**: Child table builder functions nested under their FK parent with automatic FK value resolution from parent context
- [ ] **DSL-04**: Support multiple records of the same type within a single block (e.g., multiple users under one organization)
- [ ] **DSL-05**: Topological insert order — parent tables inserted before child tables based on FK dependency graph
- [ ] **DSL-06**: Batch insert per table for efficiency (all records of a table type in one batch)
- [ ] **DSL-07**: Record refresh after insert to capture DB-generated values (IDs, timestamps, defaults)
- [ ] **DSL-08**: Result object ordering matches declaration order in the DSL
- [ ] **DSL-09**: Self-referential FK two-pass insert (insert with null FK, then update after ID is generated)
- [ ] **DSL-10**: Multiple-FK-to-same-table runtime resolution (correct FK column populated based on which named builder was used)

### Project Structure

- [x] **PROJ-01**: Three-module Gradle project: `dsl-runtime`, `codegen`, `gradle-plugin`
- [x] **PROJ-02**: `dsl-runtime` has no compile dependency on KotlinPoet or Gradle APIs
- [x] **PROJ-03**: `codegen` module independently testable without Gradle
- [x] **PROJ-04**: Generated code depends only on `dsl-runtime` and user's jOOQ version

### Testing

- [ ] **TEST-01**: Codegen tests use compile-and-run validation (not string matching) via kotlin-compile-testing
- [ ] **TEST-02**: Gradle plugin integration tests via Gradle TestKit (GradleRunner)
- [ ] **TEST-03**: Runtime DSL integration tests against a real database (H2 Postgres mode or Testcontainers)

## v2 Requirements

Deferred to future release. Tracked but not in current roadmap.

### Advanced Features

- **ADV-01**: Composite (multi-column) foreign key support
- **ADV-02**: Maven Central / Gradle Plugin Portal publishing
- **ADV-03**: Sensible default values for columns (deterministic, not random) so tests only specify what they care about

## Out of Scope

| Feature | Reason |
|---------|--------|
| Random/fake data generation | Non-deterministic test data causes flaky tests; defer to datafaker/kotlin-faker |
| Query/read DSL | This is an insert-only tool; jOOQ handles queries |
| Framework-specific integrations (Spring, Micronaut) | DSLContext parameter keeps the library framework-agnostic |
| Record update/delete operations | Out of scope for test data setup tool |
| Migration or schema management | jOOQ handles that |
| Mobile app or UI | This is a library, not an application |

## Traceability

Which phases cover which requirements. Updated during roadmap creation.

| Requirement | Phase | Status |
|-------------|-------|--------|
| CODEGEN-01 | Phase 3 | Pending |
| CODEGEN-02 | Phase 2 | Pending |
| CODEGEN-03 | Phase 2 | Pending |
| CODEGEN-04 | Phase 2 | Pending |
| CODEGEN-05 | Phase 2 | Pending |
| CODEGEN-06 | Phase 2 | Pending |
| CODEGEN-07 | Phase 4 | Pending |
| CODEGEN-08 | Phase 4 | Pending |
| DSL-01 | Phase 1 | Pending |
| DSL-02 | Phase 1 | Pending |
| DSL-03 | Phase 1 | Pending |
| DSL-04 | Phase 1 | Pending |
| DSL-05 | Phase 1 | Pending |
| DSL-06 | Phase 1 | Pending |
| DSL-07 | Phase 1 | Pending |
| DSL-08 | Phase 1 | Pending |
| DSL-09 | Phase 4 | Pending |
| DSL-10 | Phase 4 | Pending |
| PROJ-01 | Phase 1 | Complete |
| PROJ-02 | Phase 1 | Complete |
| PROJ-03 | Phase 1 | Complete |
| PROJ-04 | Phase 1 | Complete |
| TEST-01 | Phase 2 | Pending |
| TEST-02 | Phase 3 | Pending |
| TEST-03 | Phase 4 | Pending |

**Coverage:**
- v1 requirements: 25 total
- Mapped to phases: 25
- Unmapped: 0

---
*Requirements defined: 2026-03-15*
*Last updated: 2026-03-15 after roadmap creation*
