# Project Retrospective

*A living document updated after each milestone. Lessons feed forward into future planning.*

## Milestone: v1.0 — Initial Release

**Shipped:** 2026-03-16
**Phases:** 4 | **Plans:** 10 | **Tasks:** 19

### What Was Built
- Complete DSL runtime engine with topological insert, FK resolution, and typed results
- Code generator: ClasspathScanner → MetadataExtractor → IR → KotlinPoet emitters
- Gradle plugin with extension config, task registration, and test source set wiring
- Self-referential FK two-pass insert and multi-FK disambiguation
- Integration test suite against Postgres 16 via Testcontainers

### What Worked
- Module dependency order as phase order (runtime → codegen → plugin → integration) created clean progression where each phase's tests validated previous phases
- Compile-and-run validation pattern (kotlin-compile-testing) caught real issues that string-matching would have missed
- Hand-written builders in Phase 1 served as the golden reference pattern for code generation in Phase 2
- Two-pass IR extraction eliminated ordering bugs in metadata cross-linking

### What Was Inefficient
- ROADMAP.md phase checkboxes and progress table got out of sync with actual completion state — some phases showed `[ ]` despite being complete
- DSL-06 requirement text ("batch insert per table") was imprecise — the research phase discovered it was impossible as stated, but the requirement text was never updated
- Build-order fragility (hardcoded relative paths without `dependsOn`) was a known shortcut that should have been fixed at creation time

### Patterns Established
- `CodeGenerator.generateSource()` for in-memory compilation testing (no disk I/O needed)
- TestSchema classes shared between dsl-runtime tests and integration tests — single source of truth for jOOQ table definitions
- FK-column-name-based builder naming convention: strip `_id` suffix for readable API (`createdBy`, `updatedBy`)
- `buildWithChildren()` unconditionally on all builders — prevents latent bugs in intermediate builders

### Key Lessons
1. Requirements should be updated when research reveals the stated approach is impossible — leaving stale requirement text creates audit noise
2. Gradle test dependencies between modules should be declared explicitly, not relied on through implicit build ordering
3. `kotlin-compile-testing` version needs to match the project Kotlin version — 1.6.0 bundles Kotlin 1.9.x which requires `-Xskip-metadata-version-check`

### Cost Observations
- Model mix: ~70% sonnet (executors, verifiers), ~30% opus (orchestration)
- Sessions: ~4 sessions across 2 days
- Notable: Wave-based parallel execution was available but phases had sequential dependencies, so most execution was serial

---

## Cross-Milestone Trends

### Process Evolution

| Milestone | Phases | Plans | Key Change |
|-----------|--------|-------|------------|
| v1.0 | 4 | 10 | Initial project — established module-dependency-order phasing |

### Cumulative Quality

| Milestone | Tests | Verification Score | Tech Debt Items |
|-----------|-------|-------------------|-----------------|
| v1.0 | 30+ (H2 + Postgres) | 45/45 truths | 4 items |

### Top Lessons (Verified Across Milestones)

1. Module dependency order as phase execution order creates natural validation progression
2. Compile-and-run tests catch real issues that string matching misses
