# Phase 2: Code Generation Engine - Research

**Researched:** 2026-03-15
**Domain:** KotlinPoet code generation, classpath scanning with ClassGraph, kotlin-compile-testing validation
**Confidence:** HIGH

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- Scan a user-specified directory of compiled `.class` files using a `URLClassLoader`
- Find both `UpdatableRecordImpl` subclasses (records) and `TableImpl` subclasses (table metadata) by walking the package tree
- Table singletons discovered via their static `INSTANCE` field or Kotlin object instance — not derived by naming convention
- FK metadata extracted from `Table.getReferences()` (canonical source: primary keys, foreign keys, identity columns, all fields)
- Optional package filter to restrict scanning scope — scans entire directory by default
- Must use kotlin-compile-testing for compile-and-run validation per TEST-01 — not string matching or snapshot tests

### Claude's Discretion
- IR model structure and field types
- KotlinPoet code generation patterns
- How to handle edge cases in jOOQ class loading (different jOOQ versions, custom naming strategies)
- Internal error handling and reporting during scan/generation

### Deferred Ideas (OUT OF SCOPE)
None — discussion stayed within phase scope
</user_constraints>

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| CODEGEN-02 | Recursive scan of a configured directory for jOOQ-generated record classes (classes extending UpdatableRecordImpl) | ClassGraph scans `.class` files without loading them; URLClassLoader used to instantiate after discovery |
| CODEGEN-03 | Generate a DSL builder class per table with typed property setters matching jOOQ record fields | KotlinPoet TypeSpec.classBuilder with superclass + PropertySpec.builder(..).mutable() + FunSpec override |
| CODEGEN-04 | Generate a result class per table with typed property accessors and the underlying jOOQ record | KotlinPoet TypeSpec.classBuilder with constructor parameter holding UpdatableRecord and val accessors |
| CODEGEN-05 | Generate a top-level DslResult class containing ordered lists of result objects per root table | KotlinPoet TypeSpec.classBuilder with LinkedHashMap property + typed accessor functions per root table |
| CODEGEN-06 | Generate nested builder functions for single-column FK relationships (child builders inside parent builders) | ForeignKey.getFields() identifies FK columns; ForeignKey.getKey().table identifies parent; child builder function generated inside parent via TypeSpec.addFunction |
| TEST-01 | Codegen tests use compile-and-run validation (not string matching) via kotlin-compile-testing | KotlinCompilation with inheritClassPath=true compiles generated source; result.classLoader executes it against dsl-runtime |
</phase_requirements>

---

## Summary

Phase 2 builds a standalone code generator in the `codegen` module. Given a directory of compiled jOOQ `.class` files, it must: (1) scan and identify table/record classes via ClassGraph + URLClassLoader, (2) extract metadata into an internal IR, (3) emit Kotlin source using KotlinPoet, and (4) validate the output compiles and executes correctly via kotlin-compile-testing.

The golden output is `TestBuilders.kt` in `dsl-runtime/src/test`. The generator must produce code that is structurally equivalent: root builders extend `RecordBuilder<R>`, override `buildRecord()` using `record.set(field, value)`, declare child builder functions that close over the FK field reference, implement `buildWithChildren()`, and expose a top-level `DslScope.tableName()` extension function. Result classes wrap the inserted `UpdatableRecord<R>` and expose typed property accessors.

The three main phases of the engine — Scan, Extract IR, Emit — are cleanly separable. ClassGraph handles bytecode scanning without loading classes (fast, safe). URLClassLoader then loads only identified classes to interrogate live metadata via jOOQ's own API (`table.fields`, `table.getReferences()`). KotlinPoet emits correct, idiomatic Kotlin. kotlin-compile-testing closes the loop by compiling and executing the generated code in-process during tests.

**Primary recommendation:** Use ClassGraph for classpath scanning (no class loading during discovery), URLClassLoader for live jOOQ metadata interrogation, KotlinPoet 2.2.0 for emission, kotlin-compile-testing 1.6.0 for compile-and-run test validation.

