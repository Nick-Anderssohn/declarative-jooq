---
phase: 07-example-schema
plan: 01
subsystem: database
tags: [jooq, sql, postgresql, flyway, kotlin]

# Dependency graph
requires: []
provides:
  - app_user table DDL with id, name, email columns
  - shared_with junction table DDL linking todo_list to app_user
  - created_by and updated_by FK columns on todo_list and todo_item
  - UserTable.kt and UserRecord.kt hand-written jOOQ classes
  - SharedWithTable.kt and SharedWithRecord.kt hand-written jOOQ classes
  - Updated TodoListTable/Record with CREATED_BY, UPDATED_BY fields and getReferences()
  - Updated TodoItemTable/Record with CREATED_BY, UPDATED_BY fields and 3-FK getReferences()
affects: [08-dsl-integration, 09-demo-app]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - Hand-written jOOQ TableImpl subclasses with companion object singleton (APP_USER, SHARED_WITH)
    - Multi-FK getReferences() declaring each FK with Internal.createForeignKey
    - Nullable FK columns (created_by, updated_by) for optional user assignment

key-files:
  created:
    - examples/todo-list/src/main/kotlin/com/nickanderssohn/todolist/jooq/UserTable.kt
    - examples/todo-list/src/main/kotlin/com/nickanderssohn/todolist/jooq/UserRecord.kt
    - examples/todo-list/src/main/kotlin/com/nickanderssohn/todolist/jooq/SharedWithTable.kt
    - examples/todo-list/src/main/kotlin/com/nickanderssohn/todolist/jooq/SharedWithRecord.kt
  modified:
    - examples/todo-list/src/main/resources/db/migration/V1__create_todo_schema.sql
    - examples/todo-list/src/main/kotlin/com/nickanderssohn/todolist/jooq/TodoListTable.kt
    - examples/todo-list/src/main/kotlin/com/nickanderssohn/todolist/jooq/TodoListRecord.kt
    - examples/todo-list/src/main/kotlin/com/nickanderssohn/todolist/jooq/TodoItemTable.kt
    - examples/todo-list/src/main/kotlin/com/nickanderssohn/todolist/jooq/TodoItemRecord.kt

key-decisions:
  - "Use app_user as table name (not user) to avoid PostgreSQL reserved word conflict"
  - "created_by and updated_by are nullable FKs — records can exist without user assignment initially"
  - "shared_with has its own BIGSERIAL id primary key for consistency with other tables"
  - "shared_with has composite UNIQUE(todo_list_id, user_id) to prevent duplicate shares"

patterns-established:
  - "Multi-FK table pattern: declare each FK in getReferences() via Internal.createForeignKey referencing target table's primaryKey"
  - "Nullable FK fields use SQLDataType.BIGINT (no .nullable(false)) — nullable by default in jOOQ"

requirements-completed: [SCHEMA-01, SCHEMA-02, SCHEMA-03, SCHEMA-04, SCHEMA-05, SCHEMA-06]

# Metrics
duration: 2min
completed: 2026-03-18
---

# Phase 7 Plan 01: Example Schema Summary

**4-table PostgreSQL schema (app_user, todo_list, todo_item, shared_with) with 8 hand-written jOOQ table/record classes declaring multi-FK relationships for the todo-list example project**

## Performance

- **Duration:** 2 min
- **Started:** 2026-03-18T02:29:50Z
- **Completed:** 2026-03-18T02:31:29Z
- **Tasks:** 2
- **Files modified:** 9

## Accomplishments

- Extended V1 SQL migration with app_user table (created before todo_list/todo_item for FK resolution), created_by/updated_by FK columns on both list and item tables, and shared_with junction table with composite unique constraint
- Created UserTable/UserRecord jOOQ classes for the app_user table
- Created SharedWithTable/SharedWithRecord jOOQ classes with two FK references (to todo_list and app_user)
- Updated TodoListTable with CREATED_BY/UPDATED_BY fields and two-FK getReferences() pointing to app_user
- Updated TodoItemTable with CREATED_BY/UPDATED_BY fields and three-FK getReferences() (todo_list + 2x app_user)
- All 8 jOOQ classes compile cleanly via `./gradlew compileKotlin`

## Task Commits

Each task was committed atomically:

1. **Task 1: Update SQL migration** - `ca9008a` (feat)
2. **Task 2: Create/update jOOQ table and record classes** - `a82823c` (feat)

**Plan metadata:** (docs commit below)

## Files Created/Modified

- `examples/todo-list/src/main/resources/db/migration/V1__create_todo_schema.sql` - Full 4-table schema DDL
- `examples/todo-list/src/main/kotlin/com/nickanderssohn/todolist/jooq/UserTable.kt` - jOOQ table for app_user
- `examples/todo-list/src/main/kotlin/com/nickanderssohn/todolist/jooq/UserRecord.kt` - jOOQ record for app_user
- `examples/todo-list/src/main/kotlin/com/nickanderssohn/todolist/jooq/SharedWithTable.kt` - jOOQ table for shared_with with 2 FK refs
- `examples/todo-list/src/main/kotlin/com/nickanderssohn/todolist/jooq/SharedWithRecord.kt` - jOOQ record for shared_with
- `examples/todo-list/src/main/kotlin/com/nickanderssohn/todolist/jooq/TodoListTable.kt` - Added CREATED_BY, UPDATED_BY fields and getReferences()
- `examples/todo-list/src/main/kotlin/com/nickanderssohn/todolist/jooq/TodoListRecord.kt` - Added createdBy, updatedBy properties
- `examples/todo-list/src/main/kotlin/com/nickanderssohn/todolist/jooq/TodoItemTable.kt` - Added CREATED_BY, UPDATED_BY fields, updated getReferences() to 3 FKs
- `examples/todo-list/src/main/kotlin/com/nickanderssohn/todolist/jooq/TodoItemRecord.kt` - Added createdBy, updatedBy properties

## Decisions Made

- Used `app_user` as the table name instead of `user` to avoid PostgreSQL reserved word
- `created_by` and `updated_by` are nullable (no `.nullable(false)`) so records can be inserted without a user initially
- `shared_with` table has its own BIGSERIAL primary key for consistency with other tables
- `shared_with` has `UNIQUE(todo_list_id, user_id)` to prevent duplicate share entries

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Richer schema is in place: app_user table, FK columns on todo_list/todo_item, shared_with junction table
- All jOOQ classes compile and declare FK relationships — ready for DSL integration phase
- No blockers

---
*Phase: 07-example-schema*
*Completed: 2026-03-18*
