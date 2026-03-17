# Technology Stack

**Project:** declarative-jooq — v0.2 Natural DSL Naming & Placeholders
**Researched:** 2026-03-16
**Scope:** Stack additions and changes required for child-table-named builders and placeholder objects ONLY. Pre-existing validated stack is not re-researched.

---

## Verdict: No New Dependencies Required

Both v0.2 features (child-table-named builders and placeholder objects) are implementable as pure logic and data-modeling changes within the existing four modules. The existing dependency set is sufficient.

**Confidence:** HIGH — based on direct code inspection of all affected source files.

---

## Validated Existing Stack (Do Not Change)

| Technology | Pinned Version | Module | Role |
|------------|---------------|--------|------|
| Kotlin | 2.1.20 | all | Implementation language |
| jOOQ | 3.19.16 | codegen, dsl-runtime | Schema introspection + record API |
| KotlinPoet | 2.2.0 | codegen | Source code emission |
| ClassGraph | 4.8.181 | codegen | Classpath scanning |
| Gradle | 8.12 | gradle-plugin | Build integration |
| JUnit Jupiter | 5.11.4 | all | Test runner |
| H2 | 2.3.232 | codegen, dsl-runtime | In-memory DB for unit tests |
| kotlin-compile-testing | 1.6.0 | codegen, integration-tests | Compile-and-run codegen tests |
| Testcontainers (postgres) | 1.20.6 | integration-tests | Postgres dialect validation |
| PostgreSQL JDBC | 42.7.4 | integration-tests | Postgres driver |
| SLF4J simple | 2.0.16 | integration-tests | Testcontainers log routing |

Note: The old STACK.md (v0.1, written before implementation) listed KotlinPoet 1.17.x. The actual build uses **2.2.0** — the installed version wins.

---

## What Each v0.2 Feature Touches

### Feature 1: Child-Table-Named Builders

**Where the change lives:** `codegen` module only — specifically `MetadataExtractor.kt` and `ForeignKeyIR.kt`.

**What changes:**

Currently `MetadataExtractor` derives `builderFunctionName` from the FK column name:

```kotlin
val strippedFkCol = fkColumnName.removeSuffix("_id")
val builderFunctionName = if (isSelfRef) {
    "child" + toPascalCase(tableName)
} else {
    toCamelCase(strippedFkCol)   // e.g., "organization_id" -> "organization"
}
```

The new rule: use `toCamelCase(childTableName)` as the default, fall back to the FK-column-derived name only when the FK column name does not match the parent table name pattern (i.e., when there are multiple FKs to the same parent, or when `table_name` and `table_name_id` both exist pointing to the same table).

The disambiguation logic already exists conceptually — the multi-FK case (`created_by`, `updated_by` both to `app_user`) already uses FK-column names. The only change is the default for the unambiguous single-FK case.

`ForeignKeyIR.builderFunctionName` is already the right shape — it just gets a different value computed at scan time. No structural changes to IR are needed.

`BuilderEmitter` consumes `fk.builderFunctionName` verbatim. No changes needed to the emitter.

**New libraries needed:** None.

---

### Feature 2: Placeholder Objects

**Where the change lives:** `dsl-runtime` module (new class) and `codegen` module (updated emitter return types).

**What a placeholder must do:**
1. Be returned from a child builder function call (e.g., `val aliceRef = organization { ... }`)
2. Hold a reference to the `RecordNode` that will exist after the DSL block is executed and the graph is built
3. Be passable to other builders as an explicit FK source, overriding the auto-resolved parent context
4. Support cross-root-tree references — a placeholder captured in one root tree's block assigned to an FK in a different root tree's block

**Implementation approach — pure Kotlin data modeling in dsl-runtime:**

A `Placeholder<R : UpdatableRecord<R>>` class wraps a `RecordNode`. The builder functions on generated builders are changed from returning `Unit` to returning `Placeholder<R>`:

```kotlin
// Current generated output:
fun appUser(block: AppUserBuilder.() -> Unit) { ... }  // returns Unit

// New generated output:
fun appUser(block: AppUserBuilder.() -> Unit): Placeholder<AppUserRecord> { ... }
```

The placeholder is returned immediately after the deferred child block is registered. The actual `RecordNode` inside the placeholder is populated when `buildWithChildren()` executes the deferred block. This means the placeholder acts as a handle that is safe to pass around within the DSL block, resolved lazily when insertion runs.

For explicit FK assignment (overriding parent context), the `RecordBuilder` receives an optional `explicitFkSource: Placeholder<*>?` and the `TopologicalInserter` resolves it at insert time — same mechanism as parent FK resolution, just sourced from the placeholder's node instead of `parentNode`.

**New types needed in dsl-runtime:**
- `Placeholder<R : UpdatableRecord<R>>` — wraps a lazy `RecordNode` reference; populated by `buildWithChildren()`
- Possibly a `PlaceholderFkBinding` data class — pairs a `Placeholder<*>` with the `TableField<*, *>` it should populate — registered on the `RecordNode` or `RecordGraph`

**New libraries needed:** None. These are straightforward Kotlin data classes. No proxy/reflection tricks are required — the placeholder is an eagerly-constructed wrapper whose internal node reference is set by the deferred block execution, which all happens within the same `execute { }` call before `TopologicalInserter` runs.

---

## What NOT to Add

