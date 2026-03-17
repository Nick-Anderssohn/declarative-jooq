# Project Retrospective

*A living document updated after each milestone. Lessons feed forward into future planning.*

## Milestone: v0.1 — Initial Release

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

## Milestone: v0.2 — Natural DSL Naming & Placeholders

**Shipped:** 2026-03-17
**Phases:** 2 (5-6) | **Plans:** 5 | **Tasks:** 11

### What Was Built
- Two-pass FK naming algorithm replacing single-pass inline naming — child table name by default, FK-column fallback, collision detection
- Self-referential FK naming fix: `category { }` instead of `childCategory { }`
- PlaceholderRef infrastructure: `val alice = appUser { }` returns a typed placeholder for future FK wiring
- Cross-tree placeholder FK assignment with correct topological sort order
- Codegen emitter updates: Result-returning builders and placeholder FK property setters
- README documentation with working naming and placeholder examples

### What Worked
- Two-pass algorithm (collect candidates → detect collisions) was the right decomposition — NAME-03 collision detection fell out naturally without lookahead
- PlaceholderRef stored on the referencing node (not the placeholder node) aligned cleanly with FK ownership semantics
- Reusing existing TestSchema in integration tests made cross-tree placeholder tests straightforward to write
- Plans were very well-scoped — each plan had a clear contract and zero deviation

### What Was Inefficient
- STATE.md progress tracking got stale (showed 0% and "planning" status despite all phases complete) — the CLI `milestone complete` command's state update didn't reflect actual progress
- ROADMAP.md plan checkboxes for Phase 6 were `[ ]` instead of `[x]` (copy issue from roadmap creation)

### Patterns Established
- Two-pass naming: collect candidate names first, then resolve collisions — applicable to any naming scheme with uniqueness constraints
- PlaceholderRef pattern: data class on the referencing node iterated in the inserter — clean separation between declaration and resolution
- Suppress FK column property when placeholder property name matches — prevents conflicting Kotlin declarations in generated code

### Key Lessons
1. Planning documents (ROADMAP.md, STATE.md) need a cleanup pass at milestone start — stale checkboxes and status fields create confusion during completion
2. Generated code must handle property name collisions explicitly — when `placeholderPropertyName == column.propertyName`, one must be suppressed to avoid Kotlin `conflicting declarations`
3. `lateinit var` works cleanly for cross-tree placeholder harness because `execute {}` blocks run eagerly — no async complications

### Cost Observations
- Model mix: ~100% sonnet (all execution phases used balanced profile)
- Sessions: 2-3 sessions across 1 day
- Notable: Very fast milestone — v0.2 scoped to exactly 2 phases with clear requirements, executed in 1 day

---

## Cross-Milestone Trends

### Process Evolution

| Milestone | Phases | Plans | Key Change |
|-----------|--------|-------|------------|
| v0.1 | 4 | 10 | Initial project — established module-dependency-order phasing |
| v0.2 | 2 | 5 | Feature iteration — two-pass naming + placeholder objects on solid v0.1 foundation |

### Cumulative Quality

| Milestone | Tests | Verification Score | Tech Debt Items |
|-----------|-------|-------------------|-----------------|
| v0.1 | 30+ (H2 + Postgres) | 45/45 truths | 4 items |
| v0.2 | 40+ (H2 + Postgres) | 5/5 success criteria | 2 items (STATE/ROADMAP staleness) |

### Top Lessons (Verified Across Milestones)

1. Module dependency order as phase execution order creates natural validation progression
2. Compile-and-run tests catch real issues that string matching misses
3. Two-pass algorithms (collect → resolve) are the clean pattern for naming with uniqueness constraints
