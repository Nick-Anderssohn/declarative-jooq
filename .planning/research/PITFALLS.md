# Domain Pitfalls

**Domain:** Kotlin code generator / jOOQ DSL wrapper / Gradle plugin
**Project:** declarative-jooq
**Researched:** 2026-03-15
**Confidence:** HIGH (KotlinPoet, jOOQ, Gradle plugin APIs are well-documented; pitfalls draw from documented API behavior and known failure modes)

---

## Critical Pitfalls

Mistakes that cause rewrites or major architectural rethink.

---

### Pitfall 1: Classloading Isolation in Gradle Plugin — Scanning User's jOOQ Classes

**What goes wrong:** The Gradle plugin needs to scan user-generated jOOQ record classes to introspect their fields and FK metadata. If you load those classes directly in the plugin's own classloader (the Gradle classloader), you will encounter `ClassNotFoundException` for jOOQ classes, `NoClassDefFoundError` for user dependencies, or silently load the wrong version of a class. The plugin classpath and the user project classpath are separate and do not share classes.

**Why it happens:** Gradle plugin code runs in a classloader that contains only Gradle internals and explicitly declared plugin dependencies — not the user's compile/runtime classpath. jOOQ record classes extend `UpdatableRecordImpl`, which lives in jOOQ jars that are on the user's classpath, not the plugin's.

**Consequences:** Runtime crashes during code generation with cryptic `ClassNotFoundException`. Attempting to fix it by adding jOOQ as a plugin dependency creates version conflicts (plugin jOOQ version vs user jOOQ version). This is a rewrite-level mistake if the initial design assumes class loading works transparently.

**Prevention:** Do not attempt to instantiate or reflectively load user jOOQ record classes in the plugin process. Instead:
- Use a Worker API task or a separate `JavaExec` subprocess that runs on the user's compile classpath.
- Alternatively, use source-level scanning: parse the generated `.java`/`.kt` source files or compiled `.class` bytecode via ASM (no classloading required), extract field/FK metadata from bytecode structure rather than via reflection.
- jOOQ FK metadata is also available from generated table reference classes (e.g., `Tables.USER.getForeignKeys()`) which can be read from bytecode without full classloading.

**Detection:** `ClassNotFoundException: org.jooq.impl.UpdatableRecordImpl` during code generation task. Any attempt to call `.getDeclaredFields()` on loaded classes from user sourceset fails.

**Phase:** Must be resolved before any code generation work begins. Classloading strategy is the architectural cornerstone of the Gradle plugin.

---

### Pitfall 2: Topological Sort Silently Producing Wrong Order for Self-Referential FKs

**What goes wrong:** Standard Kahn's algorithm / DFS topological sort treats a self-referential FK (e.g., `category.parent_id -> category.id`) as a cycle, causing the algorithm to either throw a "cycle detected" error or — worse — silently drop the node from the sorted output.

**Why it happens:** A self-edge (node pointing to itself) is mathematically a cycle in a directed graph. Most off-the-shelf implementations treat any cycle as a fatal error and halt.

**Consequences:** `category` table either throws an error at sort time, or (in naive implementations that skip cycle nodes) is never inserted at all. Self-referential records are a stated requirement of the project, so this is a blocker if not handled.

**Prevention:** Before running topological sort, strip all self-edges from the graph. Self-referential FKs are handled separately: insert the self-referential records first with `parent_id = null`, then run a second-pass update to set `parent_id` after all records are inserted. The topological sort operates on the FK graph with self-edges removed. Document this two-phase strategy explicitly.

**Detection:** Running the topological sort on a schema containing any table with a self-referential FK causes either an exception or missing tables in the sorted output.

**Phase:** Must be addressed in the topological sort / batch insert phase. Design the two-pass strategy before implementing insertion logic.

---

### Pitfall 3: Generated DSL Code Has Import Conflicts Due to Same-Named Record Classes