| Temptation | Why Not |
|------------|---------|
| Kotlin `kotlin-reflect` | Not needed — no runtime type inspection required for placeholder resolution |
| Guava / Arrow / other utility libs | Overkill; the placeholder pattern is a simple wrapper class |
| A proxy/delegation framework | Placeholders do not need to transparently intercept calls; they are explicit handles |
| kotlinx.coroutines | No async required; DSL execution is synchronous |
| A separate `:placeholder` module | Placeholder lives in `dsl-runtime` — it is a runtime concept, not a codegen concept |
| Upgrading kotlin-compile-testing | Known issue: 1.6.0 bundles Kotlin 1.9.x compiler vs project's 2.1.20. The `-Xskip-metadata-version-check` workaround is in place and working. Do NOT upgrade without checking that a 2.x-compatible release exists and that tests still pass — this is pre-existing tech debt, not v0.2 work. |

---

## Integration Points Between Features and Existing Code

### Child-Table-Named Builders — Integration Checklist

| File | Change | Risk |
|------|--------|------|
| `MetadataExtractor.kt` | New `builderFunctionName` derivation logic | LOW — isolated, no runtime impact |
| `ForeignKeyIR.kt` | No structural change; field value differs | LOW |
| `BuilderEmitter.kt` | No change needed | None |
| `CodeGeneratorTest.kt` | Test 7 (`multipleFkNaming`) asserts `createdBy` and `updatedBy` present, `task` absent — this test remains valid | None |
| `CodeGeneratorTest.kt` (harnesses) | Harnesses use `organization { ... }` (child-table-named already) and `createdBy { ... }` (FK-column-named for multi-FK disambiguation) — both must continue working | LOW |
| `FullPipelineTest.kt` | Same patterns — verify harness call sites after name change | LOW |

The naming change will affect the generated function name for `AppUserBuilder.organization(...)` — currently generated as `organization` (FK column `organization_id` stripped to `organization`, which happens to equal the child table name `app_user`... wait, actually `app_user` has FK `organization_id` pointing to `organization`, so the builder function on `OrganizationBuilder` is for `app_user` records). Let me be precise:

- `OrganizationBuilder` has an inbound FK from `app_user.organization_id`
- Current name: `organization` (from `organization_id` stripped) — this is the builder function name on `OrganizationBuilder` for creating child `app_user` records
- New name: `appUser` (from child table name `app_user` → camelCase `appUser`)

The test harnesses currently call `organization { ... }` inside `organization { ... }`. After the rename, this becomes `appUser { ... }`. The harnesses must be updated to use the new name, and Test 7 must be updated to assert `appUser` is present instead of `organization`.

The multi-FK case: `AppUserBuilder` has inbound FKs from `task.created_by` and `task.updated_by` (both to `app_user`). These both point to the same child table `task`, so disambiguation is required — FK column names are used: `createdBy` and `updatedBy`. This behavior stays unchanged.

### Placeholder Objects — Integration Checklist

| File | Change | Risk |
|------|--------|------|
| `dsl-runtime` | New `Placeholder<R>` class | LOW |
| `RecordBuilder.kt` | `buildWithChildren()` populates placeholder; child builder functions return `Placeholder<R>` | MEDIUM — changes return type contract |
| `BuilderEmitter.kt` | Child builder functions emit `Placeholder<R>` return type | MEDIUM |
| `TopologicalInserter.kt` | Resolve `PlaceholderFkBinding`s after all nodes built but before insert | MEDIUM |
| `RecordGraph.kt` | May need to collect `PlaceholderFkBinding`s registered during DSL block | LOW |
| Test harnesses | New harness methods for placeholder usage; existing tests unaffected (they don't use placeholders) | LOW |

---

## Actual Versions to Use (v0.2)

No version changes needed. Use exactly what is in the build files today:

```kotlin
// codegen/build.gradle.kts — unchanged
implementation("com.squareup:kotlinpoet:2.2.0")
implementation("io.github.classgraph:classgraph:4.8.181")
compileOnly("org.jooq:jooq:3.19.16")

// dsl-runtime/build.gradle.kts — unchanged
compileOnly("org.jooq:jooq:3.19.16")
```

---

## Confidence Assessment

| Area | Confidence | Reason |
|------|------------|--------|
| No new libraries needed | HIGH | Both features are data modeling changes; verified by reading all affected source files |
| Naming logic change scope | HIGH | `MetadataExtractor.builderFunctionName` is the single derivation point; `BuilderEmitter` consumes it without transformation |
| Placeholder implementation approach | MEDIUM | Design is clear; exact API shape (how callers assign placeholder to FK) needs design decision in the implementation plan, not a library question |
| Test harness updates required | HIGH | Existing harnesses hardcode `organization { ... }` as child builder name, which will change to `appUser { ... }` after the naming fix |
| kotlin-compile-testing compatibility | MEDIUM | The `-Xskip-metadata-version-check` workaround is in place; any new generated code patterns must continue to compile under the 1.9.x compiler that 1.6.0 bundles |

---

## Sources

- Direct code inspection: `MetadataExtractor.kt`, `ForeignKeyIR.kt`, `BuilderEmitter.kt`, `RecordBuilder.kt`, `RecordNode.kt`, `RecordGraph.kt`, `TopologicalInserter.kt`, `DslScope.kt`
- Build file inspection: `codegen/build.gradle.kts`, `dsl-runtime/build.gradle.kts`, `integration-tests/build.gradle.kts`
- Test inspection: `CodeGeneratorTest.kt`, `FullPipelineTest.kt`, `TestBuilders.kt`
- Project context: `.planning/PROJECT.md`