---

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| KotlinPoet | 2.2.0 | Generate `.kt` source files programmatically | Square's official Kotlin code-gen library; stable API since 1.0; widely used in KSP/KAPT ecosystems |
| ClassGraph | 4.8.181+ | Scan `.class` files to find subclasses without loading | Reads bytecode directly; handles URLClassLoader; parallelized; does not trigger class initialization |
| kotlin-compile-testing | 1.6.0 | Compile and execute generated Kotlin code in tests | Provides in-process KotlinCompilation; result.classLoader for runtime execution; standard for testing codegen |
| jOOQ | 3.19.16 | Runtime metadata API (`Table.fields`, `Table.getReferences()`) | Already in project; `compileOnly` in codegen module |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| JUnit Jupiter | 5.11.4 | Test runner | Already used in dsl-runtime; consistent across modules |
| H2 | 2.3.232 | In-memory DB for kotlin-compile-testing integration tests | Needed so compiled DSL code can execute against real DB in TEST-01 |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| ClassGraph | Manual `URLClassLoader` + `File.walk` + `ClassLoader.loadClass` | ClassGraph avoids class loading during discovery — safer for untrusted code; ClassGraph is standard for this problem |
| ClassGraph | Reflections library | Reflections is older, less maintained; ClassGraph is faster and more accurate |
| kotlin-compile-testing (tschuchortdev) | ZacSweers fork | ZacSweers fork is more actively maintained for newer Kotlin; use tschuchortdev 1.6.0 first, switch if Kotlin 2.x compatibility issues arise |

**Installation:**
```bash
# codegen/build.gradle.kts additions:
implementation("com.squareup:kotlinpoet:2.2.0")
implementation("io.github.classgraph:classgraph:4.8.181")
compileOnly("org.jooq:jooq:3.19.16")

testImplementation("com.github.tschuchortdev:kotlin-compile-testing:1.6.0")
testImplementation("org.jooq:jooq:3.19.16")
testImplementation("com.h2database:h2:2.3.232")
testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
testImplementation(project(":dsl-runtime"))
```

---

## Architecture Patterns

### Recommended Project Structure
```
codegen/src/main/kotlin/com/example/declarativejooq/codegen/
├── scanner/
│   ├── ClasspathScanner.kt       # ClassGraph scan → raw class list
│   └── MetadataExtractor.kt      # URLClassLoader load → jOOQ API → IR
├── ir/
│   ├── TableIR.kt                # Intermediate representation: table metadata
│   ├── ColumnIR.kt               # Column name, Kotlin type, jOOQ field ref
│   └── ForeignKeyIR.kt           # FK: child columns → parent table
├── emitter/
│   ├── BuilderEmitter.kt         # Emits XxxBuilder.kt per table
│   ├── ResultEmitter.kt          # Emits XxxResult.kt per table
│   ├── DslResultEmitter.kt       # Emits top-level DslResult class (CODEGEN-05)
│   └── DslScopeEmitter.kt        # Emits DslScope extension functions
└── CodeGenerator.kt              # Orchestrates: scan → extract → emit → write

codegen/src/test/kotlin/com/example/declarativejooq/codegen/
├── CodeGeneratorTest.kt          # compile-and-run tests via kotlin-compile-testing
└── ScannerTest.kt                # Unit tests for ClasspathScanner + MetadataExtractor
```

### Pattern 1: Classpath Scanning with ClassGraph (without loading)

**What:** Use ClassGraph to find `UpdatableRecordImpl` and `TableImpl` subclasses by reading bytecode — no class initialization.
**When to use:** Discovery phase, before URLClassLoader is created.

```kotlin
// Source: ClassGraph API — HIGH confidence
fun findTableClasses(classDir: File, packageFilter: String?): List<String> {
    val scan = ClassGraph()
        .overrideClasspath(classDir.absolutePath)
        .enableClassInfo()
        .apply { if (packageFilter != null) acceptPackages(packageFilter) }
        .scan()

    return scan.getSubclasses("org.jooq.impl.TableImpl")
        .filterNot { it.isAbstract }
        .map { it.name }
}
```

### Pattern 2: URLClassLoader for Live jOOQ Metadata

**What:** After discovering class names via ClassGraph, load them with a URLClassLoader and use jOOQ's own API to extract column and FK metadata.
**When to use:** Extraction phase, after class discovery.

