# Phase 1: Runtime DSL Foundation - Context

**Gathered:** 2026-03-15
**Status:** Ready for planning

<domain>
## Phase Boundary

Three-module Gradle project scaffold (dsl-runtime, codegen, gradle-plugin) plus the complete DSL runtime engine in dsl-runtime. The runtime accepts a declarative record graph via `execute(dslContext) { ... }`, inserts records in topological order with automatic FK resolution from parent context, and returns a typed DslResult with results in declaration order. Phase 1 uses hand-written builders for testing since codegen is Phase 2.

</domain>

<decisions>
## Implementation Decisions

### Claude's Discretion
- Insert + refresh strategy — how to reconcile batch efficiency with needing PKs for FK resolution (research flagged that batch insert doesn't return generated keys)
- Builder API shape — base interfaces/abstract classes that generated code will implement, how FK context flows through nesting
- Result object nesting depth and access patterns
- Error behavior on constraint violations, missing FKs, insertion failures
- Test database choice for Phase 1 (H2 Postgres mode vs Testcontainers)

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Project context
- `.planning/PROJECT.md` — Core value, constraints, key decisions
- `.planning/REQUIREMENTS.md` — DSL-01 through DSL-08, PROJ-01 through PROJ-04 are this phase's requirements
- `.planning/ROADMAP.md` — Phase 1 success criteria and dependency structure

### Research findings
- `.planning/research/STACK.md` — Recommended stack, testing strategy, module structure
- `.planning/research/ARCHITECTURE.md` — Component boundaries, data flow, module dependencies
- `.planning/research/PITFALLS.md` — Batch insert + refresh conflict, classloader isolation, topological sort edge cases
- `.planning/research/SUMMARY.md` — Synthesized findings and roadmap implications

### User's DSL examples
- The user provided concrete DSL usage examples during project initialization (in PROJECT.md context) showing: `execute { organization { user { } } }` nesting pattern, result object structure with `OrganizationResult` containing `users: List<UserRecord>`, and `DslResult` as the top-level return type

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- None — greenfield project

### Established Patterns
- None yet — this phase establishes the patterns

### Integration Points
- jOOQ's `DSLContext` for record insertion
- jOOQ's `UpdatableRecordImpl` as the base class for records
- jOOQ's `Table`, `ForeignKey`, `TableField` APIs for metadata

</code_context>

<specifics>
## Specific Ideas

- User provided concrete DSL example: `execute { organization { name = "Org 1"; user { email = "..."; name = "..." } } }`
- Result objects: `OrganizationResult` has `record: OrganizationRecord` + `users: List<UserRecord>`, with convenience getters delegating to record
- `DslResult` contains `organizations: List<OrganizationResult>`
- FK fields (like `organizationId` on user) are auto-populated from parent context — user doesn't set them
- Multiple records of same type in same block (multiple `user { }` blocks under one `organization { }`)

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 01-runtime-dsl-foundation*
*Context gathered: 2026-03-15*
