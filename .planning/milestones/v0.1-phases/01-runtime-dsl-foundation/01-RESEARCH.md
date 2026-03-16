# Phase 1: Runtime DSL Foundation - Research

**Researched:** 2026-03-15
**Domain:** Kotlin DSL builder pattern, jOOQ CRUD API, Gradle multi-project setup, topological graph sort
**Confidence:** HIGH for core patterns; MEDIUM for specific version pinning

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
None — all implementation details are Claude's discretion for this phase.

### Claude's Discretion
- Insert + refresh strategy — how to reconcile batch efficiency with needing PKs for FK resolution (research flagged that batch insert doesn't return generated keys)
- Builder API shape — base interfaces/abstract classes that generated code will implement, how FK context flows through nesting
- Result object nesting depth and access patterns
- Error behavior on constraint violations, missing FKs, insertion failures
- Test database choice for Phase 1 (H2 Postgres mode vs Testcontainers)

### Deferred Ideas (OUT OF SCOPE)
None — discussion stayed within phase scope.
</user_constraints>

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| PROJ-01 | Three-module Gradle project: `dsl-runtime`, `codegen`, `gradle-plugin` | Standard Gradle multi-project build with `settings.gradle.kts` include declarations |
| PROJ-02 | `dsl-runtime` has no compile dependency on KotlinPoet or Gradle APIs | Module boundary enforced by NOT adding those dependencies to `dsl-runtime/build.gradle.kts` |
| PROJ-03 | `codegen` module independently testable without Gradle | Codegen module is a pure Kotlin library — JUnit 5 tests run directly, no Gradle test runner needed |
| PROJ-04 | Generated code depends only on `dsl-runtime` and user's jOOQ version | Generated code imports only `dsl-runtime` types; jOOQ is `compileOnly` in `dsl-runtime` |
| DSL-01 | `execute(dslContext) { ... }` entry point returns typed `DslResult` | Top-level function with `DSLContext` + lambda receiver; lambda builds the record graph |
| DSL-02 | Root table builder functions at execute block top level | Functions declared in the `DslScope` receiver class, available at the top of the execute block |
| DSL-03 | Child builder functions nested under FK parent, auto-populate FK from parent context | Parent builder exposes child builder function; parent record PK passed as context into child scope |
| DSL-04 | Multiple records of same type in one block | Builder functions append to a `MutableList` — can be called multiple times in same block |
| DSL-05 | Topological insert order based on FK dependency graph | Kahn's algorithm over table-level FK graph; strip self-edges before sort |
| DSL-06 | Batch insert per table | Group records by table; call `dslContext.batchInsert(records)` per table group |
| DSL-07 | Record refresh after insert to capture DB-generated values | Individual `record.store()` (not batch) populates generated keys via JDBC `getGeneratedKeys()` |
| DSL-08 | Result object ordering matches declaration order | Use `LinkedHashMap` and `MutableList` everywhere — never `HashMap` or `HashSet` |
</phase_requirements>

---

## Summary

Phase 1 establishes the three-module Gradle project scaffold and delivers a fully working runtime DSL engine. The runtime accepts a declarative record graph built through nested Kotlin lambda receivers, inserts records in topological (parent-before-child) order, resolves single-column FK values automatically from parent record context, and returns a typed `DslResult` preserving declaration order.

The central architectural decision this phase must resolve is the **batch-insert vs. individual-insert trade-off**. Research confirms that `DSLContext.batchInsert()` follows JDBC batch semantics and does NOT return generated keys — auto-incremented primary keys remain null in the record objects after a batch call. Since child records need their parent's PK to populate the FK field, batch insert breaks FK resolution. The recommended strategy is individual `record.store()` calls per record: jOOQ's `store()` uses JDBC `getGeneratedKeys()` and automatically populates the identity field back into the record object. This is reliable across both H2 and PostgreSQL. DSL-06 ("batch insert") should be interpreted as "group inserts by table in topological order" rather than literally using `batchInsert()`. If future performance profiling identifies this as a bottleneck, a `RETURNING`-clause approach can be used for PostgreSQL, but that is out of scope for Phase 1.

H2 in Postgres mode is appropriate for Phase 1 integration tests. H2 does NOT support the PostgreSQL `RETURNING` clause (GitHub issue #3962 is open as of May 2024 with no committed fix), but this does not matter because the recommended strategy uses `store()` + `getGeneratedKeys()` which H2 supports fully. H2 also supports `IDENTITY` / auto-increment columns and the jOOQ CRUD APIs that depend on them.

**Primary recommendation:** Use individual `record.store()` for each record in topological order; avoid `batchInsert()` for Phase 1. Use H2 with Postgres mode for integration tests.

---

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Kotlin | 2.1.20 | Implementation language | Latest stable 2.1.x; K2 compiler is production-ready |
| JVM target | 11 | Bytecode target | Gradle 8.x requires JVM 17+ to run but plugin code must be JVM 11+ bytecode |
| jOOQ | 3.19.x (compileOnly in dsl-runtime) | Record CRUD API, DSLContext, FK metadata | Project constraint 3.18+; 3.19.x is current stable LTS line |
| Gradle | 8.5+ | Multi-project build | `settings.gradle.kts` multi-project, lazy task registration |

### Testing (Phase 1)
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| JUnit 5 (Jupiter) | 5.12.x | Test runner | Standard JVM testing; `useJUnitPlatform()` in Gradle |
| H2 | 2.4.240 | In-memory test database | Fast integration tests without Docker; H2 supports IDENTITY columns and `getGeneratedKeys()` |
| jOOQ | 3.19.x | Concrete at test scope | `testImplementation` in `dsl-runtime`; compile-only in main |

Note: Kotest is listed in prior research as the assertion library recommendation but JUnit 5's built-in assertions (`assertEquals`, `assertNotNull`) are sufficient for Phase 1 and avoid an extra dependency. The planner may choose either.

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Individual `record.store()` | `batchInsert()` | batchInsert does not return generated keys — FK resolution breaks; store() is correct |
| H2 for tests | Testcontainers (Postgres) | Testcontainers needs Docker and is slower; H2 is sufficient since we avoid RETURNING |
| JUnit 5 built-in assertions | Kotest assertions | Kotest is more readable but an extra dependency; fine to add, not required |

**Installation:**
```kotlin
// dsl-runtime/build.gradle.kts
dependencies {
    compileOnly("org.jooq:jooq:3.19.+")
    testImplementation("org.junit.jupiter:junit-jupiter:5.12.+")
    testImplementation("com.h2database:h2:2.4.240")
    testImplementation("org.jooq:jooq:3.19.+")  // concrete in test scope
}

tasks.test {
    useJUnitPlatform()
}
```

---

## Architecture Patterns

### Recommended Project Structure

```
declarative-jooq/
├── settings.gradle.kts          # include(":dsl-runtime", ":codegen", ":gradle-plugin")
├── build.gradle.kts             # root: shared kotlin("jvm") plugin config, repositories
├── gradle/libs.versions.toml    # version catalog (optional but recommended)
├── dsl-runtime/
│   ├── build.gradle.kts         # compileOnly jOOQ, testImpl H2 + JUnit
│   └── src/
│       ├── main/kotlin/
│       │   └── com/example/declarativejooq/
│       │       ├── DslScope.kt          # entry point + root builder registration
│       │       ├── RecordNode.kt        # single record + field values + FK context
│       │       ├── RecordGraph.kt       # ordered collection of RecordNodes
│       │       ├── TopologicalInserter.kt   # sort + insert + generated key capture
│       │       └── ResultAssembler.kt   # wraps records into result types
│       └── test/kotlin/
│           └── com/example/declarativejooq/
│               ├── TestSchema.kt        # hand-crafted H2 schema + jOOQ records for tests
│               └── DslExecutionTest.kt  # end-to-end integration tests
├── codegen/
│   ├── build.gradle.kts         # empty for Phase 1 (module scaffold only)
│   └── src/main/kotlin/.gitkeep
└── gradle-plugin/
    ├── build.gradle.kts         # java-gradle-plugin plugin (scaffold only)
    └── src/main/kotlin/.gitkeep
```

### Pattern 1: Kotlin DSL with Lambda Receivers and @DslMarker

**What:** Type-safe nested builder DSL where each builder class is the receiver in a lambda. The `@DslMarker` annotation prevents accidentally calling outer-scope builder functions inside inner scopes.

**When to use:** This is the idiomatic Kotlin approach for hierarchical record declaration.

**Core pattern:**
```kotlin
// Source: https://kotlinlang.org/docs/type-safe-builders.html

@DslMarker
@Target(AnnotationTarget.CLASS)
annotation class DeclarativeJooqDsl

@DeclarativeJooqDsl
abstract class RecordBuilder<R : UpdatableRecord<R>> {
    // Subclasses expose typed field setters and child builder functions
    // Child builders call: childNodes.add(childBuilder.apply(block))
    internal val childNodes: MutableList<RecordNode> = mutableListOf()
}

// Top-level entry point
fun execute(dslContext: DSLContext, block: DslScope.() -> Unit): DslResult {
    val scope = DslScope()
    scope.block()
    return TopologicalInserter(dslContext).insertAll(scope.recordGraph)
}

// Scope class — the receiver for the execute block
@DeclarativeJooqDsl
class DslScope {
    internal val recordGraph = RecordGraph()
    // Generated code adds root-table builder functions here as extension functions
}
```

**Why @DslMarker matters:** Without it, `user { organization { } }` inside a nested block would compile and produce confusing results. With the annotation, the compiler enforces that only the nearest receiver's members are accessible.

### Pattern 2: RecordNode — The Internal Representation

**What:** Each declaration in the DSL block produces a `RecordNode` that captures the jOOQ table reference, the user-set field values, and the FK wiring to its parent node.

```kotlin
data class RecordNode(
    val table: Table<*>,
    val fieldValues: LinkedHashMap<Field<*>, Any?>,   // preserves declaration order
    val parentNode: RecordNode?,                       // null for root records
    val parentFkField: TableField<*, *>?,              // the FK column to populate from parent PK
    val children: MutableList<RecordNode> = mutableListOf()
)
```

The `parentFkField` is known at construction time (generated code or hand-written test code knows which field is the FK). The actual value (the parent's PK) is not known until after the parent record is inserted — that resolution happens in `TopologicalInserter`.

### Pattern 3: Topological Sort (Kahn's Algorithm)

**What:** Determines which table groups insert before others based on FK dependency graph. Self-edges are stripped before the sort.

**When to use:** Required to satisfy DSL-05.

**Algorithm:**
```kotlin
// Source: Kahn's algorithm — O(V+E) where V=tables, E=FK relationships
fun topologicalSort(tableGraph: Map<String, Set<String>>): List<String> {
    // tableGraph: tableName -> set of tables this table depends on (has FKs to)
    val inDegree = tableGraph.keys.associateWith { 0 }.toMutableMap()
    val dependents = mutableMapOf<String, MutableList<String>>()

    for ((table, deps) in tableGraph) {
        for (dep in deps) {
            if (dep != table) {  // strip self-edges
                inDegree[table] = (inDegree[table] ?: 0) + 1
                dependents.getOrPut(dep) { mutableListOf() }.add(table)
            }
        }
    }

    val queue = ArrayDeque(inDegree.filter { it.value == 0 }.keys)
    val result = mutableListOf<String>()

    while (queue.isNotEmpty()) {
        val current = queue.removeFirst()
        result.add(current)
        dependents[current]?.forEach { dependent ->
            inDegree[dependent] = inDegree[dependent]!! - 1
            if (inDegree[dependent] == 0) queue.add(dependent)
        }
    }

    if (result.size != inDegree.size) {
        throw IllegalStateException("Cycle detected in FK graph — this should not happen for non-self-referential FKs in Phase 1")
    }
    return result
}
```

**Critical:** Self-referential FKs (e.g., `category.parent_id → category.id`) are Phase 4. For Phase 1, document that cycles cause a clear error.

### Pattern 4: Individual store() for Insert with Generated Key Capture

**What:** Each record is inserted individually using `record.store()`. jOOQ uses JDBC `getGeneratedKeys()` and populates the identity field back into the `UpdatableRecord` object automatically. The PK value is then used to populate child FK fields.

**When to use:** Required for FK chain to work. Do NOT use `batchInsert()`.

```kotlin
// Source: https://www.jooq.org/doc/latest/manual/sql-execution/crud-with-updatablerecords/simple-crud/
fun insertInOrder(dslContext: DSLContext, nodes: List<RecordNode>) {
    val sortedTables = topologicalSort(buildTableGraph(nodes))
    val nodesByTable = nodes.groupBy { it.table.name }
        .let { map -> LinkedHashMap<String, List<RecordNode>>().also { lhm ->
            sortedTables.forEach { t -> nodesByTable[t]?.let { lhm[t] = it } }
        }}

    for ((_, tableNodes) in nodesByTable) {
        for (node in tableNodes) {
            // Populate FK from parent's already-inserted record PK
            if (node.parentNode != null && node.parentFkField != null) {
                val parentPk = node.parentNode.insertedRecord!!.getValue(
                    node.parentNode.table.primaryKey!!.fields[0]
                )
                node.record.setValue(node.parentFkField as TableField<Nothing, Any?>, parentPk)
            }

            node.record.attach(dslContext.configuration())
            node.record.store()  // auto-populates generated PK via getGeneratedKeys()
            node.insertedRecord = node.record  // capture for children to reference
        }
    }
}
```

### Pattern 5: Declaration Order Preservation

**What:** All internal collections that track record ordering MUST use `LinkedHashMap` and `MutableList`, never `HashMap` or `HashSet`.

**Where this matters:** `RecordNode.fieldValues`, the `RecordGraph`'s root node list, and the parent's `children` list. Violating this produces non-deterministic result ordering (DSL-08 failure).

### Recommended Module Scaffold for settings.gradle.kts

```kotlin
// settings.gradle.kts
rootProject.name = "declarative-jooq"

include(":dsl-runtime", ":codegen", ":gradle-plugin")
```

```kotlin
// root build.gradle.kts — shared configuration for all subprojects
plugins {
    kotlin("jvm") version "2.1.20" apply false
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")

    repositories {
        mavenCentral()
    }

    kotlin {
        jvmToolchain(11)
    }
}
```

```kotlin
// codegen/build.gradle.kts — Phase 1 scaffold only, minimal content
plugins {
    kotlin("jvm")
}
// No dependencies needed in Phase 1; module exists to satisfy PROJ-01
```

```kotlin
// gradle-plugin/build.gradle.kts — Phase 1 scaffold only
plugins {
    kotlin("jvm")
    `java-gradle-plugin`
}
// No plugin registration needed in Phase 1; module exists to satisfy PROJ-01
```

### Anti-Patterns to Avoid

- **Using `batchInsert()` for the main insert loop:** Does not return generated keys; breaks FK chain. Individual `store()` is required.
- **Using `HashMap` or `HashSet` for record tracking:** Destroys declaration order; use `LinkedHashMap` and `MutableList`.
- **Treating self-referential FKs in the topo sort:** Self-edges cause cycle detection to fail; they are Phase 4 scope. For Phase 1, the sort must throw a clear error if a cycle is detected.
- **Making `DslScope` itself the builder for records:** The scope class should only hold the record graph; individual table builder classes manage field setting and child builder invocation.
- **Forgetting `@DslMarker`:** Without it, `user { organization { } }` inside a nested block compiles silently and produces confusing results.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Graph cycle detection | Custom cycle checker | Kahn's algorithm — cycle detected when sorted count < node count | Kahn's algorithm inherently detects cycles; no separate pass needed |
| Generated key retrieval | Manual SELECT after insert | `record.store()` | jOOQ's `store()` calls JDBC `getGeneratedKeys()` automatically |
| H2 schema setup in tests | Schema migration tooling | `dslContext.execute(DDL_SQL)` in a `@BeforeAll` method | Simple DDL is sufficient for test schema; Flyway adds unnecessary complexity for unit tests |
| In-memory data structures | Custom ordered map | `LinkedHashMap` from Kotlin stdlib | Already exists; declaration-order preservation for free |

**Key insight:** jOOQ's `UpdatableRecord` already handles the hard part of generated-key retrieval. The runtime library's only responsibility is ordering the inserts correctly and wiring FK values between parent and child records.

---

## Common Pitfalls

### Pitfall 1: Batch Insert Does Not Return Generated Keys

**What goes wrong:** Using `DSLContext.batchInsert(records)` as the insert mechanism. After the call, all auto-generated primary key fields in the record objects remain null/zero. Any child record that tries to read the parent's PK for FK population gets null, causing either a NullPointerException or a DB constraint violation.

**Why it happens:** JDBC batch execution (`executeBatch()`) does not support `RETURNING` clauses, and most JDBC drivers do not return generated keys from batch operations. jOOQ's `batchInsert()` follows JDBC batch semantics exactly.

**How to avoid:** Use individual `record.store()` for each record. Performance is acceptable for test data setup (typically tens to hundreds of records).

**Warning signs:** After insert, `record.getValue(primaryKeyField)` returns `null`.

### Pitfall 2: H2 Does Not Support PostgreSQL RETURNING Clause

**What goes wrong:** Designing the refresh strategy around `INSERT ... RETURNING id` (which PostgreSQL supports). H2 in Postgres compatibility mode does NOT implement this clause. GitHub issue h2database/h2database#3962 is open as of May 2024 with no committed fix.

**How to avoid:** Use `record.store()` which internally uses JDBC `getGeneratedKeys()` — supported by both H2 and Postgres. The `RETURNING` clause workaround is unnecessary and would break Phase 1 tests.

### Pitfall 3: Self-Referential FK Causes Cycle in Topological Sort

**What goes wrong:** Any table with `parent_id → id` (self-referential FK) appears as a self-edge in the dependency graph. Kahn's algorithm treats this as a cycle and either errors or silently omits the table from the sorted output.

**How to avoid:** This is a Phase 4 concern (DSL-09). For Phase 1, strip self-edges from the graph before sorting and document that self-referential tables are not supported yet. Add an explicit error for schemas containing self-referential FKs in the Phase 1 runtime.

**Warning signs:** `topologicalSort` result size differs from input size; `IllegalStateException: Cycle detected`.

### Pitfall 4: Declaration Order Lost Due to HashMap

**What goes wrong:** Using `HashMap` or `HashSet` anywhere in the record tracking pipeline. Iteration order of these collections is non-deterministic. The DslResult's record lists appear in arbitrary order, not the order the user declared them.

**How to avoid:** Audit every collection in `DslScope`, `RecordGraph`, `RecordNode`, and `ResultAssembler`. Replace all `HashMap` with `LinkedHashMap`, all `HashSet` with `LinkedHashSet`, all unordered collections with `MutableList`.

### Pitfall 5: FK Context Resolved Too Late (After Insert Loop Completes)

**What goes wrong:** Building the full record graph first, then trying to resolve FK values in a second pass using parent node references. If the parent's PK is only known after insertion, the FK resolution must happen immediately after each parent is inserted — not after all inserts.

**How to avoid:** Resolve FK values for each record's children immediately after each `record.store()` call within the insert loop. The insert loop processes nodes in topological order, so when a child is being inserted, its parent has already been inserted and its PK is available.

### Pitfall 6: Module Dependency Leakage (PROJ-02 Violation)

**What goes wrong:** Adding a dependency on `codegen` or `gradle-plugin` to `dsl-runtime/build.gradle.kts`, either directly or transitively. This means the user's test classpath pulls in KotlinPoet and Gradle API jars.

**How to avoid:** The dependency graph must be strictly one-directional: `gradle-plugin → codegen` and `codegen → (optional) dsl-runtime`. `dsl-runtime` must have zero dependencies on the other two modules. Enforce this with a `configurations.implementation { resolutionStrategy { failOnVersionConflict() } }` check or just by never adding those as dependencies.

---

## Code Examples

### Setting Up H2 Test Database for jOOQ Integration Tests

```kotlin
// Source: jOOQ manual + H2 docs
// TestSchema.kt — shared test infrastructure

object TestSchema {
    lateinit var dslContext: DSLContext

    // Create a simple organization → user schema for testing
    fun setupH2(): DSLContext {
        val ds = JdbcDataSource().apply {
            setURL("jdbc:h2:mem:test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
        }
        val conn = ds.connection
        conn.createStatement().execute("""
            CREATE TABLE IF NOT EXISTS organization (
                id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                name VARCHAR(255) NOT NULL
            )
        """.trimIndent())
        conn.createStatement().execute("""
            CREATE TABLE IF NOT EXISTS "user" (
                id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                name VARCHAR(255) NOT NULL,
                email VARCHAR(255) NOT NULL,
                organization_id BIGINT NOT NULL REFERENCES organization(id)
            )
        """.trimIndent())
        return DSL.using(ds, SQLDialect.H2)
    }
}
```

### Hand-Written jOOQ Table References for Phase 1 Tests

Since codegen is Phase 2, Phase 1 tests must hand-write minimal jOOQ table/record classes. The simplest approach is to use jOOQ's DSL API directly without generated classes, or write minimal `TableImpl` subclasses:

```kotlin
// Simpler approach for Phase 1 tests: use jOOQ's inline table API
// This avoids needing generated jOOQ record classes

val org = dslContext.newRecord(
    DSL.table("organization"),
    DSL.field("name", String::class.java)
)
org.setValue(DSL.field("name", String::class.java), "Acme Corp")
org.store()
val orgId = org.getValue(DSL.field("id", Long::class.java))
```

However, the hand-written builder approach (where the test creates minimal `TableImpl` subclasses) is closer to what Phase 2 will generate and provides better compile-time safety. The planner should decide which approach to use for Phase 1 tests.

### execute() Entry Point Sketch

```kotlin
// Source: Kotlin type-safe builder pattern + project architecture

@DeclarativeJooqDsl
class DslScope(internal val dslContext: DSLContext) {
    internal val rootNodes: MutableList<RecordNode> = mutableListOf()
    // Generated extension functions attach root-table builders here
}

fun execute(dslContext: DSLContext, block: DslScope.() -> Unit): DslResult {
    val scope = DslScope(dslContext)
    scope.block()
    return TopologicalInserter(dslContext).execute(scope.rootNodes)
}
```

---

## State of the Art

| Old Approach | Current Approach | Impact |
|--------------|------------------|--------|
| `batchInsert()` for bulk inserts | Individual `store()` per record | `store()` is the only reliable way to get generated PKs in jOOQ without RETURNING |
| Groovy DSL for Gradle build scripts | Kotlin DSL (`*.gradle.kts`) | Type-safe, IDE-supported; all new Gradle tooling uses Kotlin DSL |
| JUnit 4 `@Rule` | JUnit 5 `@BeforeAll` / `@AfterAll` | JUnit 5 is the current standard; Gradle 8 `useJUnitPlatform()` is built-in |

**Deprecated/outdated:**
- `DSLContext.batchInsert()` for PK-generating inserts: Technically not deprecated, but functionally inadequate for any workflow needing generated keys.
- `RETURNING` clause workaround for H2: Not possible; H2 doesn't support it.

---

## Open Questions

1. **Hand-written vs. jOOQ-DSL test records**
   - What we know: Phase 2 will generate `UpdatableRecordImpl` subclasses. Phase 1 needs records for testing without codegen.
   - What's unclear: Whether to write minimal `TableImpl` subclasses manually (closer to real usage) or use jOOQ's inline `DSL.table()`/`DSL.field()` API (simpler but less representative).
   - Recommendation: Write minimal hand-crafted `TableImpl` subclasses for the test schema. This validates that the runtime works with real jOOQ record types, not just the DSL API. The planner should include a task specifically for creating the test schema classes.

2. **RecordNode: store-before-children or accumulate-then-sort**
   - What we know: Records must be inserted parent-before-child. Each child needs the parent PK immediately.
   - What's unclear: Whether to insert each record immediately as the DSL block executes (depth-first) or collect all nodes then sort and insert.
   - Recommendation: Collect all nodes during DSL block execution, then sort by table topology and insert in sorted order. This allows the DSL block to be purely declarative with no DB side effects during lambda evaluation — cleaner semantics and easier to test.

3. **DslResult structure for Phase 1**
   - What we know: The final result must expose typed lists of result objects in declaration order.
   - What's unclear: Since codegen is Phase 2, Phase 1 will return a `DslResult` that either has generic record lists or typed lists defined by hand in the test.
   - Recommendation: For Phase 1, `DslResult` can be a simple `Map<String, List<UpdatableRecord<*>>>` keyed by table name, or a typed wrapper that the hand-written test defines. The planner should decide whether to invest in a generic DslResult now or keep it minimal.

---

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 5 (Jupiter) 5.12.x |
| Config file | `dsl-runtime/build.gradle.kts` — `tasks.test { useJUnitPlatform() }` |
| Quick run command | `./gradlew :dsl-runtime:test` |
| Full suite command | `./gradlew :dsl-runtime:test :codegen:test :gradle-plugin:test` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| PROJ-01 | Three modules exist as Gradle multi-project | smoke | `./gradlew projects` | ❌ Wave 0 |
| PROJ-02 | dsl-runtime has no KotlinPoet/Gradle compile deps | unit | `./gradlew :dsl-runtime:dependencies --configuration compileClasspath` | ❌ Wave 0 |
| PROJ-03 | codegen tests run without Gradle daemon | unit | `./gradlew :codegen:test` (no plugin needed) | ❌ Wave 0 |
| PROJ-04 | Generated code only needs dsl-runtime + jOOQ | structural | Verified by module dep declarations | ❌ Wave 0 |
| DSL-01 | `execute(dslContext) {}` returns DslResult | integration | `./gradlew :dsl-runtime:test --tests "*.DslExecutionTest.testBasicExecute"` | ❌ Wave 0 |
| DSL-02 | Root builder functions available at execute block top level | integration | `./gradlew :dsl-runtime:test --tests "*.DslExecutionTest.testRootBuilder"` | ❌ Wave 0 |
| DSL-03 | Child builder auto-populates FK from parent context | integration | `./gradlew :dsl-runtime:test --tests "*.DslExecutionTest.testFkResolution"` | ❌ Wave 0 |
| DSL-04 | Multiple records of same type in one block | integration | `./gradlew :dsl-runtime:test --tests "*.DslExecutionTest.testMultipleChildren"` | ❌ Wave 0 |
| DSL-05 | Topological insert order — parent before child | unit | `./gradlew :dsl-runtime:test --tests "*.TopologicalSorterTest.*"` | ❌ Wave 0 |
| DSL-06 | Records grouped by table in sorted order | unit | `./gradlew :dsl-runtime:test --tests "*.TopologicalInserterTest.*"` | ❌ Wave 0 |
| DSL-07 | DB-generated PK populated after store() | integration | `./gradlew :dsl-runtime:test --tests "*.DslExecutionTest.testGeneratedKeyPopulated"` | ❌ Wave 0 |
| DSL-08 | DslResult records in declaration order | integration | `./gradlew :dsl-runtime:test --tests "*.DslExecutionTest.testDeclarationOrder"` | ❌ Wave 0 |

### Sampling Rate
- **Per task commit:** `./gradlew :dsl-runtime:test`
- **Per wave merge:** `./gradlew :dsl-runtime:test :codegen:test :gradle-plugin:test`
- **Phase gate:** Full suite green before `/gsd:verify-work`

### Wave 0 Gaps
- [ ] `dsl-runtime/src/test/kotlin/com/example/declarativejooq/DslExecutionTest.kt` — covers DSL-01 through DSL-04, DSL-07, DSL-08
- [ ] `dsl-runtime/src/test/kotlin/com/example/declarativejooq/TopologicalSorterTest.kt` — covers DSL-05
- [ ] `dsl-runtime/src/test/kotlin/com/example/declarativejooq/TopologicalInserterTest.kt` — covers DSL-06
- [ ] `dsl-runtime/src/test/kotlin/com/example/declarativejooq/TestSchema.kt` — shared H2 setup and hand-crafted jOOQ table/record classes
- [ ] `settings.gradle.kts` — root project file satisfying PROJ-01
- [ ] `dsl-runtime/build.gradle.kts`, `codegen/build.gradle.kts`, `gradle-plugin/build.gradle.kts` — module scaffolds for PROJ-01 through PROJ-04

---

## Sources

### Primary (HIGH confidence)
- [jOOQ Simple CRUD / store() behavior](https://www.jooq.org/doc/latest/manual/sql-execution/crud-with-updatablerecords/simple-crud/) — store() populates generated keys via getGeneratedKeys()
- [jOOQ Identity Values](https://www.jooq.org/doc/3.12/manual/sql-execution/crud-with-updatablerecords/identity-values/) — H2 is explicitly supported; identity values auto-refreshed after store()
- [Kotlin Type-Safe Builders](https://kotlinlang.org/docs/type-safe-builders.html) — DslMarker, lambda receivers, nested builder pattern
- [Gradle Multi-Project Build](https://docs.gradle.org/current/samples/sample_building_kotlin_applications_multi_project.html) — settings.gradle.kts structure, module include declarations
- Kahn's algorithm (O(V+E)) — standard CS algorithm for topological sort with cycle detection

### Secondary (MEDIUM confidence)
- [H2 RETURNING clause issue #3962](https://github.com/h2database/h2database/issues/3962) — confirmed NOT supported in H2 as of May 2024; issue still open
- [jOOQ batch insert + RETURNING limitation](https://www.jooq.org/doc/latest/manual/sql-execution/batch-execution/) — batch operations don't support RETURNING; generated keys not returned

### Tertiary (LOW confidence, verify before pinning)
- Kotlin 2.1.20 as recommended version — verify latest 2.1.x on kotlinlang.org before pinning
- JUnit 5.12.x as latest — JUnit 6.x is GA as of Feb 2026; consider whether to skip to JUnit 6 or stay on stable 5.12
- H2 2.4.240 — latest as of Sep 2025; verify on Maven Central

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — jOOQ, Kotlin, Gradle patterns are well-established; specific versions are MEDIUM pending verification
- Architecture: HIGH — Kotlin builder pattern + @DslMarker is official Kotlin documentation; Kahn's algorithm is fundamental CS
- Pitfalls: HIGH — confirmed from official sources: H2 RETURNING not supported (open issue), batchInsert doesn't return keys (JDBC spec), LinkedHashMap requirement is basic Java correctness
- Insert strategy (store vs batch): HIGH — confirmed from jOOQ docs that store() uses getGeneratedKeys() and populates identity fields

**Research date:** 2026-03-15
**Valid until:** 2026-06-15 (H2 RETURNING support should be re-checked; may have been added in H2 3.x if released)