```kotlin
// Kotlin companion objects compile to a nested Companion class + static field.
// The table singleton is accessible via: MyTable.Companion or MyTable.TABLE_NAME field.
// Access via reflection: klass.getDeclaredField("INSTANCE") is standard for Kotlin objects,
// but for jOOQ-style classes with companion objects, look for the static field whose
// type is the table class itself.
fun loadTableInstance(klass: Class<*>): TableImpl<*>? {
    // Try Kotlin object INSTANCE first
    return try {
        val instanceField = klass.getDeclaredField("INSTANCE")
        instanceField.get(null) as? TableImpl<*>
    } catch (e: NoSuchFieldException) {
        // Fall back: find first static field whose type is the table class
        klass.declaredFields
            .firstOrNull { java.lang.reflect.Modifier.isStatic(it.modifiers) && it.type == klass }
            ?.apply { isAccessible = true }
            ?.get(null) as? TableImpl<*>
    }
}
```

**Critical note (from STATE.md research flag):** jOOQ Kotlin-generated table classes use a companion object with a static field named after the uppercased table name (e.g., `ORGANIZATION`, `APP_USER`). The companion object itself is accessible as `Companion`, but the table instance is in the static field. In `TestSchema.kt`, the pattern is:
```kotlin
companion object {
    val ORGANIZATION = OrganizationTable()  // generates static field `ORGANIZATION`
}
```
So: scan for static fields whose type is the declaring class — this is more robust than assuming `INSTANCE`.

### Pattern 3: Extracting FK Metadata from jOOQ

**What:** Use `table.getReferences()` to discover FKs.
**When to use:** IR extraction for CODEGEN-06.

```kotlin
// Source: jOOQ ForeignKey API — HIGH confidence (verified in jOOQ javadoc)
fun extractFKs(table: TableImpl<*>): List<ForeignKeyIR> {
    return table.getReferences().map { fk ->
        ForeignKeyIR(
            // Child table columns involved in the FK
            childFields = fk.getFields(),           // List<TableField<CHILD, ?>>
            // Parent table (the table this FK points to)
            parentTable = fk.key.table,             // Table<PARENT>
            // Parent table columns being referenced
            parentKeyFields = fk.getKeyFields()     // List<TableField<PARENT, ?>>
        )
    }
}
```

For CODEGEN-06 (single-column FK), only process FKs where `fk.getFields().size == 1`.

### Pattern 4: KotlinPoet Builder Class Emission

**What:** Generate a builder class that extends `RecordBuilder<R>` with typed properties and child builder functions.
**When to use:** Emitting `XxxBuilder.kt`.

```kotlin
// Source: KotlinPoet official docs + TestBuilders.kt golden pattern
// Generates equivalent of OrganizationBuilder in TestBuilders.kt
fun emitBuilderClass(tableIR: TableIR): TypeSpec {
    val recordType = ClassName("", tableIR.recordClassName)
    val builderType = ClassName("", tableIR.builderClassName)
    val recordBuilderType = ClassName("com.example.declarativejooq", "RecordBuilder")
        .parameterizedBy(recordType)
    val recordGraphType = ClassName("com.example.declarativejooq", "RecordGraph")

    return TypeSpec.classBuilder(tableIR.builderClassName)
        .primaryConstructor(
            FunSpec.constructorBuilder()
                .addParameter("graph", recordGraphType)
                .build()
        )
        .superclass(recordBuilderType)
        .addSuperclassConstructorParameter(
            "%T.%L", tableIR.tableObjectRef, tableIR.tableConstantName
        )
        .addSuperclassConstructorParameter("null")   // parentNode
        .addSuperclassConstructorParameter("null")   // parentFkField
        .addSuperclassConstructorParameter("graph")
        // Add mutable var properties for each non-identity column
        .addProperties(tableIR.nonIdentityColumns.map { col ->
            PropertySpec.builder(col.propertyName, col.kotlinType.copy(nullable = true))
                .mutable()
                .initializer("null")
                .build()
        })
        // Override buildRecord()
        .addFunction(buildRecordFun(tableIR))
        // Add child builder functions for each FK pointing to this table
        .addFunctions(tableIR.inboundFKs.map { fk -> childBuilderFun(fk) })
        // buildWithChildren()
        .addFunction(buildWithChildrenFun())
        .build()
}
```

### Pattern 5: KotlinPoet Extension Function Emission (DslScope entry point)

**What:** Generate a top-level extension function on `DslScope` for each root table.
**When to use:** Emitting `DslScope.tableName()` extension per root table.

