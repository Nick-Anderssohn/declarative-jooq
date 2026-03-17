# Requirements: declarative-jooq

**Defined:** 2026-03-16
**Core Value:** Eliminate boilerplate and manual FK wiring when creating test data — declare what records you want and how they relate, and the library handles insertion order, FK assignment, and result assembly.

## v0.2 Requirements

Requirements for v0.2 release. Each maps to roadmap phases.

### Builder Naming

- [ ] **NAME-01**: Builder functions for child tables default to the child table name (e.g., `user { }` inside `organization { }`)
- [ ] **NAME-02**: Builder functions fall back to FK column name (minus `_id` suffix) when the FK column name does not match the parent table name (e.g., `createdBy { }` for `created_by` → `user`)
- [ ] **NAME-03**: When two FK columns pointing to the same table would produce the same builder name (`table_name` and `table_name_id`), the collision is detected and disambiguated
- [ ] **NAME-04**: Self-referential FK builder functions use the table name (e.g., `category { }` instead of `childCategory { }`)

### Placeholder Objects

- [ ] **PLCH-01**: Builder blocks return a typed placeholder object representing the future record
- [ ] **PLCH-02**: Placeholder objects can be assigned to FK column properties on other builders for explicit FK wiring
- [ ] **PLCH-03**: Placeholder references work across different root trees within the same `execute` block
- [ ] **PLCH-04**: Placeholder assignment overrides parent-context auto-resolved FK values

### Documentation

- [ ] **DOCS-01**: README.md updated to reflect new DSL naming convention and placeholder usage

## Future Requirements

None currently deferred.

## Out of Scope

| Feature | Reason |
|---------|--------|
| Multi-column (composite) foreign keys | Rare and adds significant complexity — deferred from v0.1 |
| Maven Central / Gradle Plugin Portal publishing | Local only for now (mavenLocal) |
| Record update/delete operations | Insert-only tool |
| Query/read DSL | jOOQ handles queries |
| Sensible default values for columns | Defer to datafaker/kotlin-faker |

## Traceability

Which phases cover which requirements. Updated during roadmap creation.

| Requirement | Phase | Status |
|-------------|-------|--------|
| NAME-01 | — | Pending |
| NAME-02 | — | Pending |
| NAME-03 | — | Pending |
| NAME-04 | — | Pending |
| PLCH-01 | — | Pending |
| PLCH-02 | — | Pending |
| PLCH-03 | — | Pending |
| PLCH-04 | — | Pending |
| DOCS-01 | — | Pending |

**Coverage:**
- v0.2 requirements: 9 total
- Mapped to phases: 0
- Unmapped: 9 ⚠️

---
*Requirements defined: 2026-03-16*
*Last updated: 2026-03-16 after initial definition*
