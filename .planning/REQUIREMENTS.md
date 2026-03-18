# Requirements: declarative-jooq

**Defined:** 2026-03-17
**Core Value:** Eliminate boilerplate and manual FK wiring when creating test data — declare what records you want and how they relate, and the library handles insertion order, FK assignment, and result assembly.

## v0.3 Requirements

Requirements for the Richer Example milestone. All changes are to the `examples/todo-list` project.

### Schema

- [x] **SCHEMA-01**: Database has a `user` table with id, name, and email fields (added to existing `V1__create_todo_schema.sql`)
- [x] **SCHEMA-02**: `todo_list` table has a `created_by` FK column referencing `user`
- [x] **SCHEMA-03**: `todo_list` table has an `updated_by` FK column referencing `user`
- [x] **SCHEMA-04**: `todo_item` table has a `created_by` FK column referencing `user`
- [x] **SCHEMA-05**: `todo_item` table has an `updated_by` FK column referencing `user`
- [x] **SCHEMA-06**: `shared_with` table links a `todo_list` to a `user`

### API

- [ ] **API-01**: User can create a user record via POST `/api/users`
- [ ] **API-02**: User can list all users via GET `/api/users`
- [ ] **API-03**: User can share a todo list with a user via POST `/api/todo-lists/{id}/share`
- [ ] **API-04**: User can view which users a todo list is shared with via GET `/api/todo-lists/{id}/shares`
- [ ] **API-05**: User can specify `created_by` user ID when creating a todo list
- [ ] **API-06**: User can specify `created_by` user ID when creating a todo item

### Tests

- [ ] **TEST-01**: Integration test seeds users and todo lists/items with `created_by`/`updated_by` FK wiring via DSL
- [ ] **TEST-02**: Integration test uses placeholder refs to wire one user as creator of multiple records
- [ ] **TEST-03**: Integration test seeds `shared_with` records via DSL
- [ ] **TEST-04**: REST API integration test covers user creation and listing
- [ ] **TEST-05**: REST API integration test covers sharing a list and retrieving shares

## Future Requirements

(None identified — scope is complete for v0.3)

## Out of Scope

| Feature | Reason |
|---------|--------|
| Flyway V2+ migration files | Example project updated directly in V1; no incremental migration needed |
| Authentication / login | Out of scope for test data example |
| User update/delete endpoints | Not needed for demonstrating test data patterns |
| Pagination on list endpoints | Over-engineering for an example |

## Traceability

Which phases cover which requirements. Updated during roadmap creation.

| Requirement | Phase | Status |
|-------------|-------|--------|
| SCHEMA-01 | Phase 7 | Complete |
| SCHEMA-02 | Phase 7 | Complete |
| SCHEMA-03 | Phase 7 | Complete |
| SCHEMA-04 | Phase 7 | Complete |
| SCHEMA-05 | Phase 7 | Complete |
| SCHEMA-06 | Phase 7 | Complete |
| API-01 | Phase 8 | Pending |
| API-02 | Phase 8 | Pending |
| API-03 | Phase 8 | Pending |
| API-04 | Phase 8 | Pending |
| API-05 | Phase 8 | Pending |
| API-06 | Phase 8 | Pending |
| TEST-01 | Phase 9 | Pending |
| TEST-02 | Phase 9 | Pending |
| TEST-03 | Phase 9 | Pending |
| TEST-04 | Phase 9 | Pending |
| TEST-05 | Phase 9 | Pending |

**Coverage:**
- v0.3 requirements: 17 total
- Mapped to phases: 17
- Unmapped: 0 ✓

---
*Requirements defined: 2026-03-17*
*Last updated: 2026-03-17 after roadmap creation*