```kotlin
// Source: KotlinPoet functions docs + TestBuilders.kt pattern
// Generates: fun DslScope.organization(block: OrganizationBuilder.() -> Unit)
fun emitRootExtensionFun(tableIR: TableIR): FunSpec {
    val dslScopeType = ClassName("com.example.declarativejooq", "DslScope")
    val builderType = ClassName("", tableIR.builderClassName)
    val lambdaType = LambdaTypeName.get(receiver = builderType, returnType = UNIT)

    return FunSpec.builder(tableIR.dslFunctionName)
        .receiver(dslScopeType)
        .addParameter("block", lambdaType)
        .addStatement("val builder = %T(recordGraph)", builderType)
        .addStatement("builder.block()")
        .addStatement("val node = builder.buildWithChildren()")
        .addStatement("recordGraph.addRootNode(node)")
        .build()
}
```

### Pattern 6: kotlin-compile-testing Compile-and-Run Validation

**What:** Compile generated Kotlin source using KotlinCompilation and execute the generated DSL against a real in-memory DB.
**When to use:** TEST-01 — compile-and-run validation.

```kotlin
// Source: tschuchortdev/kotlin-compile-testing README — HIGH confidence
@Test
fun generatedBuilderCompilesAndExecutes() {
    // 1. Generate the source using the code generator
    val generatedSource = generateBuilderSource(/* using TestSchema classes as input */)

    // 2. Compile — inheritClassPath=true makes dsl-runtime classes available
    val result = KotlinCompilation().apply {
        sources = listOf(SourceFile.kotlin("GeneratedBuilders.kt", generatedSource))
        inheritClassPath = true   // makes dsl-runtime + jOOQ + H2 available
        messageOutputStream = System.out
    }.compile()

    assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)

    // 3. Load and execute the generated DSL
    val dslClass = result.classLoader.loadClass("com.example.generated.GeneratedBuildersKt")
    // Use reflection to invoke the generated execute block or DslScope extension
}
```

**Key insight:** `inheritClassPath = true` makes all test-classpath JARs available to the compiled code. This includes `dsl-runtime`, jOOQ, and H2 — so the generated code can actually execute against an in-memory DB without any additional setup.

### Anti-Patterns to Avoid

- **Loading all classes during scan:** ClassGraph reads bytecode; don't load classes until after scan narrows the candidate list. Loading all classes in a large directory causes unnecessary initialization side effects.
- **Name-convention-based table discovery:** The CONTEXT.md explicitly locks against this. Use jOOQ's `table.getRecordType()` and the static instance field, not string matching on class names.
- **Emitting `record.set(field, value)` with string field names:** Always reference the field via the table singleton constant (e.g., `AppUserTable.APP_USER.NAME`), not by string. This is how the golden `TestBuilders.kt` works and it preserves type safety.
- **Child builders with `parentNode: RecordNode?`:** Child builders always have a non-null parent node. The nullability in `RecordBuilder` is for the root case. Generated child builder constructors should take `RecordNode` (non-null).

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Find classes extending a type on disk | File.walk + loadClass + isAssignableFrom | ClassGraph | Custom scanning loads all classes, triggering init; ClassGraph reads bytecode safely |
| Generate formatted Kotlin source | String concatenation / templates | KotlinPoet | Import management, proper escaping, correct formatting are all handled; templates cause subtle syntax errors |
| Compile Kotlin source in tests | ProcessBuilder kotlinc subprocess | kotlin-compile-testing | In-process compilation is fast, incremental, and result.classLoader enables direct execution |
| Kotlin type name mapping from jOOQ DataType | Custom DataType→KClass map | jOOQ's `DataType.getType()` | Returns the Java class; use `asTypeName()` or map to KotlinPoet `TypeName` via `kotlin()` extension |

**Key insight:** KotlinPoet's import management is the primary reason to use it over string templates. Top-level imports are deduped, short names are used where unambiguous, and star imports can be configured — all essential for generating readable, compilable Kotlin.

---

## Common Pitfalls

### Pitfall 1: Kotlin Companion Object Static Field Naming

**What goes wrong:** Assuming the table singleton is always in a field named `INSTANCE` fails for jOOQ Kotlin-generated code, which puts the singleton in a field named after the table constant (e.g., `ORGANIZATION`, `APP_USER`).
**Why it happens:** Kotlin `object` declarations use `INSTANCE`; Kotlin companion objects create static fields named after the declared property.
**How to avoid:** Scan all static fields on the table class; find the one whose type is the declaring class. The `TestSchema.kt` example shows `companion object { val ORGANIZATION = OrganizationTable() }` — this generates a static field `ORGANIZATION`, not `INSTANCE`.
**Warning signs:** `NoSuchFieldException: INSTANCE` when loading a jOOQ-style table.

