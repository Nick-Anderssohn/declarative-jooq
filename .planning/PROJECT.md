# declarative-jooq

## What This Is

A Kotlin library that provides a DSL wrapping jOOQ for declaratively creating database records with automatic foreign key resolution. Designed for integration test data setup — you describe your record graph declaratively, and the library inserts everything in the right order with FK relationships handled automatically. Includes a code generator that scans jOOQ-generated classes and produces DSL builders, plus a Gradle plugin for build integration.

## Core Value

Eliminate boilerplate and manual FK wiring when creating test data — declare what records you want and how they relate, and the library handles insertion order, FK assignment, and result assembly.

## Requirements

### Validated

- ✓ Code generator that scans jOOQ record classes and generates DSL builder code — v1.0
- ✓ Gradle plugin that integrates code generation into the build process — v1.0
- ✓ DSL with `execute(dslContext) { ... }` entry point for declarative record creation — v1.0
- ✓ Nested builder blocks that automatically resolve single-column foreign keys from parent context — v1.0
- ✓ Support for root nodes (tables with no required FKs) at the top level of execute block — v1.0
- ✓ Support for child nodes nested under their FK parent — v1.0
- ✓ Topological insert order per table with individual `store()` calls for FK chain resolution — v1.0
- ✓ Records refreshed after insert to capture DB-generated defaults (IDs, timestamps) — v1.0
- ✓ Result objects wrapping each record with typed property accessors — v1.0
- ✓ Top-level result object (DslResult) containing ordered lists of result objects per root table — v1.0
- ✓ Support for self-referential foreign keys (two-pass insert) — v1.0
- ✓ Support for multiple FKs from one table to the same target table (disambiguated builder names) — v1.0
- ✓ Multiple records of the same type within a block — v1.0
- ✓ Ordering of result objects matches declaration order in the DSL — v1.0
- ✓ Compile-and-run codegen tests via kotlin-compile-testing — v1.0
- ✓ Gradle TestKit integration tests — v1.0
- ✓ End-to-end Postgres integration tests via Testcontainers — v1.0

### Active

(None — next milestone not yet planned)

### Out of Scope

- Multi-column (composite) foreign keys — rare and adds significant complexity
- Publishing to Maven Central or Gradle Plugin Portal — local only for now (mavenLocal)
- Production record creation — this is a test data tool
- Query/read DSL — this is insert-only; jOOQ handles queries
- Migration or schema management — jOOQ handles that
- Record update/delete operations
- Sensible default values for columns — defer to datafaker/kotlin-faker

## Context

Shipped v1.0 with 2,791 LOC Kotlin across 31 files in 4 modules.
Tech stack: Kotlin 2.1.20, jOOQ 3.19.16, KotlinPoet 2.1.0, ClassGraph 4.8.179, Gradle 8.12.
Tested against H2 (unit tests) and Postgres 16 (Testcontainers integration tests).

Known tech debt:
- Build-order fragility: codegen/integration-tests hardcode relative path to dsl-runtime test classes without Gradle `dependsOn`
- `kotlin-compile-testing:1.6.0` bundles Kotlin 1.9.x compiler (project uses 2.1.20)

## Constraints

- **Kotlin**: 2.1.20 (built with; targets JVM 11)
- **jOOQ**: 3.19.16
- **Build tool**: Gradle plugin with `apply plugin:` and extension configuration
- **Publishing**: mavenLocal only
- **Insert strategy**: Individual `store()` calls per record in topological order (batch cannot return generated keys)

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Individual `store()` calls per record, not `batchInsert()` | JDBC batch cannot return generated keys needed for FK chain resolution | ✓ Good — correct design, all FK chains work |
| Test data focus, not general purpose | Keeps scope tight, allows opinionated defaults | ✓ Good — clean API surface |
| Gradle plugin (not just task) | Better UX — `apply plugin:` with extension config | ✓ Good — config cache compatible |
| Skip composite FKs | Rare in practice, significant complexity | ✓ Good — deferred to v2 |
| Static field reflection for table singleton discovery | Kotlin companion object fields use property name, not `INSTANCE` | ✓ Good — works for jOOQ tables |
| URLClassLoader with context classloader as parent | Ensures jOOQ API types resolve when loading user classes | ✓ Good — no ClassCastException |
| Two-pass IR extraction | Build all TableIR first, cross-link inboundFKs second | ✓ Good — no ordering dependency |
| `DslScope.recordGraph` made public (was internal) | Generated extension functions in separate module need access | ✓ Good — correct API boundary for generated code |
| Self-ref root tables use child-style constructor | Serves as both DSL entry point and self-ref child builder | ✓ Good — single code path |
| `buildWithChildren()` on ALL builders unconditionally | Prevents bugs where intermediate builders skip children | ✓ Good — fixed latent bug |
| FK-column-based naming for builder functions | `created_by` → `createdBy`, `updated_by` → `updatedBy` | ✓ Good — readable and disambiguated |
| Reuse TestSchema classes for integration tests | Proves Postgres dialect without javac complexity | ✓ Good — simpler test setup |

---
*Last updated: 2026-03-16 after v1.0 milestone*
