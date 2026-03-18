---
phase: 09-example-tests
plan: 02
subsystem: testing
tags: [kotlin, spring-boot, testcontainers, rest-api, integration-tests]

# Dependency graph
requires:
  - phase: 09-01
    provides: DSL integration tests and test infrastructure (TestRestTemplate, Testcontainers, truncation)
  - phase: 08-example-api
    provides: UserController (/api/users), ShareController (/api/todo-lists/{listId}/share and /shares)
provides:
  - REST API integration tests for user creation, user listing, todo list sharing, and share retrieval
affects: []

# Tech tracking
tech-stack:
  added: []
  patterns:
    - TestRestTemplate POST/GET against live HTTP server with Testcontainers PostgreSQL
    - Cast response body to Map::class.java / List::class.java for lightweight JSON assertions

key-files:
  created: []
  modified:
    - examples/todo-list/src/test/kotlin/com/nickanderssohn/todolist/TodoListIntegrationTest.kt

key-decisions:
  - "No new decisions — plan executed exactly as written"

patterns-established:
  - "REST tests use restTemplate.postForEntity with domain request DTOs imported from controller package"
  - "Response bodies deserialized to Map/List and assertions made on individual fields cast via Number.toLong()"

requirements-completed: [TEST-04, TEST-05]

# Metrics
duration: 5min
completed: 2026-03-17
---

# Phase 09 Plan 02: Example Tests (REST API) Summary

**Two HTTP integration tests proving user CRUD and todo-list sharing via TestRestTemplate against a live Spring Boot server backed by Testcontainers PostgreSQL**

## Performance

- **Duration:** ~5 min
- **Started:** 2026-03-18T03:28:25Z
- **Completed:** 2026-03-18T03:30:27Z
- **Tasks:** 1
- **Files modified:** 1

## Accomplishments

- Added TEST-04: creates Alice and Bob via POST /api/users, then verifies GET /api/users returns both with correct name/email/id fields
- Added TEST-05: creates two users, creates a todo list with createdBy, shares the list via POST /api/todo-lists/{id}/share, and verifies GET /api/todo-lists/{id}/shares returns the share record with correct FK values
- All 9 tests in TodoListIntegrationTest.kt now pass (4 original + 3 DSL + 2 REST)

## Task Commits

Each task was committed atomically:

1. **Task 1: Add REST API integration tests for users and sharing** - `a72cb32` (feat)

**Plan metadata:** (docs commit below)

## Files Created/Modified

- `examples/todo-list/src/test/kotlin/com/nickanderssohn/todolist/TodoListIntegrationTest.kt` - Added two new @Test methods and imports for CreateUserRequest and ShareTodoListRequest

## Decisions Made

None - followed plan as specified.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Phase 09 complete — both DSL and REST API integration tests covering user CRUD and sharing are in place
- The todo-list example is fully tested end-to-end

---
*Phase: 09-example-tests*
*Completed: 2026-03-17*