### Pitfall 2: ClassLoader Isolation in Tests

**What goes wrong:** The codegen module's test ClassLoader has `dsl-runtime` on its classpath. When a URLClassLoader is created pointing to compiled test schema classes, the URLClassLoader won't be able to resolve `UpdatableRecordImpl` from jOOQ unless the parent ClassLoader is configured correctly.
**Why it happens:** URLClassLoader by default uses the bootstrap ClassLoader as parent. jOOQ classes need to be visible.
**How to avoid:** Construct URLClassLoader with the test ClassLoader as parent: `URLClassLoader(arrayOf(classDir.toURI().toURL()), Thread.currentThread().contextClassLoader)`.

### Pitfall 3: Identity Columns in buildRecord()

**What goes wrong:** Calling `record.set(identityField, null)` or including identity fields in the builder's property setters generates code that either fails at runtime or corrupts the generated PK.
**Why it happens:** Identity columns (auto-increment) must not be set; jOOQ detects them via `table.identity`. Setting them to null explicitly may override the DB default.
**How to avoid:** Filter out identity fields in IR extraction: `table.fields.filter { it != table.identity?.field }`. Only non-identity, non-PK fields become builder properties.

### Pitfall 4: kotlin-compile-testing with Kotlin 2.x

**What goes wrong:** `kotlin-compile-testing` 1.6.0 embeds Kotlin 1.9.24 internally. When the project uses Kotlin 2.1.x (as this project does), there can be version mismatch warnings or compilation failures for code using 2.x-only features.
**Why it happens:** kotlin-compile-testing 1.6.0 uses an older embedded compiler.
**How to avoid:** The generated code should use only Kotlin 1.x-compatible patterns — no context parameters, no 2.x-only syntax. The `@DeclarativeJooqDsl` annotation and `var` properties are safe. If mismatches arise, consider the ZacSweers fork which targets newer Kotlin.
**Warning signs:** `warning: version mismatch` or `unsupported feature` during KotlinCompilation.

### Pitfall 5: buildWithChildren() Only on Root Builders

**What goes wrong:** Generating `buildWithChildren()` on child builders causes them to collect and deferred-execute child blocks, but they are already registered via `build()` in the parent's deferred block. Double registration corrupts the graph.
**Why it happens:** The golden `TestBuilders.kt` shows `buildWithChildren()` only on `OrganizationBuilder` (root); `AppUserBuilder` calls `build()` only.
**How to avoid:** Only generate `buildWithChildren()` and child builder functions on tables that are FK parents. Tables that are only FK children call `build()` directly.

### Pitfall 6: DslResult Generated Class vs Runtime DslResult

**What goes wrong:** CODEGEN-05 requires generating a typed `DslResult` class. The runtime already has `DslResult` in `com.example.declarativejooq`. Name clash if the generated class has the same name in the same package.
**Why it happens:** Generated code imports `com.example.declarativejooq.*` from dsl-runtime.
**How to avoid:** Generate the typed result class into the user's output package (e.g., `com.example.generated`). Name it something distinct or make it a wrapper that takes the runtime `DslResult`. Alternative: generate it as a data class alongside the builders.

---

## Code Examples

### IR Data Classes (recommended design)

```kotlin
// IR model — internal to codegen, Claude's Discretion
data class TableIR(
    val tableName: String,               // "organization"
    val tableClassName: String,          // "OrganizationTable"
    val tableConstantName: String,       // "ORGANIZATION" (static field name)
    val recordClassName: String,         // "OrganizationRecord"
    val builderClassName: String,        // "OrganizationBuilder"
    val resultClassName: String,         // "OrganizationResult"
    val dslFunctionName: String,         // "organization"
    val packageName: String,             // "com.example.declarativejooq"
    val columns: List<ColumnIR>,
    val outboundFKs: List<ForeignKeyIR>, // FKs this table's records reference (this is child)
    val inboundFKs: List<ForeignKeyIR>,  // FKs pointing to this table (this is parent)
    val isRoot: Boolean,                 // true if no required outbound FKs
)

data class ColumnIR(
    val columnName: String,              // "organization_id"
    val propertyName: String,            // "organizationId" (camelCase)
    val javaType: Class<*>,              // Long::class.java
    val kotlinTypeName: TypeName,        // KotlinPoet TypeName
    val isIdentity: Boolean,
    val isNullable: Boolean,
    val tableFieldRefExpression: String  // "AppUserTable.APP_USER.ORGANIZATION_ID"
)

data class ForeignKeyIR(
    val fkName: String,
    val childField: ColumnIR,            // Single column (phase 2; multi-col = phase 4)
    val parentTableIR: TableIR,
    val builderFunctionName: String,     // "user" — derived from child table name
)
```

