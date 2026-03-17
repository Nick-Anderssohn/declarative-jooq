# Milestones

## v0.2 Natural DSL Naming & Placeholders (Shipped: 2026-03-17)

**Phases:** 2 (5-6) | **Plans:** 5 | **Tasks:** 11
**Commits:** 24 | **Kotlin LOC:** 3,364 (+573 from v0.1) | **Files changed:** 31 (+3,588, -103)
**Timeline:** 1 day (2026-03-16 → 2026-03-17)
**Requirements:** 9/9 satisfied

**Key accomplishments:**
1. Two-pass FK naming algorithm: builder functions default to child table name (`appUser { }` instead of `userId { }`)
2. FK-column fallback naming when column doesn't match parent table (`createdBy { }` for `created_by → app_user`)
3. Collision detection for duplicate builder names — both fall back to FK-column names (NAME-03)
4. Self-referential FK builders use table name (`category { }`, not `childCategory { }`)
5. PlaceholderRef infrastructure: `val alice = appUser { }` captures a typed placeholder for future FK wiring
6. Cross-tree placeholder references with correct topological insert order within `execute` block
7. Codegen emitters updated to generate Result-returning builders and placeholder FK properties
8. README documented with naming conventions and placeholder usage patterns

**Archive:** `.planning/milestones/v0.2-ROADMAP.md`, `v0.2-REQUIREMENTS.md`

---

## v0.1 (Shipped: 2026-03-16)

**Phases:** 4 | **Plans:** 10 | **Tasks:** 19
**Commits:** 60 | **Kotlin LOC:** 2,791 across 31 files
**Timeline:** 2 days (2026-03-15 → 2026-03-16)
**Requirements:** 25/25 satisfied

**Key accomplishments:**
1. Three-module Gradle project scaffold (dsl-runtime, codegen, gradle-plugin) with correct inter-module dependencies
2. Complete DSL runtime engine: topological insert, FK resolution, typed DslResult with declaration-order preservation
3. Code generation engine: ClasspathScanner, MetadataExtractor, IR models, KotlinPoet emitters producing compilable DSL source
4. Gradle plugin with `apply plugin:` syntax, extension configuration, source set wiring, and configuration cache compatibility
5. Self-referential FK support (two-pass insert) and multi-FK disambiguation (FK-column-name-based builder naming)
6. End-to-end integration tests against real Postgres 16 via Testcontainers (6 scenarios)

**Tech debt accepted:**
- DSL-06 requirement text says "batch insert" but implementation uses individual `store()` (correct by design — batch cannot return generated keys)
- Build-order fragility: codegen and integration-tests hardcode relative paths to dsl-runtime test classes without Gradle `dependsOn`
- `kotlin-compile-testing:1.6.0` bundles older Kotlin compiler (1.9.x vs project's 2.1.20)

**Archive:** `.planning/milestones/v0.1-ROADMAP.md`, `v0.1-REQUIREMENTS.md`, `v0.1-MILESTONE-AUDIT.md`

---

