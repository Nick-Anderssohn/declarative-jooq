---
gsd_state_version: 1.0
milestone: v0.3
milestone_name: Richer Example
status: planning
stopped_at: Completed 08-example-api-08-01-PLAN.md
last_updated: "2026-03-18T02:52:47.786Z"
last_activity: 2026-03-17 — v0.3 roadmap created
progress:
  total_phases: 3
  completed_phases: 1
  total_plans: 3
  completed_plans: 2
  percent: 0
---

# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-03-17)

**Core value:** Eliminate boilerplate and manual FK wiring when creating test data — declare what records you want and how they relate, and the library handles insertion order, FK assignment, and result assembly.
**Current focus:** v0.3 Phase 7 — Example Schema

## Current Position

Phase: 7 of 9 (Example Schema)
Plan: — (not yet planned)
Status: Ready to plan
Last activity: 2026-03-17 — v0.3 roadmap created

Progress: [░░░░░░░░░░] 0%

## Performance Metrics

**Velocity:**
- Total plans completed: 0 (this milestone)
- Average duration: —
- Total execution time: —

**By Phase:**

| Phase | Plans | Total | Avg/Plan |
|-------|-------|-------|----------|
| - | - | - | - |

*Updated after each plan completion*
| Phase 07-example-schema P01 | 2 | 2 tasks | 9 files |
| Phase 08-example-api P01 | 1 | 2 tasks | 8 files |

## Accumulated Context

### Decisions

All v0.1/v0.2 decisions archived in PROJECT.md Key Decisions table.

v0.3 decisions:
- Schema changes go into the EXISTING `V1__create_todo_schema.sql`, not a new migration file
- jOOQ table/record classes are hand-written (not generated) in the todo-list example
- [Phase 07-example-schema]: Use app_user as table name (not user) to avoid PostgreSQL reserved word conflict
- [Phase 07-example-schema]: created_by and updated_by are nullable FKs — records can exist without user assignment initially
- [Phase 07-example-schema]: shared_with has composite UNIQUE(todo_list_id, user_id) to prevent duplicate shares
- [Phase 08-example-api]: createdBy defaults to null in all create signatures so existing callers are unaffected

### Pending Todos

None.

### Blockers/Concerns

None — requirements fully defined, roadmap created.

### Quick Tasks Completed

| # | Description | Date | Commit | Directory |
|---|-------------|------|--------|-----------|
| 260316-o7g | Change com.example to com.nickanderssohn throughout the repo | 2026-03-17 | 6abfe84 | [260316-o7g-change-com-example-to-com-nickanderssohn](./quick/260316-o7g-change-com-example-to-com-nickanderssohn/) |
| 260316-or6 | Create README.md describing the repository | 2026-03-17 | 7b59b84 | [260316-or6-create-a-readme-md-that-describes-the-re](./quick/260316-or6-create-a-readme-md-that-describes-the-re/) |
| 260317-m29 | Change multi-FK disambiguation from column-named to table-named with TableField parameter | 2026-03-17 | 21eb0bf | [260317-m29-change-multi-fk-disambiguation-from-colu](./quick/260317-m29-change-multi-fk-disambiguation-from-colu/) |
| 260317-n30 | Add standalone todo-list example project demonstrating declarative-jooq in Spring Boot | 2026-03-17 | 68cf1e5 | [260317-n30-add-example-todo-list-manager-project-us](./quick/260317-n30-add-example-todo-list-manager-project-us/) |
| 260317-nvt | Support PascalCase and camelCase db schema naming conventions in codegen | 2026-03-17 | 89e109f | [260317-nvt-right-now-we-assume-that-snake-case-was-](./quick/260317-nvt-right-now-we-assume-that-snake-case-was-/) |
| 260317-p40 | Add Java jOOQ fixture classes and tests to verify Java codegen pattern works | 2026-03-17 | 464277a | [260317-p40-it-looks-like-we-ve-only-tested-situatio](./quick/260317-p40-it-looks-like-we-ve-only-tested-situatio/) |
| 260317-pif | Move execute() into DecDsl singleton object; update all call sites and docs | 2026-03-18 | a8c5ddd | [260317-pif-move-dsl-kt-execute-function-into-a-sing](./quick/260317-pif-move-dsl-kt-execute-function-into-a-sing/) |

## Session Continuity

Last session: 2026-03-18T02:52:47.785Z
Stopped at: Completed 08-example-api-08-01-PLAN.md
Resume file: None
