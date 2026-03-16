# Milestones

## v1.0 (Shipped: 2026-03-16)

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

**Archive:** `.planning/milestones/v1.0-ROADMAP.md`, `v1.0-REQUIREMENTS.md`, `v1.0-MILESTONE-AUDIT.md`

---

