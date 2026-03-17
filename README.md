# declarative-jooq

Declarative test data creation for jOOQ with automatic FK resolution.

## What it does

Writing test data for a relational database usually means manually inserting parent records, capturing their generated IDs, and threading those IDs into child records — in the right order. As schemas grow, this ceremony scales with them: a three-level hierarchy requires explicit wiring at every level, and tests become brittle when the schema changes.

declarative-jooq inverts that model. You declare a graph of records as nested Kotlin builders, and the library handles insert order, FK assignment, and result assembly for you. The code generator scans your compiled jOOQ record classes and produces typed Kotlin DSL extension functions that mirror your schema's relationships.

The result is test setup code that reads like the data model it represents, with no manual ID tracking and no insert-order bookkeeping.

## Quick Example

**Before** — manual jOOQ with explicit FK wiring:

```kotlin
// Insert organization, capture ID
val orgRecord = ctx.newRecord(ORGANIZATION)
orgRecord.name = "Acme"
orgRecord.insert()
val orgId = orgRecord.id  // capture generated PK

// Insert user, wire org FK manually
val userRecord = ctx.newRecord(APP_USER)
userRecord.name = "Alice"
userRecord.email = "alice@acme.com"
userRecord.organizationId = orgId  // manually assigned
userRecord.insert()
val userId = userRecord.id  // capture again

// Insert task, wire user FK manually
val taskRecord = ctx.newRecord(TASK)
taskRecord.title = "Alice's Task"
taskRecord.createdBy = userId  // manually assigned
taskRecord.insert()
```

**After** — declarative DSL:

```kotlin
val result = execute(ctx) {
    organization {
        name = "Acme"
        organization {          // app_user nested under org via organization_id FK
            name = "Alice"
            email = "alice@acme.com"
            createdBy {         // task nested under user via created_by FK
                title = "Alice's Task"
            }
        }
    }
}
```

The library inserts the organization first, then the user with the correct `organization_id`, then the task with the correct `created_by` — all automatically.

## Features

- **Automatic FK resolution** — child records receive parent IDs without any manual wiring
- **Topological insert ordering** — records are inserted in dependency order regardless of declaration order
- **Self-referential FK support** — uses a two-pass insert strategy so parent_id can be set after child records are created
- **Multiple FKs to the same table** — disambiguated builder names (e.g., `createdBy { }` vs `updatedBy { }`) when a child table has multiple FK columns pointing to the same parent table
- **Typed result accessors** — retrieve inserted records by table name via `DslResult`
- **Declaration-order preservation** — sibling records within the same parent are inserted in the order they are declared
- **Code generator** — scans compiled jOOQ record classes via classpath scanning and produces typed Kotlin DSL extension functions
- **Gradle plugin** — registers the `generateDeclarativeJooqDsl` task and auto-wires generated sources into the test source set

## Getting Started

### 1. Publish to mavenLocal

declarative-jooq is not published to a public Maven repository. Publish it locally first:

```bash
./gradlew publishToMavenLocal
```

### 2. Add the plugin to your build

In your consumer project's `settings.gradle.kts`, add the plugin classpath:

```kotlin
pluginManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
    }
}
```

In your `build.gradle.kts`:

```kotlin
plugins {
    id("com.nickanderssohn.declarative-jooq") version "0.1.0"
}

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    testImplementation("com.nickanderssohn:dsl-runtime:0.1.0")
}
```

> Note: Check the version string against the published artifact in your local Maven repository (`~/.m2/repository/com/nickanderssohn/`).

### 3. Configure the extension

```kotlin
declarativeJooq {
    // Directory containing compiled jOOQ record classes. Required.
    classesDir.set(layout.buildDirectory.dir("classes/kotlin/main"))

    // Output package for generated DSL code. Required.
    outputPackage.set("com.example.generated")

    // Optional: restrict which jOOQ packages are scanned.
    packageFilter.set("com.example.jooq")
}
```

### 4. Generate DSL sources

```bash
./gradlew generateDeclarativeJooqDsl
```

Generated sources are placed in `build/generated/declarative-jooq/` and are automatically added to the test source set. Regenerate whenever your jOOQ record classes change.

### 5. Write tests

```kotlin
import com.nickanderssohn.declarativejooq.execute

@Test
fun `test with declarative data`() {
    val result = execute(dslContext) {
        organization {
            name = "Acme"
            organization {
                name = "Alice"
                email = "alice@acme.com"
            }
        }
    }

    val users = result.records("app_user")
    assertEquals(1, users.size)
}
```

