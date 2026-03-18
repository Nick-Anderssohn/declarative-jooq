---
phase: 09-example-tests
plan: 01
subsystem: testing
tags: [kotlin, jooq, spring-boot, testcontainers, postgresql, declarative-jooq, integration-tests]

# Dependency graph
requires:
  - phase: 07-example-schema
    provides: app_user, todo_list, todo_item, shared_with tables with FK relationships
  - phase: 08-example-api
    provides: Spring Boot controllers, repositories, and DSL seeding patterns
provides:
  - Integration tests proving multi-FK disambiguation via TableField parameter
  - Integration tests proving placeholder fan-out (one user as creator of multiple records)
  - Integration tests proving shared_with junction seeding
  - Updated existing tests adapted to new app_user root table codegen
affects: [future-example-tests, documentation]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - appUser root builder as parent for todo_list and todo_item DSL trees
    - Placeholder capture (val alice = appUser { }) for cross-record FK wiring
    - todoList(fkField) child builder on AppUserBuilder for CREATED_BY disambiguation
    - todoItem(fkField) child builder on AppUserBuilder for CREATED_BY disambiguation
    - sharedWith { user = alice } for junction table seeding via AppUserResult placeholder
    - TodoItemBuilder.todoList = myList placeholder for wiring TODO_LIST_ID via ref

key-files:
  created: []
  modified:
    - examples/todo-list/src/test/kotlin/com/nickanderssohn/todolist/TodoListIntegrationTest.kt

key-decisions:
  - "TEST-01 structure: bob created at root level, alice as second appUser block with todoList(CREATED_BY) and todoItem(CREATED_BY) children, todoList wired to item via placeholder ref"
  - "TEST-02 structure: alice created as single appUser with two todoList(CREATED_BY) children — proves alice inserted once, both lists reference her id"
  - "TEST-03 structure: alice and bob as root appUser blocks, Owner as third appUser with sharedWith children using user = alice/bob placeholders"
  - "Existing tests adapted to appUser { todoList(CREATED_BY) { } } nesting because app_user is now the codegen root table (no outbound FKs)"

patterns-established:
  - "Multi-FK test pattern: create users at root level, nest fk-bearing tables as children of AppUserBuilder with explicit TableField parameter"
  - "Junction seeding pattern: sharedWith { user = appUserResult } inside todoList { } block"
  - "Placeholder fan-out pattern: capture appUser result, use as createdBy/updatedBy setter on sibling or descendant builders"

requirements-completed: [TEST-01, TEST-02, TEST-03]

# Metrics
duration: 7min
completed: 2026-03-18
---

# Phase 09 Plan 01: Example Tests Summary

**Three DSL integration tests proving multi-FK wiring, placeholder fan-out, and shared_with junction seeding in the todo-list Spring Boot example**

## Performance

- **Duration:** 7 min
- **Started:** 2026-03-18T03:20:27Z
- **Completed:** 2026-03-18T03:27:03Z
- **Tasks:** 1
- **Files modified:** 1

## Accomplishments
- Added TEST-01 verifying todo_list.created_by = alice.id and todo_list.updated_by = bob.id, todo_item.created_by = alice.id and todo_item.updated_by = bob.id via deep FK assertions
- Added TEST-02 verifying one Alice user inserted exactly once when her result is used as created_by for two separate todo_list records (placeholder fan-out)
- Added TEST-03 verifying two shared_with junction rows created with correct todo_list_id, alice's user_id, and bob's user_id
- Fixed broken existing 4 tests by adapting them from root-level `todoList { }` to `appUser { todoList(CREATED_BY) { } }` nesting (codegen root table changed when app_user was added)
- Updated @BeforeEach truncation to include shared_with and app_user tables
- Published updated dsl-runtime/codegen/gradle-plugin to mavenLocal (DecDsl singleton was missing from published jar)

## Task Commits

Each task was committed atomically:

1. **Task 1: Regenerate builders and add DSL integration tests** - `28d36e3` (feat)

