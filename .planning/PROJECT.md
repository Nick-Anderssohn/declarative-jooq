# declarative-jooq

## What This Is

A Kotlin library that provides a DSL wrapping jOOQ for declaratively creating database records with automatic foreign key resolution. Primarily designed for integration test data setup — you describe your record graph declaratively, and the library inserts everything in the right order with FK relationships handled automatically. Includes a Gradle plugin that scans jOOQ-generated classes and generates the DSL code.

## Core Value

Eliminate boilerplate and manual FK wiring when creating test data — declare what records you want and how they relate, and the library handles insertion order, FK assignment, and result assembly.

## Requirements

### Validated

(None yet — ship to validate)

### Active

- [ ] Code generator that scans jOOQ record classes and generates DSL builder code
- [ ] Gradle plugin that integrates code generation into the build process
- [ ] DSL with `execute(dslContext) { ... }` entry point for declarative record creation
- [ ] Nested builder blocks that automatically resolve single-column foreign keys from parent context
- [ ] Support for root nodes (tables with no required FKs) at the top level of execute block
- [ ] Support for child nodes nested under their FK parent
- [ ] Batch insert per table in topological (dependency) order
- [ ] Records refreshed after insert to capture DB-generated defaults (IDs, timestamps)
- [ ] Result objects wrapping each record with typed property accessors
- [ ] Top-level result object (DslResult) containing ordered lists of result objects per root table
- [ ] Result objects for parent tables include ordered lists of child result objects
- [ ] Support for self-referential foreign keys (e.g., category.parent_id → category.id)
- [ ] Support for multiple FKs from one table to the same target table (e.g., created_by/updated_by → user.id)
- [ ] Multiple records of the same type within a block (multiple users under one organization)
- [ ] Ordering of result objects matches declaration order in the DSL

### Out of Scope

- Multi-column (composite) foreign keys — rare and adds significant complexity
- Publishing to Maven Central or private repos — local only for now (mavenLocal)
- Production record creation — this is a test data tool
- Query/read DSL — this is insert-only
- Migration or schema management — jOOQ handles that
- Record update/delete operations

## Context

- jOOQ generates record classes from database schemas (e.g., `OrganizationRecord`, `UserRecord`) that extend `UpdatableRecordImpl`
- jOOQ record classes have typed properties for each column and constructors with all fields
- Foreign key metadata is available from jOOQ's generated table references (e.g., `Organization.ORGANIZATION`, `User.USER`)
- The code generator needs to inspect jOOQ record classes at compile time to produce the DSL builders
- The Gradle plugin scans a user-specified directory for jOOQ-generated classes and runs codegen

## Constraints

- **Kotlin**: 1.9+ — broad compatibility
- **jOOQ**: 3.18+ — recent enough for current API patterns
- **Build tool**: Gradle plugin (not just a task) with `apply plugin:` and extension configuration
- **Publishing**: mavenLocal only for now
- **Insert strategy**: Batch per table in topological order, records refreshed after insert for DB-generated values

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Batch insert per table in topological order | Efficient — fewer round trips than one-at-a-time, while still respecting FK dependencies | — Pending |
| Test data focus, not general purpose | Keeps scope tight, allows opinionated defaults (e.g., always refresh after insert) | — Pending |
| Gradle plugin (not just task) | Better UX — `apply plugin:` with extension config, integrates cleanly into build | — Pending |
| Skip composite FKs | Rare in practice, significant complexity for codegen and DSL design | — Pending |

---
*Last updated: 2026-03-15 after initialization*