## DSL Usage

### Root records

Any table that has no required FK columns can be declared at the top level:

```kotlin
execute(ctx) {
    organization {
        name = "Acme Corp"
    }
    category {
        name = "Electronics"
    }
}
```

### Nested records

Child tables are declared inside the parent they belong to. The builder name corresponds to the FK relationship:

```kotlin
execute(ctx) {
    organization {
        name = "Acme"
        organization {          // declares an app_user with organization_id wired to this org
            name = "Alice"
            email = "alice@acme.com"
        }
    }
}
```

### Multi-level nesting

Nesting is not limited to two levels:

```kotlin
execute(ctx) {
    organization {
        name = "Acme"
        organization {
            name = "Alice"
            email = "alice@acme.com"
            createdBy {         // task with created_by FK wired to Alice
                title = "Alice's Task"
            }
        }
    }
}
```

### Self-referential FKs

Tables with a FK to themselves are supported. The library inserts the parent first (without the self-reference), then updates the child's FK column in a second pass:

```kotlin
execute(ctx) {
    category {
        name = "Electronics"
        childCategory {
            name = "Phones"
            childCategory {
                name = "Smartphones"
            }
        }
    }
}
```

### Multiple FKs to the same table

When a child table has more than one FK column pointing to the same parent table, the code generator produces a separate builder for each FK column. Builder names are derived from the FK column name:

```kotlin
// task has created_by and updated_by, both referencing app_user
execute(ctx) {
    organization {
        name = "Acme"
        organization {
            name = "Alice"
            email = "alice@acme.com"
            createdBy {         // sets task.created_by = Alice.id
                title = "Alice's Task"
                // task.updated_by remains NULL
            }
        }
    }
}
```

### Accessing results

`execute` returns a `DslResult` that provides access to inserted records:

```kotlin
val result = execute(ctx) {
    organization { name = "Acme" }
}

// Get all records for a table by name
val orgs: List<UpdatableRecord<*>> = result.records("organization")

// Get all records grouped by table name
val all: Map<String, List<UpdatableRecord<*>>> = result.allRecords()
```

Records in each table list are in declaration order.

## Project Structure

| Module | Description |
|---|---|
| `dsl-runtime` | Core DSL engine: record graph, topological sorter, inserter, result assembler |
| `codegen` | Source code generator: scans jOOQ record classes via classpath scanning, emits typed Kotlin DSL builders using KotlinPoet |
| `gradle-plugin` | Gradle build integration: registers `generateDeclarativeJooqDsl` task, wires extension config, auto-adds generated sources to test source set |
| `integration-tests` | End-to-end tests: spins up a Postgres 16 container via Testcontainers, generates and compiles DSL sources at test time, exercises all FK scenarios |

## Building and Testing

### Prerequisites

- JDK 11 or later
- Docker (required for integration tests that use Testcontainers)

### Commands

Build all modules:

```bash
./gradlew build
```

Run unit tests (no Docker required):

```bash
./gradlew :dsl-runtime:test :codegen:test
```

Run integration tests (requires Docker):

```bash
# Build dsl-runtime test classes first (integration-tests scan them for code generation)
./gradlew :dsl-runtime:testClasses

# Then run integration tests
./gradlew :integration-tests:cleanTest :integration-tests:test
```

Run all tests:

```bash
./gradlew test
```

Publish to local Maven repository:

```bash
./gradlew publishToMavenLocal
```

## Tech Stack

| Component | Version |
|---|---|
| Kotlin | 2.1.20 |
| jOOQ | 3.19.16 |
| KotlinPoet | 2.2.0 |
| ClassGraph | 4.8.181 |
| Gradle | 8.12 |
| JUnit | 5.11.4 |
| Testcontainers | 1.20.6 |
| H2 (unit tests) | 2.3.232 |
| PostgreSQL (integration) | 16 (Alpine) |
| JVM target | 11 |

## Limitations

- **Composite foreign keys are not supported.** Only single-column FK relationships are handled.
- **Published via mavenLocal only.** There is no public Maven Central artifact.
- **Insert-only.** The library creates records; it does not support queries, updates, or deletes.
- **No schema management.** declarative-jooq assumes your schema already exists; it does not create or migrate tables.
- **jOOQ record classes required.** The code generator needs compiled jOOQ-generated record classes on the classpath; hand-written POJOs are not supported.
