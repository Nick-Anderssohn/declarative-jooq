# Todo List Example — declarative-jooq

A self-contained Spring Boot + Kotlin + jOOQ example demonstrating how to integrate
[declarative-jooq](../../README.md) into a real application.

## What this demonstrates

- Hand-written jOOQ Table/Record classes for two related tables (`todo_list`, `todo_item`)
- The `declarative-jooq` Gradle plugin generating a test-data seeding DSL from those classes
- Integration tests that use the generated DSL to seed hierarchical data in one declaration:
  ```kotlin
  execute(dslContext) {
      todoList {
          title = "Groceries"
          todoItem { title = "Buy milk" }
          todoItem { title = "Buy eggs" }
      }
  }
  ```
  FK wiring (`todo_item.todo_list_id`) is handled automatically.
- A REST API (CRUD for `TodoList` and `TodoItem`) tested against a real PostgreSQL database
  via Testcontainers.

## Prerequisites

- JDK 11 or later
- Docker (for Testcontainers in integration tests)

## Data model

```
todo_list
  id          BIGSERIAL PK
  title       VARCHAR(255) NOT NULL
  description TEXT
  created_at  TIMESTAMP NOT NULL

todo_item
  id           BIGSERIAL PK
  todo_list_id BIGINT NOT NULL FK -> todo_list(id) ON DELETE CASCADE
  title        VARCHAR(255) NOT NULL
  completed    BOOLEAN NOT NULL DEFAULT FALSE
  created_at   TIMESTAMP NOT NULL
```

## How to run

**Step 1:** Publish declarative-jooq to your local Maven repository (run once, or after changes):

```bash
# From the root declarative-jooq project
./gradlew publishToMavenLocal
```

**Step 2:** Run the tests:

```bash
cd examples/todo-list
./gradlew test
```

The `generateDeclarativeJooqDsl` task runs automatically before the test compilation step,
scanning the compiled jOOQ classes and generating the builder/result DSL sources into
`build/generated/declarative-jooq/`.

## REST endpoints

| Method | Path                                      | Description            |
|--------|-------------------------------------------|------------------------|
| GET    | /api/todo-lists                           | List all todo lists    |
| GET    | /api/todo-lists/{id}                      | Get a todo list by ID  |
| POST   | /api/todo-lists                           | Create a todo list     |
| DELETE | /api/todo-lists/{id}                      | Delete a todo list     |
| GET    | /api/todo-lists/{listId}/items            | List items in a list   |
| POST   | /api/todo-lists/{listId}/items            | Create an item         |
| PATCH  | /api/todo-lists/{listId}/items/{itemId}   | Toggle item completed  |
| DELETE | /api/todo-lists/{listId}/items/{itemId}   | Delete an item         |