**What goes wrong:** KotlinPoet's `ClassName` and `TypeName` system handles fully qualified names correctly, but when you generate `fun user(block: UserBuilder.() -> Unit)` and a user has both `com.example.generated.tables.records.UserRecord` and a hand-written `com.example.model.User`, KotlinPoet may produce ambiguous or incorrect import statements.

**Why it happens:** KotlinPoet's import deduplication is based on simple name collisions. If two types share the same simple name, it falls back to fully qualified usage — but only if you correctly use `ClassName("package", "SimpleName")` rather than string interpolation in type specs. If you build type names via string concatenation, you bypass KotlinPoet's import management entirely, producing code that won't compile.

**Consequences:** Generated code has import conflicts or missing imports. The generated DSL fails to compile in the user's project. This is an insidious failure because the generator itself succeeds — only the downstream compilation fails.

**Prevention:**
- Always use `ClassName(packageName, simpleName)` and `TypeName` API — never string-interpolate into `%L` format specifiers for type names. Use `%T` for all type references.
- Use `%T` for all types in KotlinPoet format strings. `%L` is for literal string output, `%T` is for type references with proper import management.
- Write an integration test that compiles the generated output, not just that it produces the expected string.

**Detection:** Generated `.kt` files have duplicate imports or unresolved references when compiled in the user's test project.

**Phase:** Early in code generator implementation. Integration test (generate + compile) is the only reliable check.

---

### Pitfall 4: jOOQ Record Refresh After Batch Insert Returns Wrong Records

**What goes wrong:** The project spec requires records be "refreshed after insert to capture DB-generated defaults." With batch inserts, `DSLContext.batchInsert(records)` does not refresh records in-place — it returns affected row counts, not the inserted rows with their generated values.

**Why it happens:** JDBC batch execution (`executeBatch()`) does not support `RETURNING` clauses in most databases. jOOQ's `batchInsert` follows JDBC semantics. The generated IDs/defaults are not automatically populated back into the record objects after a batch call.

**Consequences:** Result objects contain null or zero for auto-generated primary keys. FK assignment for child records (which need parent PK values) fails silently or with NPE. The entire insert chain produces broken results.

**Prevention:** After each table's batch insert, run a separate `store()` or use `DSLContext.insertInto(...).values(...).returning(...).fetchInto(...)` pattern for databases that support `RETURNING` (PostgreSQL). For databases without `RETURNING` (MySQL/H2 in non-Postgres mode), use individual `record.store()` calls (which do refresh) or fetch inserted records by a known unique key immediately after batch. Document which strategy is used and which databases it supports.

**Detection:** After `batchInsert`, check `record.getValue(primaryKeyField)` — if null, the record was not refreshed.

**Phase:** Core runtime insert logic phase. The batch + refresh strategy must be designed together, not retrofitted.

---

## Moderate Pitfalls

---

### Pitfall 5: Gradle Plugin Extension Not Lazy — Breaks Configuration-Time Evaluation

**What goes wrong:** Plugin extensions that read configuration values eagerly (at plugin application time) rather than lazily (at task execution time) will read values before the user's `build.gradle` has finished configuring them — producing defaults or empty strings instead of user-provided values.

**Why it happens:** `project.extensions.create(MyExtension::class)` returns the extension immediately, but the user's build script configures it in an `afterEvaluate` block or simply after the `apply plugin:` line. If the plugin reads `extension.outputDir` during `apply()` to configure a task, it reads the unset default.

**Consequences:** Code generation task uses wrong output directory, wrong source set, or skips generation entirely. Fails silently with empty generated output.

**Prevention:** Use Gradle's `Property<T>` and `DirectoryProperty` types in the extension (not raw `String` or `File`). Wire task inputs/outputs to extension properties using `.set(provider)` — tasks read values lazily at execution time, not at configuration time. Never read `extension.outputDir.get()` in the `apply()` method body.

**Detection:** Code generation produces output in the wrong directory. Adding a `println(extension.outputDir)` in `apply()` prints an empty/default value even when the user has configured it.

