# Project Research Summary

**Project:** declarative-jooq — v0.2 Natural DSL Naming & Placeholders
**Domain:** Kotlin code-generated jOOQ DSL library — builder naming and cross-tree FK references
**Researched:** 2026-03-16
**Confidence:** HIGH

## Executive Summary

The v0.2 milestone consists of two features: (1) child-table-named builder functions — changing the generated DSL so `appUser { }` names a child builder by the child table name instead of the FK column name — and (2) placeholder objects — returning a typed handle from builder blocks so users can explicitly wire FK relationships that the library cannot infer from nesting context alone. Both features build on the v0.1 implementation without requiring new external dependencies. All research is grounded in direct inspection of the codebase, giving HIGH confidence across the board.

The recommended implementation order is: dsl-runtime changes first (new `RecordPlaceholder<R>` class, `RecordNode.explicitDependencies` field, `TopologicalInserter` extension), then codegen changes (`MetadataExtractor` naming algorithm refactored to two-pass with collision detection, `BuilderEmitter` and `DslScopeEmitter` updated for placeholder return types), then tests updated atomically with each phase. The naming change and its test harness migration must be done atomically — partial renames produce confusing compile errors in inline Kotlin strings that are hard to diagnose.

The primary risks are naming collision (two FKs from the same child table to the same parent table naively producing the same builder function name) and placeholder PK timing (reading a PK before the referenced record is inserted). Both risks are well-understood and have clear prevention strategies: two-pass naming with explicit collision detection, and deferred-reference placeholders that carry `RecordNode` handles resolved by `TopologicalInserter` at insertion time — the same mechanism already used for parent-context FK wiring.

---

## Key Findings

### Recommended Stack

No new dependencies are required for v0.2. The existing stack is sufficient: Kotlin 2.1.20, jOOQ 3.19.16 (compileOnly), KotlinPoet 2.2.0 for code emission, and the existing test infrastructure (H2, kotlin-compile-testing 1.6.0, Testcontainers/Postgres). Placeholders are implemented as plain Kotlin data classes in `dsl-runtime` — no proxy frameworks, reflection utilities, or async libraries are needed.

The one pre-existing tech debt item to leave alone: `kotlin-compile-testing` 1.6.0 bundles a Kotlin 1.9.x compiler. The `-Xskip-metadata-version-check` workaround is in place. Do not upgrade unless a 2.x-compatible release is confirmed and tests still pass — this is out of scope for v0.2.

**Core technologies (no changes from v0.1):**
- Kotlin 2.1.20 — implementation language — no change
- KotlinPoet 2.2.0 — source code emission for codegen — emitter logic changes only
- jOOQ 3.19.16 — schema introspection + record API — no change
- H2 + kotlin-compile-testing 1.6.0 — unit and compile tests — extend with new cases
- Testcontainers (Postgres) — integration tests — extend with placeholder scenarios

### Expected Features

**Must have (table stakes for v0.2):**
- Child-table-named builder functions (default case) — `appUser { }` instead of `organization { }` for the child `app_user` builder on `OrganizationBuilder`
- FK-column fallback when the stripped column name does not match the parent table — `createdBy { }` and `updatedBy { }` preserved on `AppUserBuilder`
- Collision detection for multiple FKs from the same child table to the same parent table — prevents two methods named `appUser` on the same class
- Edge case handling for `table_name` and `table_name_id` columns both FK to the same table — must not generate duplicate function names
- `RecordPlaceholder<R>` returned from builder blocks — users can capture `val alice = appUser { }` and use it as a FK source
- Placeholder assignable to FK fields on generated builders — `createdBy = alice` wires Alice's future PK into the task's FK field
- Placeholder override of parent-context auto-resolution — explicit placeholder wins when both context and placeholder target the same FK field
- Cross-root-tree placeholder references — placeholder from one root tree assigned to a builder in a different root tree
- Compilation correctness for all generated DSL changes — verified via existing compile-testing infrastructure

**Should have (differentiators for v0.2):**
- Natural language-first naming — nesting reads as entity relationships, not FK column names
- Explicit FK override without escaping to raw jOOQ — users can wire FKs the library cannot infer, without `record.set(FIELD, id)`
- Disambiguation that is self-documenting — when FK-column fallback triggers, names like `createdBy { }` describe the relationship

**Defer (confirmed out of scope per PROJECT.md):**
- Composite FK placeholders
- Lazy/deferred insertion model
- Placeholder escaping the `execute { }` scope

### Architecture Approach

