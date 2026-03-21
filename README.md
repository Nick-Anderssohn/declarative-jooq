# declarative-jooq

[![CI](https://github.com/Nick-Anderssohn/declarative-jooq/actions/workflows/ci.yml/badge.svg)](https://github.com/Nick-Anderssohn/declarative-jooq/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/com.nickanderssohn/declarative-jooq-dsl-runtime)](https://central.sonatype.com/artifact/com.nickanderssohn/declarative-jooq-dsl-runtime)

declarative-jooq is the best way to set up your test data if you use [jOOQ](https://github.com/jooq/jooq) and kotlin.
This tool scans all of your jooq classes and generates a DSL that allows you to set up your test
records in a declarative manner. For example:

```kotlin
// Dec.Dsl.execute inserts all records defined by the block passed to it.
val testData = DecDsl.execute(ctx) {
    organization {
        name = "Example Organization"
        
        user {           
            name = "Alice"
            email = "alice@example.com"
            
            // The task table has 2 columns pointing to user, so we specify created_by explicitly.
            task(TaskTable.TASK.CREATED_BY) {   
                title = "Alice's Task"
            }
        }
    }
}
```

The above code creates an organization, a user belonging to an organization, and a task belonging to that user.
This is a much more readable approach than setting up your test data via inserts. e.g.

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

## Getting Started

### 1. Add the plugin to your build

In your consumer project's `settings.gradle.kts`, add Maven Central to the plugin repositories:

```kotlin
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}
```

In your `build.gradle.kts`:

```kotlin
plugins {
    id("com.nickanderssohn.declarative-jooq") version "1.1.0"
}

dependencies {
    testImplementation("com.nickanderssohn:declarative-jooq-dsl-runtime:1.1.0")
}
```

### 2. Configure the extension

```kotlin
declarativeJooq {
    // Directory containing compiled jOOQ record classes. Required.
    classesDir.set(layout.buildDirectory.dir("classes/kotlin/main"))

    // Output package for generated DSL code. Required.
    outputPackage.set("com.example.generated")

    // Optional: restrict which jOOQ packages are scanned.
    packageFilter.set("com.example.jooq")

    // Optional: output directory for generated sources. Default: build/generated/declarative-jooq
    outputDir.set(layout.buildDirectory.dir("generated/declarative-jooq"))

    // Optional: source set to wire generated sources into. Default: "test"
    // Override to "main" if you need the generated DSL accessible from production code.
    sourceSet.set("test")
}
```

### 3. Generate DSL sources

```bash
./gradlew generateDeclarativeJooqDsl
```

Generated sources are placed in the `outputDir` directory (default: `build/generated/declarative-jooq/`) and are automatically added to the configured `sourceSet` (default: `test`). Regenerate whenever your jOOQ record classes change.

## DSL Usage

### Root records

Any table that has no required FK columns can be declared at the top level:

```kotlin
DecDsl.execute(ctx) {
    organization {
        name = "Example Org"
    }
    category {
        name = "Electronics"
    }
}
```

### Nested records

Child tables are declared inside the parent they belong to. The builder name corresponds to the child table name:

```kotlin
DecDsl.execute(ctx) {
    organization {
        name = "Example Org"
        user {           // "user" nested under organization via organization_id FK
            name = "Alice"
            email = "alice@example.com"
        }
    }
}
```

### FK Disambiguation

If a table has multiple FKs pointing to the same table, you can pass in which column to use explicitly:

```kotlin
DecDsl.execute(ctx) {
    organization {
        user {           
            name = "Alice"
            email = "alice@example.com"
            
            // task has 2 columns pointing to user (created_by and updated_by), so we specify created_by explicitly.
            task(TaskTable.TASK.CREATED_BY) {   
                title = "Alice's Task"
            }
        }
    }
}
```

### Explicit Wiring

Sometimes it is useful to reference a record in a separate block. Each block returns a result object representing
the record it creates. e.g. the example below shows a task that is owned by user 2 (via the `created_by` column),
but was updated by user 1 (via the `updated_by` column).

```kotlin
DecDsl.execute(dslContext) {
    organization {
        val user1 = user {
            name = "User 1"
            email = "user1@example.com"
        }

        user {
            name = "User 2"
            email = "user2@example.com"

            task(TaskTable.TASK.CREATED_BY) {
                title = "User 2's Task"

                // references user 1 even though this task is owned by user 2
                updatedBy = user1
            }
        }
    }
}
```

### Composite FKs

Composite (multi-column) foreign keys work the same way as single-column FKs. When a table has only one FK to its parent, nesting is automatic:

```kotlin
DecDsl.execute(ctx) {
    organization {
        name = "Example"
        // department has a composite PK (organization_id, department_id)
        department {
            departmentId = 10
            name = "Engineering"
            // employee has a composite FK (organization_id, department_id) -> department
            employee {
                name = "Alice"
            }
        }
    }
}
```

If a table has multiple composite FKs to the same parent (or a mix of single and composite FKs), pass all columns that make up the FK to disambiguate:

```kotlin
DecDsl.execute(ctx) {
    organization {
        name = "Example"
        // department has a composite PK (organization_id, department_id)
        department {
            departmentId = 10
            name = "Engineering"
            // employee has a composite FK (organization_id, department_id) -> department
            employee(EmployeeTable.EMPLOYEE.ORGANIZATION_ID, EmployeeTable.EMPLOYEE.DEPARTMENT_ID) {
                name = "Alice"
            }
        }
    }
}
```

This is the same pattern used for [single-column FK disambiguation](#fk-disambiguation) — you just pass multiple columns instead of one.

### Accessing The Resulting Records

`DecDsl.execute` returns a `DslResult`. Use the jOOQ **table name** (usually the lowercase SQL name) to retrieve every inserted row of that type; within each table, rows appear in **declaration order**.

```kotlin
val result = DecDsl.execute(ctx) {
    organization {
        name = "Example Org"
        user { name = "Alice"; email = "alice@example.com" }
        user { name = "Bob"; email = "bob@example.com" }
    }
}

val org = result.records("organization").single() as OrganizationRecord
val users = result.records("user").map { it as UserRecord }
```

Alternatively, you can use the result returned directly from the builder as shown in the [Explicit Wiring](#explicit-wiring) section.

```kotlin
lateinit var org: OrganizationResult
lateinit var user1: UserResult
lateinit var user2: UserResult

val result = DecDsl.execute(ctx) {
    org = organization {
        name = "Example Org"
        user1 = user { name = "Alice"; email = "alice@example.com" }
        user2 = user { name = "Bob"; email = "bob@example.com" }
    }
}

// org, user1, and user2 are all usable here and populated with all data from the inserts ran by DecDsl.execute()
```

## Thanks

Thanks to [GSD](https://github.com/gsd-build/get-shit-done/tree/main) and [Claude Code](https://code.claude.com/docs/en/overview).
Part of the reason I built this library was to test those two tools, but the final result is a library that is genuinely
useful. I'd call it a success!