### Mapping jOOQ DataType to KotlinPoet TypeName

```kotlin
// jOOQ DataType.getType() returns the Java class; map to Kotlin equivalents
fun javaClassToKotlinTypeName(javaClass: Class<*>): TypeName = when (javaClass) {
    java.lang.Long::class.java, Long::class.java     -> LONG
    java.lang.Integer::class.java, Int::class.java   -> INT
    java.lang.String::class.java                     -> STRING
    java.lang.Boolean::class.java, Boolean::class.java -> BOOLEAN
    java.math.BigDecimal::class.java                 -> ClassName("java.math", "BigDecimal")
    java.time.LocalDate::class.java                  -> ClassName("java.time", "LocalDate")
    java.time.LocalDateTime::class.java              -> ClassName("java.time", "LocalDateTime")
    else -> javaClass.asTypeName()                   // fallback to KotlinPoet asTypeName()
}
```

### FileSpec with Top-Level Extension Function

```kotlin
// Source: KotlinPoet functions docs — verified
val file = FileSpec.builder(outputPackage, "${tableIR.tableName.capitalize()}Builders")
    .addImport("com.example.declarativejooq", "RecordGraph", "DslScope", "RecordNode")
    .addType(builderTypeSpec)
    .addFunction(rootExtensionFunSpec)  // DslScope.organization(block)
    .build()

file.writeTo(outputDirectory)  // writes to Path
// OR for tests:
val sourceString = buildString { file.writeTo(this) }
```

### kotlin-compile-testing: Compile Generated Source and Execute

