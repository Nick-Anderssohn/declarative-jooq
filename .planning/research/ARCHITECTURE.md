# Architecture Patterns

**Domain:** Kotlin DSL library with Gradle code-generation plugin
**Researched:** 2026-03-15
**Confidence:** MEDIUM — external tools unavailable; based on training knowledge of Gradle plugin architecture, jOOQ internals, and comparable Kotlin codegen libraries (Arrow Meta, KSP, jOOQ's own generator). Core structural patterns are stable and well-established.

---

## Recommended Architecture

Three-module Gradle project. Each module has a single, clear responsibility with no circular dependencies.

```
declarative-jooq/
├── settings.gradle.kts
├── build.gradle.kts             (root, version catalog, shared config)
├── dsl-runtime/                 (MODULE 1: runtime library)
│   ├── build.gradle.kts
│   └── src/main/kotlin/
├── codegen/                     (MODULE 2: code generation engine)
│   ├── build.gradle.kts
│   └── src/main/kotlin/
└── gradle-plugin/               (MODULE 3: Gradle plugin)
    ├── build.gradle.kts
    └── src/main/kotlin/
```

### Why Three Modules, Not Two

Separating `codegen` from `gradle-plugin` is the critical structural decision:

- **`gradle-plugin`** depends on Gradle API, which must not leak into `codegen`
- **`codegen`** can be invoked standalone (CLI, tests, IDEA plugin later) without Gradle
- **`dsl-runtime`** has zero knowledge of codegen — it ships to the user's test classpath
- Test isolation: `codegen` is unit-testable without standing up a Gradle build

---

## Component Boundaries

| Module | Responsibility | Depends On | Consumed By |
|--------|---------------|------------|-------------|
| `dsl-runtime` | Runtime DSL engine — `execute {}` block, FK resolution, topological sort, batch insert, result assembly | jOOQ 3.18+ (compileOnly ok for DSL types, required at runtime) | User's test code |
| `codegen` | Inspects jOOQ-generated classes, builds an internal model (tables, columns, FKs), emits Kotlin source | jOOQ 3.18+, KotlinPoet or string templates | `gradle-plugin` |
| `gradle-plugin` | Wires `codegen` into the Gradle build lifecycle, provides extension DSL for configuration, sets up task inputs/outputs | `codegen`, Gradle API | User's `build.gradle.kts` |

### What Does NOT Communicate With What

- `dsl-runtime` never imports from `codegen` or `gradle-plugin`
- `codegen` never imports from `gradle-plugin`
- `gradle-plugin` may import from `codegen` but calls it as a library (not the other way)

---

## Data Flow

### Build-Time Flow (code generation)

```
User's jOOQ classes (compiled .class files on disk)
    |
    v
Gradle Plugin
  - reads extension config (source dir, output dir, base package)
  - creates GenerateJooqDslTask with classpath inputs
    |
    v
codegen module: ClasspathScanner
  - URLClassLoader over user's jOOQ output directory + jOOQ jars
  - reflectively loads all classes extending TableImpl<R>
    |
    v
codegen module: MetadataExtractor
  - for each TableImpl subclass:
      - calls table.fields() → Field<*>[] for columns
      - calls table.getReferences() → List<ForeignKey<R,O>> for FKs
      - inspects ForeignKey.getKey().getTable() for referenced table
      - inspects ForeignKey.getFields() for FK columns
  - produces: List<TableModel> (internal IR, no jOOQ types)
    |
    v
codegen module: TopologicalAnalyzer
  - builds dependency graph from TableModel FK relationships
  - detects self-referential FKs (category.parent_id → category.id)
  - detects multiple FKs to same target table
    |
    v
codegen module: KotlinEmitter
  - for each TableModel, generates:
      - Builder class (the DSL block)
      - Result class (typed wrapper returned after insert)
      - Extension function hanging off DslContext scope
  - writes .kt files to configured output directory
    |
    v
Generated .kt source files
  - wired by plugin into user's sourceSet (testImplementation)
  - compiled by Kotlin compiler in subsequent build phase
```

### Runtime Flow (test execution)

```
Test calls: execute(dslContext) { org { user { } } }
    |
    v
dsl-runtime: DslScope
  - builder blocks called in declaration order
  - each builder records: table ref, field values, FK context from parent
    |
    v
dsl-runtime: RecordGraph
  - builds directed acyclic graph of record nodes
  - resolves FK values: parent result record → child FK field
  - handles self-refs by deferring self-referential FK to update after insert
    |
    v
dsl-runtime: TopologicalInserter
  - sorts record nodes topologically by table FK dependencies
  - groups records by table (batch per table)
  - executes batch inserts in order
  - refreshes each record after insert (store() then refresh()) to capture DB defaults
    |
    v
dsl-runtime: ResultAssembler
  - wraps each inserted record in its typed Result wrapper
  - assembles DslResult: ordered lists per root table, children nested under parents
    |
    v
DslResult returned to test
  - test accesses: result.organizations[0].users[0].id
```

---

## How codegen Inspects jOOQ Classes

### Approach: Reflection via URLClassLoader (MEDIUM confidence)

jOOQ generated classes carry full FK metadata as static fields at the class level. The recommended approach for a Gradle plugin codegen is **classpath reflection**, not source parsing or jOOQ's generator SPI.

**Why reflection, not source parsing:**
- Parsing Kotlin/Java source is fragile (whitespace, formatting, comments)
- jOOQ already compiled the metadata into the class — use it
- jOOQ generator SPI requires hooking into jOOQ's own codegen lifecycle, which this tool runs after

**The reflection approach:**

```kotlin
// In codegen module — MetadataExtractor
val loader = URLClassLoader(
    classpathEntries.map { it.toURI().toURL() }.toTypedArray(),
    javaClass.classLoader  // parent: has jOOQ itself
)

// Find all TableImpl subclasses
val tableClasses: List<Class<*>> = classpathEntries
    .flatMap { scanClassFiles(it) }
    .mapNotNull { className ->
        runCatching { loader.loadClass(className) }.getOrNull()
    }
    .filter { TableImpl::class.java.isAssignableFrom(it) }
    .filter { !it.isInterface && !Modifier.isAbstract(it.modifiers) }
```

**Extracting metadata from jOOQ table instances:**

jOOQ table classes have a static `$INSTANCE` or singleton field (the `TABLE_NAME` constant in generated code). The table object exposes:

```kotlin
// Get the singleton table instance (generated classes have a companion or static field)
val tableInstance = tableClass.getField(tableName.uppercase()).get(null) as TableImpl<*>

// Columns
val fields: Array<Field<*>> = tableInstance.fields()
// field.name → column name
// field.dataType → jOOQ DataType (maps to Kotlin type)
// field.type → java.lang.Class

// Foreign keys (outbound: this table → other)
val outboundFKs: List<ForeignKey<*, *>> = tableInstance.references
// fk.key.table → the referenced table
// fk.fields → columns in THIS table that are FK columns
// fk.key.fields → columns in the referenced table (usually PK)

// Primary key
val pk: UniqueKey<*>? = tableInstance.primaryKey
// pk.fields → PK columns
```

**Self-referential FK detection:**
```kotlin
val isSelfRef = fk.key.table == tableInstance
```

**Multiple FKs to same target:**
```kotlin
val fksByTarget = tableInstance.references
    .groupBy { it.key.table.name }
// entries with size > 1 are the "multiple FKs to same table" case
```

### Class Discovery Strategy

Two viable approaches:

1. **Directory walk** (simpler, recommended): Walk the user's jOOQ output directory, convert `.class` file paths to class names, attempt to load each. Filter by `TableImpl` supertype.

2. **Package scan**: User provides the package name in the plugin extension; scan only classes in that package. More targeted but requires the user to specify it.

Recommendation: Walk the directory (approach 1) as the default, with optional package filter. The user already tells the plugin where jOOQ output lives.

---

## Patterns to Follow

### Pattern 1: Plugin Extension + Task Separation

The Gradle plugin registers an extension for configuration and a `Task` subclass for execution. Configuration is read lazily (Gradle `Property<T>` / `DirectoryProperty`) so it participates in configuration cache.

```kotlin
// Extension
abstract class DeclarativeJooqExtension {
    abstract val jooqClassesDir: DirectoryProperty
    abstract val outputDir: DirectoryProperty
    abstract val basePackage: Property<String>
}

// Task
abstract class GenerateDeclarativeJooqDsl : DefaultTask() {
    @get:InputDirectory abstract val jooqClassesDir: DirectoryProperty
    @get:OutputDirectory abstract val outputDir: DirectoryProperty
    @get:Input abstract val basePackage: Property<String>

    @TaskAction
    fun generate() {
        // delegates to codegen module — no Gradle API here
        CodeGenerator.run(
            jooqClassesDir.get().asFile,
            outputDir.get().asFile,
            basePackage.get()
        )
    }
}
```

### Pattern 2: Internal Representation (IR) Decouples Emitter from Extractor

The `MetadataExtractor` produces an IR (`TableModel`, `ColumnModel`, `ForeignKeyModel`) that contains no jOOQ types. The `KotlinEmitter` consumes only the IR.

This means:
- Emitter is testable with hand-crafted IR
- Emitter can be swapped (e.g., emit Java instead of Kotlin) without touching extraction
- IR serializable to JSON for debugging/caching

```kotlin
data class TableModel(
    val className: String,       // e.g. "UserRecord"
    val tableName: String,       // e.g. "USER"
    val schemaName: String?,
    val columns: List<ColumnModel>,
    val primaryKey: List<String>,
    val foreignKeys: List<ForeignKeyModel>
)

data class ForeignKeyModel(
    val constraintName: String,
    val localColumns: List<String>,
    val referencedTable: String,
    val referencedColumns: List<String>,
    val isSelfReferential: Boolean
)
```

### Pattern 3: Topological Sort in Both codegen and Runtime

Both modules need topological ordering:

- **codegen**: Determines nesting structure in generated DSL (which tables can be roots vs. must be children)
- **runtime**: Determines actual insert order (records must insert before records that FK-reference them)

Extract the sort algorithm to `dsl-runtime` (since it ships anyway) and have `codegen` either duplicate a simpler version or depend on `dsl-runtime`. Since `codegen` is build-time and `dsl-runtime` is test-runtime, a clean option is to have both contain their own minimal topo sort rather than creating a shared dependency.

### Pattern 4: Generated Code Depends on Runtime, Not codegen

The generated `.kt` files import from `dsl-runtime` only. The user adds `dsl-runtime` to `testImplementation`. `codegen` and `gradle-plugin` are `buildSrc` or plugin dependencies — not on the user's test classpath.

```
User's test classpath:
  - jOOQ (already there for their schema)
  - dsl-runtime (new dependency)
  - generated .kt files (compiled from codegen output)

User's buildscript classpath:
  - gradle-plugin
  - codegen (transitive via gradle-plugin)
```

---

## Anti-Patterns to Avoid

### Anti-Pattern 1: Plugin Module Containing Business Logic

**What goes wrong:** `gradle-plugin` imports Gradle API throughout and its logic is only testable via `GradleRunner` (slow integration tests).

**Why bad:** Every test of FK extraction or code emission requires standing up a Gradle build. Tests take 5-30 seconds each instead of milliseconds.

**Instead:** `gradle-plugin` is a thin wiring layer. All logic in `codegen`. `codegen` tests are pure unit tests with no Gradle dependency.

### Anti-Pattern 2: Loading jOOQ Classes Into the Build Classloader

**What goes wrong:** Loading user's jOOQ classes directly into the Gradle daemon classloader causes classloader leaks, version conflicts, and makes the daemon dirty for subsequent builds.

**Why bad:** The Gradle daemon is long-lived. Leaking user classes causes OOM on repeated builds and obscure `ClassCastException` errors.

**Instead:** Always create a `URLClassLoader` with the Gradle daemon classloader (or `ClassLoader.getSystemClassLoader()`) as parent, load user classes into it, use the extracted metadata (primitive types, strings), then let the URLClassLoader go out of scope.

### Anti-Pattern 3: Parsing jOOQ Source Instead of Compiled Classes

**What goes wrong:** String/regex parsing of jOOQ-generated `.kt` or `.java` source to find table and FK info.

**Why bad:** Brittle against jOOQ version changes, formatting changes, comment changes. The compiled classes already have all this data via reflection — use it.

**Instead:** Require jOOQ codegen to run first (it already does in the user's build), then reflect over compiled classes.

### Anti-Pattern 4: Hardcoding Insert Order in Generated Code

**What goes wrong:** Topological order baked into the generated DSL itself, so changing the schema requires regenerating AND the order is wrong for dynamic record graphs.

**Why bad:** The actual insertion order depends on which records are declared in a given `execute {}` block — it's data-dependent, not schema-dependent. A block with 3 orgs and 5 users needs different ordering than one with just 1 user.

**Instead:** Runtime topological sort in `dsl-runtime` over the actual record graph built during the `execute {}` block. Generated code provides the schema structure; runtime provides the execution order.

### Anti-Pattern 5: Self-Referential FK as Blocking Dependency

**What goes wrong:** Treating `category.parent_id → category.id` as a normal FK causes the topological sort to find a cycle and fail.

**Why bad:** Self-referential FKs are valid and common (tree structures, self-joins).

**Instead:** During runtime graph construction, detect self-referential FKs. Insert all records in the self-referential table first (with `parent_id = null`), then issue UPDATE statements to set the parent pointers. The generated DSL for self-ref tables accepts an optional parent reference.

---

## Build Order (Module Dependencies and Suggested Phase Sequence)

```
Phase 1: dsl-runtime foundation
  - No external module dependencies
  - Core data structures: RecordNode, RecordGraph
  - Topological sort algorithm
  - Can be tested with manually-created jOOQ records

Phase 2: codegen engine
  - Depends on: jOOQ (for reflection), dsl-runtime IR (for understanding output shape)
  - ClasspathScanner, MetadataExtractor, IR models
  - KotlinEmitter (generates code that imports dsl-runtime)
  - Fully unit-testable: create test jOOQ classes in test resources, assert emitted code

Phase 3: gradle-plugin
  - Depends on: codegen, Gradle API
  - Extension, Task, plugin apply() wiring
  - Integration tested via GradleRunner with a test project in src/test/resources
  - Wires generated source into user's testImplementation sourceSet

Phase 4: dsl-runtime completion
  - FK resolution logic (uses codegen output shape knowledge)
  - Batch insert + record refresh
  - Result assembly
  - Tested against a real DB (TestContainers) with generated DSL from Phase 2
```

### Dependency Graph

```
gradle-plugin
    └── codegen
            └── (jOOQ - reflection only, not shipped to users)

dsl-runtime
    └── (jOOQ - compile/runtime, shipped to users)

Generated .kt files (output of codegen)
    └── dsl-runtime
```

---

## Scalability Considerations

| Concern | Small schema (10 tables) | Medium schema (100 tables) | Large schema (500+ tables) |
|---------|--------------------------|----------------------------|----------------------------|
| Codegen performance | Instant | < 1s, negligible | May hit URLClassLoader overhead; consider caching IR to JSON |
| Generated code size | Small, manageable | Large but fine | Very large; consider splitting into multiple files per schema |
| Runtime topo sort | O(V+E), trivially fast | Fast | Fast — graph is bounded by tables declared in block, not total schema size |
| Build incremental | Gradle input tracking handles this | Same | Same — only regenerates if jOOQ output changes |

---

## Sources

- jOOQ `TableImpl` and `ForeignKey` API: training knowledge, jOOQ 3.18+ API — MEDIUM confidence (stable API, no external verification available during research)
- Gradle plugin authoring patterns: training knowledge of Gradle 8.x best practices — MEDIUM confidence
- URLClassLoader isolation pattern: well-established JVM pattern used by build tools universally — HIGH confidence
- Multi-module separation of plugin vs. engine: pattern followed by ktlint-gradle, detekt, and similar Kotlin tooling — MEDIUM confidence (no external verification available)
- Self-referential FK handling via deferred UPDATE: standard pattern in ORM insert-order problems — MEDIUM confidence