The existing v0.1 architecture is a clean pipeline: at build time, `ClasspathScanner` -> `MetadataExtractor` -> IR models -> emitters (KotlinPoet) -> `.kt` source files. At runtime, `DslScope` -> builder lambdas -> `RecordGraph` -> `TopologicalInserter` (Kahn's algorithm) -> insert + PK resolution. Both v0.2 features extend this pipeline at well-defined points without changing module boundaries or adding new modules.

**Major components and v0.2 changes:**
1. `MetadataExtractor` (codegen) — naming algorithm refactored from single-pass inline to two-pass: first derive candidate names, then detect collisions and apply fallback
2. `BuilderEmitter` (codegen) — emits placeholder setter properties per FK column; emits `collectPlaceholderDependencies()` override; child functions return `RecordPlaceholder<ChildRecord>`
3. `DslScopeEmitter` (codegen) — root extension functions return `RecordPlaceholder<Record>` instead of `Unit`
4. `RecordPlaceholder<R>` (dsl-runtime, new) — thin wrapper with a deferred `RecordNode` reference, resolved by `resolve(node)` when the child block executes
5. `RecordNode` (dsl-runtime) — gains `explicitDependencies: MutableList<Pair<TableField<*,*>, RecordPlaceholder<*>>>`
6. `TopologicalInserter` (dsl-runtime) — `buildTableGraph` includes placeholder edges; FK resolution loop applies explicit dependencies after parent-context resolution (last write wins, so explicit overrides parent)

**Key deferred-placeholder pattern:** child builder functions create the `RecordPlaceholder` before the deferred lambda runs, pass it into the lambda, and the lambda calls `placeholder.resolve(node)` when it executes. This ensures the placeholder is resolved before `TopologicalInserter` runs, maintaining the existing synchronous execution contract.

### Critical Pitfalls

1. **Builder function name collision (multiple FKs to same table)** — two FKs from `task` to `app_user` would both naively produce `appUser()`; prevent by computing names in two passes: assign candidates, then detect collisions and switch both to FK-column-based fallback. The existing `CodeGeneratorTest.multipleFkNaming()` test guards this invariant.

2. **Placeholder PK read before insert (stale null)** — placeholders must store a `RecordNode` reference, not a PK value; `TopologicalInserter` reads `placeholder.node.record.get(pk)` at insert time after the dependency is stored. Reading the PK at graph construction time will always return null.

3. **Topological sort missing cross-tree placeholder edges** — `buildTableGraph()` currently only walks `node.parentNode` links; it must also iterate `node.explicitDependencies` to add edges for placeholder-based FK dependencies, otherwise cross-tree records may insert in wrong order causing constraint violations.

4. **Placeholder override silently ignored** — the parent-context auto-resolution writes the FK at insert time; the explicit dependency must be applied after (not before) the parent-context write so the override takes effect. A dedicated test verifying the inserted FK matches the placeholder record (not the parent context record) is required.

5. **Test harness strings not updated atomically with naming change** — `CodeGeneratorTest` and `FullPipelineTest` contain inline Kotlin strings with hardcoded builder method names. Compile errors in these strings appear as `KotlinCompilation.ExitCode.COMPILATION_ERROR` pointing inside dynamic source, which is harder to diagnose. The naming change and all harness string updates must be committed together.

---

## Implications for Roadmap

Based on the dependency structure and pitfall analysis, two phases are appropriate. The naming change must ship atomically (naming logic + test harness migration). The placeholder feature has an internal dependency on `dsl-runtime` changes before `codegen` changes, but is otherwise independent from the naming change.

### Phase 1: Child-Table-Named Builder Functions

**Rationale:** The naming change is a pure codegen modification with no runtime impact. It is self-contained, validates immediately via existing tests, and should be done before the placeholder phase because the placeholder emitter will generate code using the new function names. Doing them together would conflate two sources of test failures.

**Delivers:** Updated `MetadataExtractor` naming algorithm (two-pass, collision-detecting); updated generated DSL with child-table-named builder functions and preserved FK-column fallback; updated test harnesses using new names; new edge-case tests for `table_name`/`table_name_id` collision.

**Addresses:** Child-table-named builders (table stakes), FK-column fallback preservation, collision detection, `table_name`/`table_name_id` edge case, natural language-first naming (differentiator).

**Avoids:** Pitfall 1 (collision — two-pass naming), Pitfall 2 (`table_name`/`table_name_id` — dedicated test schema), Pitfall 3 (self-ref rename — explicit decision and atomic harness update), Pitfall 7 (API churn — grep all harness strings before starting), Pitfall 11 (naming logic in one place — `MetadataExtractor` only).

### Phase 2: Placeholder Objects

**Rationale:** Placeholders require `dsl-runtime` changes (`RecordPlaceholder`, `RecordNode` update, `TopologicalInserter` extension) before `codegen` changes (emitter updates). The runtime changes have no codegen dependencies and can be developed and unit-tested in isolation. The codegen changes depend on the runtime type being defined. Integration tests come last and exercise cross-tree scenarios.

**Delivers:** `RecordPlaceholder<R>` class; `RecordNode.explicitDependencies`; extended `TopologicalInserter` with placeholder edge inclusion and deferred PK resolution; updated `BuilderEmitter` and `DslScopeEmitter` for placeholder return types; integration tests for cross-tree and override scenarios.

**Addresses:** Placeholder object (table stakes), placeholder FK assignment, placeholder override, cross-tree references, explicit FK override without escaping to raw jOOQ (differentiator).

**Avoids:** Pitfall 4 (deferred PK — `RecordNode` reference, not PK value), Pitfall 5 (override semantics — explicit deps applied after parent-context), Pitfall 6 (topological sort extended), Pitfall 8 (lambda return type — function returns placeholder, lambda stays `Unit`), Pitfall 9 (direct `RecordNode` reference, never index-based), Pitfall 10 (coordinate `BuilderEmitter` + `DslScopeEmitter` in same task).

### Phase Ordering Rationale

- Phase 1 before Phase 2 because the placeholder emitter generates code using the new child-table-named function names; mixing both changes increases diagnostic complexity when tests fail.
- Within Phase 2, `dsl-runtime` before `codegen` because `BuilderEmitter` references `RecordPlaceholder` by type name; the type must exist before the emitter can reference it.
- Tests are included within each phase (not a separate test phase) because compile-testing harnesses for both features require incremental verification — harness strings are fragile and must be validated immediately.
- Both phases are free of external dependency changes, reducing risk.

### Research Flags

Phases with standard, well-understood patterns (skip additional research-phase):
- **Phase 1 (naming):** Single-file logic change in a well-tested extraction pipeline. The algorithm is fully specified in research. No ambiguity.
- **Phase 2 (dsl-runtime):** Deferred-reference pattern is standard Kotlin. The exact API shape for `RecordPlaceholder` is fully specified in ARCHITECTURE.md.

Phases that may benefit from a quick design review before implementation:
- **Phase 2 (codegen emitter changes):** The exact KotlinPoet emission for placeholder setter properties (`var organization: RecordPlaceholder<OrganizationRecord>? = null`) and `collectPlaceholderDependencies()` override requires verifying KotlinPoet 2.2.0 API for generating override functions and nullable typed properties. Low risk — existing `BuilderEmitter` already generates typed properties and the API is stable.

---

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | HIGH | Direct build file inspection; no new dependencies confirmed by reading all affected source files |
| Features | HIGH | Direct codebase analysis for naming algorithm; MEDIUM for placeholder DSL patterns (ecosystem conventions, multiple sources agree) |
| Architecture | HIGH | All findings based on direct v0.1 source inspection; exact code patterns documented in ARCHITECTURE.md |
| Pitfalls | HIGH | Every pitfall grounded in actual code — specific files, line numbers, and test names cited |

**Overall confidence:** HIGH

### Gaps to Address

- **Self-ref naming decision:** The research documents options A (`category()` same-name, receiver disambiguation), B (`childCategory()` preserved), and C (configurable). The algorithm change is specified for the non-self-ref case; the self-ref case needs an explicit decision before implementation. Recommendation: option A (`category()`) aligns with the child-table-named goal and Kotlin resolves correctly by receiver type.
- **Placeholder setter naming convention:** The generated setter for a FK placeholder could be `var organization: RecordPlaceholder<OrganizationRecord>?` or `var organizationRef` or `var organizationPlaceholder` to distinguish it from a direct-value `var organizationId: Long?`. The naming convention should be decided before emission code is written so it is consistent across all generated builders.

---

## Sources

### Primary (HIGH confidence)
- Direct code inspection: `MetadataExtractor.kt`, `ForeignKeyIR.kt`, `BuilderEmitter.kt`, `DslScopeEmitter.kt`, `RecordBuilder.kt`, `RecordNode.kt`, `RecordGraph.kt`, `TopologicalInserter.kt`, `TopologicalSorter.kt` — all affected source files read directly
- Direct test inspection: `CodeGeneratorTest.kt`, `FullPipelineTest.kt`, `TestBuilders.kt` — existing naming assertions and harness patterns documented
- Build file inspection: `codegen/build.gradle.kts`, `dsl-runtime/build.gradle.kts`, `integration-tests/build.gradle.kts` — dependency versions confirmed
- Project requirements: `.planning/PROJECT.md` — milestone scope and named edge cases confirmed
- Kotlin official docs: type-safe builders — builder lambda convention (`() -> Unit`); wrapping function returns the builder object

### Secondary (MEDIUM confidence)
- Web search: Kotlin DSL builder return value patterns (Gradle `Provider<T>`, kotlinx.html, Ktor) — ecosystem consensus on placeholder/handle pattern; no single authoritative source
- Test data DSL patterns article — general pattern reference; content confirmed via research, source access limited

---
*Research completed: 2026-03-16*
*Ready for roadmap: yes*