```kotlin
// Source: tschuchortdev/kotlin-compile-testing README — HIGH confidence
fun compileAndRun(generatedKotlinSource: String): KotlinCompilation.Result {
    return KotlinCompilation().apply {
        sources = listOf(
            SourceFile.kotlin("GeneratedBuilders.kt", generatedKotlinSource)
        )
        inheritClassPath = true  // dsl-runtime + jOOQ + H2 all visible
        messageOutputStream = System.out
    }.compile()
}

// After compile:
// result.exitCode == KotlinCompilation.ExitCode.OK
// result.classLoader.loadClass("com.example.generated.GeneratedBuildersKt")
//   → invoke top-level extension function via reflection
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Reflections library for classpath scanning | ClassGraph | ~2018 | ClassGraph is faster, reads bytecode, doesn't require loading |
| JavaPoet for Kotlin generation | KotlinPoet | 2017+ | Native Kotlin constructs: nullable types, extension functions, data classes |
| String template code generation | KotlinPoet | — | Correct import management, no syntax errors from formatting |
| `batchInsert` for FK chain | Individual `store()` calls (Phase 1 decision) | Phase 1 | `batchInsert` doesn't return generated keys via JDBC; this affects how generated `buildRecord()` must work |

**Deprecated/outdated:**
- Reflections library (`org.reflections:reflections`): Poorly maintained since ~2020; ClassGraph is the replacement.
- JavaPoet for Kotlin: Use KotlinPoet which understands nullable types, `var`/`val`, extension functions, lambdas.

---

## Open Questions

1. **kotlin-compile-testing Kotlin 2.x compatibility**
   - What we know: Version 1.6.0 embeds Kotlin 1.9.24; project uses Kotlin 2.1.20
   - What's unclear: Whether generated code (using only basic Kotlin patterns) will compile successfully with the embedded 1.9.x compiler
   - Recommendation: Pin to 1.6.0 and test early in Wave 0; if compilation fails with version errors, evaluate ZacSweers fork at time of failure

2. **DslResult typed class naming (CODEGEN-05)**
   - What we know: Runtime has `DslResult` in `com.example.declarativejooq`; generated code must produce a typed wrapper
   - What's unclear: Whether the generated class should replace the runtime type or wrap it
   - Recommendation: Generate a typed `GeneratedDslResult` in the output package that accepts the runtime `DslResult` and exposes typed accessors; avoids name collision

3. **Column-to-property name mapping (camelCase conversion)**
   - What we know: jOOQ columns use snake_case (`organization_id`); Kotlin properties use camelCase (`organizationId`)
   - What's unclear: Edge cases with acronyms (e.g., `user_id` → `userId` vs `userID`)
   - Recommendation: Simple snake_case → camelCase: split on `_`, capitalize each word after first, join. Don't attempt acronym detection.

---

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit Jupiter 5.11.4 |
| Config file | `codegen/build.gradle.kts` — `tasks.test { useJUnitPlatform() }` (needs adding) |
| Quick run command | `./gradlew :codegen:test --tests "*.ScannerTest"` |
| Full suite command | `./gradlew :codegen:test` |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| CODEGEN-02 | Scans class directory; finds UpdatableRecordImpl and TableImpl subclasses | unit | `./gradlew :codegen:test --tests "*.ScannerTest"` | Wave 0 |
| CODEGEN-03 | Generates builder class with typed mutable properties per table field | compile-and-run | `./gradlew :codegen:test --tests "*.CodeGeneratorTest.builderCompilesAndHasTypedProperties"` | Wave 0 |
| CODEGEN-04 | Generates result class with typed property accessors wrapping UpdatableRecord | compile-and-run | `./gradlew :codegen:test --tests "*.CodeGeneratorTest.resultClassCompilesAndAccessorsWork"` | Wave 0 |
| CODEGEN-05 | Generates DslResult class with list per root table | compile-and-run | `./gradlew :codegen:test --tests "*.CodeGeneratorTest.dslResultContainsOrderedLists"` | Wave 0 |
| CODEGEN-06 | Generates nested FK builder function inside parent builder | compile-and-run | `./gradlew :codegen:test --tests "*.CodeGeneratorTest.nestedFkBuilderGeneratedAndWiresFK"` | Wave 0 |
| TEST-01 | Generated code compiles + executes (not string match) via kotlin-compile-testing | compile-and-run | `./gradlew :codegen:test` | Wave 0 |

### Sampling Rate
- **Per task commit:** `./gradlew :codegen:test`
- **Per wave merge:** `./gradlew :codegen:test`
- **Phase gate:** Full suite green before `/gsd:verify-work`

### Wave 0 Gaps
- [ ] `codegen/src/test/kotlin/com/example/declarativejooq/codegen/ScannerTest.kt` — covers CODEGEN-02
- [ ] `codegen/src/test/kotlin/com/example/declarativejooq/codegen/CodeGeneratorTest.kt` — covers CODEGEN-03, CODEGEN-04, CODEGEN-05, CODEGEN-06, TEST-01
- [ ] `codegen/build.gradle.kts` — needs full dependency declarations + `tasks.test { useJUnitPlatform() }`

---

## Sources

### Primary (HIGH confidence)
- jOOQ 3.19 javadoc (ForeignKey, TableImpl, Table APIs) — FK metadata extraction patterns
- KotlinPoet official docs at `square.github.io/kotlinpoet` — FileSpec, TypeSpec, FunSpec, PropertySpec examples
- `TestBuilders.kt` in project — canonical golden output pattern
- `TestSchema.kt` in project — canonical scan input
- `RecordBuilder.kt` in project — superclass contract for generated builders

### Secondary (MEDIUM confidence)
- ClassGraph GitHub README — classpath scanning API, URLClassLoader support
- kotlin-compile-testing GitHub README — KotlinCompilation API, inheritClassPath, result.classLoader
- KotlinPoet properties documentation page — mutable() pattern, getter/setter spec

### Tertiary (LOW confidence)
- WebSearch: KotlinPoet 2.2.0 as latest stable (GitHub releases confirmed — MEDIUM confidence)
- WebSearch: ClassGraph 4.8.181+ as latest stable (Maven Central search confirmed — MEDIUM confidence)
- WebSearch: kotlin-compile-testing 1.6.0 as latest stable (GitHub releases confirmed — MEDIUM confidence)

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — versions verified against GitHub releases and Maven Central
- Architecture: HIGH — IR structure derived from golden TestBuilders.kt; KotlinPoet patterns verified from official docs
- Pitfalls: HIGH for Kotlin companion object naming (observed in TestSchema.kt); MEDIUM for kotlin-compile-testing Kotlin 2.x compat (unverified)

**Research date:** 2026-03-15
**Valid until:** 2026-04-15 (stable libraries; KotlinPoet/ClassGraph rarely change APIs)