**Phase:** Gradle plugin scaffolding phase (first milestone of plugin work).

---

### Pitfall 6: KotlinPoet Nullable Types vs Platform Types — Generated Code Doesn't Reflect DB Nullability

**What goes wrong:** jOOQ record fields have nullability information (columns defined as `NOT NULL` vs nullable). If the code generator ignores this and generates all builder parameters as non-nullable Kotlin types, users can't omit optional fields. If it generates all as nullable, users must null-check everything. The wrong choice produces an unusable DSL.

**Why it happens:** jOOQ's `Field<T>` uses `T` where `T` can be `String` (non-null) or `String?` (nullable) — but when read through reflection or ASM bytecode, the nullability annotation (JSR-305 `@NotNull`/`@Nullable` or jOOQ's own annotations) may not be obvious.

**Consequences:** Builder methods with wrong nullability force users to either pass `null!!` or suppress null-safety warnings. Defeats the purpose of a typed DSL.

**Prevention:** jOOQ generates columns with `NOT NULL` as non-nullable Kotlin types (when using Kotlin codegen mode) and nullable columns as `T?`. Use jOOQ's `Column.nullable()` metadata (accessible via `TableField.dataType.nullable()`) to determine nullability at codegen time, rather than inferring from the Kotlin type.

**Detection:** Generated DSL builder for a nullable column requires a non-null value, or vice versa.

**Phase:** Code generator implementation phase, specifically the field type mapping step.

---

### Pitfall 7: Multiple FKs From One Table to the Same Target — Ambiguous Auto-Resolution

**What goes wrong:** When a table has two FKs pointing to the same target table (e.g., `post.created_by_user_id` and `post.updated_by_user_id` both FK to `user.id`), the DSL's automatic FK resolution cannot determine which FK to assign from a parent `user` context block.

**Why it happens:** The auto-resolution design assumes each (child table, parent table) pair has at most one FK. With multiple FKs to the same target, the resolution is ambiguous — two fields need to be populated from potentially different parent records.

**Consequences:** Silent wrong assignment (first FK wins), runtime exception, or the generator crashes. This is a stated requirement of the project, so it must be handled correctly.

**Prevention:** When the generator detects multiple FKs from table A to table B, generate explicit named setter methods instead of (or in addition to) the implicit parent-context resolution. For example, `createdBy(user)` and `updatedBy(user)` rather than a single implicit `user` context. The DSL builder should require the user to disambiguate. Document this behavior in the DSL API.

**Detection:** Schema contains `created_by`/`updated_by` pattern. Generated code compiles but assigns the same user to both FK fields, or the generator throws an ambiguous-FK error.

**Phase:** Code generator design phase. The FK resolution strategy must account for this before the DSL API is finalized.

---

### Pitfall 8: Gradle Plugin Breaks Configuration Cache

**What goes wrong:** Gradle's configuration cache (enabled by default in Gradle 9+, opt-in in 8.x) serializes the task graph to disk. Any plugin that captures `Project`, `Task`, or service references in closures that are serialized as task state will cause configuration cache misses or errors.

**Why it happens:** Plugin authors pass `project` references into `doFirst`/`doLast` action lambdas, or capture `Task` references in worker actions. These are not serializable and cause `ConfigurationCacheException`.

**Consequences:** Build fails with `ConfigurationCacheException` when configuration cache is enabled. Users on Gradle 8+ with `--configuration-cache` or Gradle 9+ (where it is the default) can't use the plugin.

**Prevention:**
- Use `@InputFiles`, `@OutputDirectory`, `@Input` annotations on `@TaskAction` method parameters rather than accessing `project` in task actions.
- Inject `ObjectFactory`, `WorkerExecutor`, and other services via constructor injection rather than `project.objects`.
- Test the plugin with `--configuration-cache` flag from day one.
- Do not capture `project` in any action lambda.

