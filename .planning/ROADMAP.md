# Roadmap: declarative-jooq

## Milestones

- ✅ **v0.1** — Phases 1-4 (shipped 2026-03-16)
- ✅ **v0.2 Natural DSL Naming & Placeholders** — Phases 5-6 (shipped 2026-03-17)
- 🚧 **v0.3 Richer Example** — Phases 7-9 (in progress)

## Phases

<details>
<summary>✅ v0.1 (Phases 1-4) — SHIPPED 2026-03-16</summary>

- [x] Phase 1: Runtime DSL Foundation (3/3 plans) — completed 2026-03-15
- [x] Phase 2: Code Generation Engine (3/3 plans) — completed 2026-03-16
- [x] Phase 3: Gradle Plugin (2/2 plans) — completed 2026-03-16
- [x] Phase 4: Edge Cases and Integration (2/2 plans) — completed 2026-03-16

</details>

<details>
<summary>✅ v0.2 Natural DSL Naming & Placeholders (Phases 5-6) — SHIPPED 2026-03-17</summary>

- [x] Phase 5: Child-Table-Named Builder Functions (2/2 plans) — completed 2026-03-17
- [x] Phase 6: Placeholder Objects (3/3 plans) — completed 2026-03-17

</details>

### 🚧 v0.3 Richer Example (In Progress)

**Milestone Goal:** Enrich the todo-list example with a user table, multi-FK relationships, sharing, and DSL integration tests that showcase placeholder and FK disambiguation patterns.

- [x] **Phase 7: Example Schema** - Add user table, FK columns, and shared_with junction table to the todo-list example (completed 2026-03-18)
- [ ] **Phase 8: Example API** - Add user and sharing endpoints to the todo-list Spring Boot API
- [ ] **Phase 9: Example Tests** - Add integration tests exercising multi-FK wiring and placeholder patterns

## Phase Details

### Phase 7: Example Schema
**Goal**: The todo-list example database has a `user` table, `created_by`/`updated_by` FK columns on `todo_list` and `todo_item`, and a `shared_with` junction table — with corresponding hand-written jOOQ table/record classes
**Depends on**: Phase 6 (v0.2 complete)
**Requirements**: SCHEMA-01, SCHEMA-02, SCHEMA-03, SCHEMA-04, SCHEMA-05, SCHEMA-06
**Success Criteria** (what must be TRUE):
  1. `V1__create_todo_schema.sql` contains the `user` table definition with id, name, and email columns
  2. `todo_list` and `todo_item` tables each have `created_by` and `updated_by` columns that reference `user`
  3. A `shared_with` table exists linking `todo_list` to `user`
  4. Hand-written jOOQ `UserTable`, `UserRecord`, and related classes compile and are available on the classpath
  5. The application starts successfully with the updated schema applied by Flyway
**Plans:** 1/1 plans complete
Plans:
- [ ] 07-01-PLAN.md — SQL migration + hand-written jOOQ table/record classes for user, shared_with, and updated todo_list/todo_item

### Phase 8: Example API
**Goal**: The todo-list REST API exposes user creation, user listing, and sharing endpoints — and existing create-list and create-item endpoints accept `created_by` user IDs
**Depends on**: Phase 7
**Requirements**: API-01, API-02, API-03, API-04, API-05, API-06
**Success Criteria** (what must be TRUE):
  1. `POST /api/users` creates a user and returns the created record
  2. `GET /api/users` returns all users
  3. `POST /api/todo-lists/{id}/share` shares a todo list with a specified user
  4. `GET /api/todo-lists/{id}/shares` returns the list of users a todo list is shared with
  5. `POST /api/todo-lists` and `POST /api/todo-lists/{id}/items` accept and persist a `createdBy` user ID
**Plans:** 2 plans
Plans:
- [ ] 08-01-PLAN.md — User CRUD endpoints + createdBy on existing create endpoints
- [ ] 08-02-PLAN.md — Todo list sharing endpoints (share and list shares)

### Phase 9: Example Tests
**Goal**: Integration tests demonstrate multi-FK DSL wiring (created_by/updated_by disambiguation), placeholder refs wiring one user to multiple records, and shared_with seeding — proving the library patterns work end-to-end
**Depends on**: Phase 8
**Requirements**: TEST-01, TEST-02, TEST-03, TEST-04, TEST-05
**Success Criteria** (what must be TRUE):
  1. An integration test seeds users, todo lists, and todo items with `created_by`/`updated_by` FK wiring via the DSL and all records are inserted correctly
  2. An integration test uses a placeholder ref to wire one user as creator of multiple records, and the single user row is inserted once
  3. An integration test seeds `shared_with` records via the DSL and the junction rows exist in the database
  4. A REST API test verifies user creation and listing via HTTP
  5. A REST API test verifies sharing a list and retrieving the list of shares via HTTP
**Plans**: TBD

## Progress

**Execution Order:** 7 → 8 → 9

| Phase | Milestone | Plans Complete | Status | Completed |
|-------|-----------|----------------|--------|-----------|
| 1. Runtime DSL Foundation | v0.1 | 3/3 | Complete | 2026-03-15 |
| 2. Code Generation Engine | v0.1 | 3/3 | Complete | 2026-03-16 |
| 3. Gradle Plugin | v0.1 | 2/2 | Complete | 2026-03-16 |
| 4. Edge Cases and Integration | v0.1 | 2/2 | Complete | 2026-03-16 |
| 5. Child-Table-Named Builder Functions | v0.2 | 2/2 | Complete | 2026-03-17 |
| 6. Placeholder Objects | v0.2 | 3/3 | Complete | 2026-03-17 |
| 7. Example Schema | 1/1 | Complete    | 2026-03-18 | - |
| 8. Example API | v0.3 | 0/2 | Not started | - |
| 9. Example Tests | v0.3 | 0/? | Not started | - |