**Plan metadata:** (see final commit below)

## Files Created/Modified
- `examples/todo-list/src/test/kotlin/com/nickanderssohn/todolist/TodoListIntegrationTest.kt` - Added 3 new test methods, updated 2 existing seeding tests, updated truncation, added imports

## Decisions Made
- TEST-01 uses `val bob = appUser { }` at root, then `appUser { name = "Alice"; todoList(CREATED_BY) { updatedBy = bob }; todoItem(CREATED_BY) { updatedBy = bob; todoList = myList } }` — separating bob creation from alice's builder block allows cross-reference
- TEST-03 introduces a third "Owner" user as the todoList parent since sharedWith wires users via placeholder, not as parents
- Published dsl-runtime to mavenLocal before running tests — DecDsl singleton (moved from DslKt in a prior quick task) was not in the published jar

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed existing tests broken by app_user becoming codegen root table**
- **Found during:** Task 1 (step to update @BeforeEach and add new tests)
- **Issue:** When app_user was added to the schema, codegen made it the root table (no outbound FKs). This removed the `fun DslScope.todoList(...)` extension — only `fun DslScope.appUser(...)` remained. The 3 existing seeding tests used `todoList { }` at root and failed to compile.
- **Fix:** Wrapped `todoList(TODO_LIST.CREATED_BY) { }` inside `appUser { }` blocks in the 2 seeding tests (`declarative-jooq seeds test data correctly` and `seeded data is accessible via REST API`). REST-only tests (`REST API creates and retrieves todo lists`, `returns 404 for non-existent todo list`) needed no changes.
- **Files modified:** `examples/todo-list/src/test/kotlin/com/nickanderssohn/todolist/TodoListIntegrationTest.kt`
- **Verification:** All 7 tests pass
- **Committed in:** 28d36e3

**2. [Rule 3 - Blocking] Published updated dsl-runtime to mavenLocal**
- **Found during:** Task 1 (compilation step)
- **Issue:** The mavenLocal dsl-runtime jar contained `DslKt.class` (old top-level `execute` function) but not `DecDsl.class` (the singleton object). Quick task `260317-pif` had moved execute() into a `DecDsl` object in source, but didn't republish the jar. Compilation failed with "Unresolved reference 'DecDsl'" in all test methods.
- **Fix:** Ran `./gradlew :dsl-runtime:publishToMavenLocal :codegen:publishToMavenLocal :gradle-plugin:publishToMavenLocal` from repo root.
- **Files modified:** ~/.m2 (mavenLocal cache, not tracked in git)
- **Verification:** Compilation succeeded after publishing
- **Committed in:** 28d36e3 (existing file changes only; jar publication is external)

---

**Total deviations:** 2 auto-fixed (1 bug, 1 blocking)
**Impact on plan:** Both auto-fixes essential for compilation and correctness. No scope creep.

## Issues Encountered
- The plan's proposed test structure for TEST-01 used `todoList(CREATED_BY)` and `todoItem(CREATED_BY)` at root DslScope level — but the generated API only provides these as children of `AppUserBuilder`. The test structure was adapted to use AppUserBuilder child methods while preserving all assertions.
- The plan's proposed TEST-02 used root-level `todoList(CREATED_BY) { createdBy = alice }` — not valid since todoList is not root. Adapted to `appUser { todoList(CREATED_BY) { } todoList(CREATED_BY) { } }` which proves the same fan-out property.
- The plan's proposed TEST-03 used root-level `todoList { sharedWith { userId = alice } }` — adapted to use Owner appUser as parent and `sharedWith { user = alice }` (property named `user`, not `userId`).

## Next Phase Readiness
- All 7 integration tests pass (4 existing + 3 new)
- Generated builders include AppUserBuilder, SharedWithBuilder, TodoListBuilder, TodoItemBuilder with correct placeholder setters
- Phase 09 complete — example-tests phase fully verified

---
*Phase: 09-example-tests*
*Completed: 2026-03-18*