**Detection:** Run `./gradlew generateDsl --configuration-cache` — any `ConfigurationCacheException` reveals violations. The error message names the specific non-serializable reference.

**Phase:** Gradle plugin implementation phase. Add configuration cache test early.

---

### Pitfall 9: Generated Code Output Directory Not Added to Source Set

**What goes wrong:** The Gradle plugin generates `.kt` files but doesn't register the output directory as a source root in the consuming project's Kotlin source set. The generated files exist on disk but are invisible to the Kotlin compiler.

**Why it happens:** Gradle source sets must be explicitly configured to include additional source roots. Generating files into a directory under `build/` is not sufficient — the directory must be added to `sourceSets.main.kotlin.srcDirs` (or the equivalent test source set).

**Consequences:** Generated DSL classes are not compiled, causing `Unresolved reference` errors when users try to use them.

**Prevention:** In the plugin's `apply()` method, after registering the generation task, call:
```kotlin
project.extensions.getByType<KotlinProjectExtension>()
    .sourceSets["test"]
    .kotlin
    .srcDir(generationTask.flatMap { it.outputDir })
```
Use a task output `Provider` (lazy) to wire the source dir, so it's only added after the task runs. Also add a task dependency so the generation task runs before `compileTestKotlin`.

**Detection:** Generated files appear in `build/generated/` but `./gradlew compileTestKotlin` reports `Unresolved reference: UserDsl`.

**Phase:** Gradle plugin scaffolding phase.

---

## Minor Pitfalls

---

### Pitfall 10: KotlinPoet `FileSpec` Overwrites on Re-generation — No Incremental Safety

**What goes wrong:** Re-running code generation overwrites all previously generated files. If the user has manually edited a generated file (common during debugging), those edits are silently lost.

**Prevention:** Write a file header comment in every generated file: `// DO NOT EDIT — generated by declarative-jooq codegen`. Use a dedicated output directory (`build/generated/declarative-jooq/`) that is clearly separated from hand-written source. Document that edits to generated files are lost.

**Detection:** User reports that manual changes to generated DSL are overwritten on build.

**Phase:** Code generator implementation.

---

### Pitfall 11: jOOQ `TableRecord` vs `UpdatableRecord` — Wrong Base Type Assumption

**What goes wrong:** Code generator assumes all jOOQ-generated record classes extend `UpdatableRecordImpl` (which supports `store()` and `refresh()`). Tables without a primary key generate records extending `TableRecordImpl`, which does not support `refresh()` or `store()`.

**Why it happens:** jOOQ generates `UpdatableRecordImpl` subclasses only for tables with a primary key. Tables without PKs (junction/association tables) get `TableRecordImpl`.

**Consequences:** Calling `record.refresh()` after insert on a no-PK record throws `DataAccessException: Cannot refresh a record that has no identity`.

**Prevention:** During code generation, check whether the jOOQ table has a primary key (via `Table.getPrimaryKey() != null`). For tables without PKs, skip the refresh step after insert. Document that no-PK tables do not support result object property access for DB-generated defaults.

**Detection:** Schema includes a junction table (e.g., `user_roles`). Insert + refresh throws `DataAccessException`.

**Phase:** Runtime insert logic phase.

---

### Pitfall 12: Declaration Order Not Preserved in Result Object

**What goes wrong:** The project requires result objects to preserve the declaration order of records in the DSL block. If the internal collection used during insert is a `Map` or `Set` (unordered), the result objects will not match declaration order.

**Prevention:** Use `LinkedHashMap` for all internal record tracking. Use `MutableList` for ordered record collections. Never use `HashMap` or `HashSet` anywhere in the insert pipeline.

**Detection:** Two users declared in a block come back in arbitrary order in the result object.

**Phase:** DSL runtime implementation phase.

---

### Pitfall 13: KotlinPoet `%N` vs `%L` vs `%T` Format Confusion

