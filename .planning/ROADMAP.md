# Roadmap: declarative-jooq

## Milestones

- ✅ **v0.1** — Phases 1-4 (shipped 2026-03-16)
- 🚧 **v0.2 Natural DSL Naming & Placeholders** — Phases 5-6 (in progress)

## Phases

<details>
<summary>✅ v0.1 (Phases 1-4) — SHIPPED 2026-03-16</summary>

- [x] Phase 1: Runtime DSL Foundation (3/3 plans) — completed 2026-03-15
- [x] Phase 2: Code Generation Engine (3/3 plans) — completed 2026-03-16
- [x] Phase 3: Gradle Plugin (2/2 plans) — completed 2026-03-16
- [x] Phase 4: Edge Cases and Integration (2/2 plans) — completed 2026-03-16

</details>

### 🚧 v0.2 Natural DSL Naming & Placeholders (In Progress)

**Milestone Goal:** Make the DSL read naturally by naming builder functions after child tables, and add placeholder objects for explicit FK wiring across any scope.

- [ ] **Phase 5: Child-Table-Named Builder Functions** - Rename generated builder functions to use child table names by default, with FK-column fallback and collision detection
- [ ] **Phase 6: Placeholder Objects** - Add typed placeholder objects returned from builder blocks enabling explicit and cross-tree FK assignment

## Phase Details

### Phase 5: Child-Table-Named Builder Functions
**Goal**: The generated DSL uses child table names for builder functions so nested blocks read as entity relationships rather than FK column names
**Depends on**: Phase 4 (v0.1 complete)
**Requirements**: NAME-01, NAME-02, NAME-03, NAME-04
**Success Criteria** (what must be TRUE):
  1. A child builder nested under a parent uses the child table name (e.g., `user { }` inside `organization { }` instead of `userId { }`)
  2. When the FK column name does not match the parent table name, the builder falls back to the FK column name minus `_id` (e.g., `createdBy { }` for a `created_by` column pointing to `app_user`)
  3. Two FK columns from the same child table pointing to the same parent table do not produce duplicate builder function names — collision is detected and both use the FK-column-name fallback
  4. Self-referential FK builders use the table name (e.g., `category { }` not `childCategory { }`)
  5. All existing compile-testing and integration test harnesses pass with updated builder function names
**Plans**: TBD

### Phase 6: Placeholder Objects
**Goal**: Builder blocks return typed placeholder objects that users can assign to FK properties on any builder, enabling explicit and cross-tree FK wiring without escaping to raw jOOQ
**Depends on**: Phase 5
**Requirements**: PLCH-01, PLCH-02, PLCH-03, PLCH-04, DOCS-01
**Success Criteria** (what must be TRUE):
  1. Calling a builder block captures the return value as a typed placeholder (e.g., `val alice = appUser { }`) without changing how the record is inserted
  2. Assigning a placeholder to a FK property on another builder wires the future PK at insert time (e.g., `createdBy = alice` inserts Alice first and populates the FK field)
  3. A placeholder captured from one root tree can be assigned to a builder in a different root tree within the same `execute` block, and the topological insert order is correct
  4. A placeholder assignment overrides the parent-context auto-resolved FK value for the same field — the inserted record reflects the placeholder's PK, not the context parent's PK
  5. README.md documents the new builder naming convention and placeholder pattern with working examples
**Plans**: TBD

## Progress

| Phase | Milestone | Plans Complete | Status | Completed |
|-------|-----------|----------------|--------|-----------|
| 1. Runtime DSL Foundation | v0.1 | 3/3 | Complete | 2026-03-15 |
| 2. Code Generation Engine | v0.1 | 3/3 | Complete | 2026-03-16 |
| 3. Gradle Plugin | v0.1 | 2/2 | Complete | 2026-03-16 |
| 4. Edge Cases and Integration | v0.1 | 2/2 | Complete | 2026-03-16 |
| 5. Child-Table-Named Builder Functions | v0.2 | 0/? | Not started | - |
| 6. Placeholder Objects | v0.2 | 0/? | Not started | - |