**What goes wrong:** Using the wrong format specifier in KotlinPoet's `CodeBlock.of(...)` produces code that doesn't compile:
- `%L` emits a literal string — fine for values, wrong for names or types
- `%N` emits a name — for function/property names, not types
- `%T` emits a type with proper import management

Using `%L` for a class name bypasses import management and produces unqualified names that don't resolve.

**Prevention:** Strict rule: all type references use `%T` with a `ClassName` or `TypeName`. All function/property names use `%N`. String literals use `%L` or `%S`. Write a codegen unit test that round-trips a generated file through `kotlinc` compilation.

**Detection:** Generated file has a bare class name with no import, causing `Unresolved reference` at compile time.

**Phase:** Code generator implementation, from the first generated file.

---

## Phase-Specific Warnings

| Phase Topic | Likely Pitfall | Mitigation |
|-------------|---------------|------------|
| Gradle plugin scaffolding | Extension reads values eagerly (Pitfall 5) | Use `Property<T>` and lazy providers from the start |
| Gradle plugin scaffolding | Generated sources not added to source set (Pitfall 9) | Wire source dir to `test` kotlin srcDirs in `apply()` |
| Gradle plugin scaffolding | Configuration cache breakage (Pitfall 8) | Test with `--configuration-cache` from day one |
| Code generator — class scanning | Classloading isolation (Pitfall 1) | Design worker/subprocess boundary before writing any scanner code |
| Code generator — type mapping | Wrong nullability in generated DSL (Pitfall 6) | Use `TableField.dataType.nullable()` for all field nullability |
| Code generator — FK analysis | Multiple FKs to same target (Pitfall 7) | Detect and generate named setters before finalizing DSL API |
| Code generator — type output | `%L` instead of `%T` in KotlinPoet (Pitfall 13) | Enforce `%T` rule, add compile-test to CI |
| Runtime insert — batch strategy | Batch insert doesn't refresh records (Pitfall 4) | Design refresh strategy before implementing insert; pick `RETURNING` vs individual store |
| Runtime insert — ordering | Self-referential FK cycles topological sort (Pitfall 2) | Strip self-edges before sort; implement two-pass insert |
| Runtime insert — result assembly | Declaration order not preserved (Pitfall 12) | Audit all collections — replace `HashMap` with `LinkedHashMap` |
| Runtime insert — PK check | No-PK tables can't refresh (Pitfall 11) | Check `getPrimaryKey() != null` before refresh call |

---

## Sources

- jOOQ 3.18 API documentation (training knowledge): `UpdatableRecord.refresh()`, `DSLContext.batchInsert()`, `Table.getPrimaryKey()`, `TableField.dataType.nullable()` — HIGH confidence (stable jOOQ API, present since jOOQ 3.x)
- KotlinPoet documentation: `CodeBlock` format specifiers (`%T`, `%N`, `%L`, `%S`), `ClassName`, `FileSpec` — HIGH confidence (stable Square library, well-documented)
- Gradle Plugin Development Guide: `Property<T>`, `DirectoryProperty`, lazy configuration, `@InputFiles`/`@OutputDirectory` task annotations, configuration cache constraints — HIGH confidence (official Gradle docs, stable since Gradle 7+)
- Kahn's algorithm / DFS topological sort: self-edge behavior in directed graphs — HIGH confidence (computer science fundamentals)
- JDBC batch execution / `RETURNING` clause support: PostgreSQL supports `RETURNING`, MySQL/H2 do not natively — HIGH confidence (JDBC specification + database-specific behavior)

**Note:** No external URLs were accessible during this research session (WebSearch, WebFetch, and Context7 were unavailable). All findings are based on training knowledge of documented APIs and known behavioral patterns. Confidence is HIGH for the core API behaviors cited, as these are stable, long-standing characteristics of the respective libraries and tools. Recommend validating Pitfall 8 (configuration cache) against the specific Gradle version in use, as Gradle's configuration cache has evolved across 7.x → 8.x → 9.x.
