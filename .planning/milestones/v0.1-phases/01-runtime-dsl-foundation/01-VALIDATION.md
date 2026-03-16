---
phase: 1
slug: runtime-dsl-foundation
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-03-15
---

# Phase 1 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 (Jupiter) 5.12.x |
| **Config file** | `dsl-runtime/build.gradle.kts` — `tasks.test { useJUnitPlatform() }` |
| **Quick run command** | `./gradlew :dsl-runtime:test` |
| **Full suite command** | `./gradlew :dsl-runtime:test :codegen:test :gradle-plugin:test` |
| **Estimated runtime** | ~10 seconds |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew :dsl-runtime:test`
- **After every plan wave:** Run `./gradlew :dsl-runtime:test :codegen:test :gradle-plugin:test`
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** 15 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 01-01 | 01 | 1 | PROJ-01 | smoke | `./gradlew projects` | ❌ W0 | ⬜ pending |
| 01-02 | 01 | 1 | PROJ-02 | unit | `./gradlew :dsl-runtime:dependencies --configuration compileClasspath` | ❌ W0 | ⬜ pending |
| 01-03 | 01 | 1 | PROJ-03 | unit | `./gradlew :codegen:test` | ❌ W0 | ⬜ pending |
| 01-04 | 01 | 1 | PROJ-04 | structural | Verified by module dep declarations | ❌ W0 | ⬜ pending |
| 01-05 | 02 | 2 | DSL-01 | integration | `./gradlew :dsl-runtime:test --tests "*.DslExecutionTest.testBasicExecute"` | ❌ W0 | ⬜ pending |
| 01-06 | 02 | 2 | DSL-02 | integration | `./gradlew :dsl-runtime:test --tests "*.DslExecutionTest.testRootBuilder"` | ❌ W0 | ⬜ pending |
| 01-07 | 02 | 2 | DSL-03 | integration | `./gradlew :dsl-runtime:test --tests "*.DslExecutionTest.testFkResolution"` | ❌ W0 | ⬜ pending |
| 01-08 | 02 | 2 | DSL-04 | integration | `./gradlew :dsl-runtime:test --tests "*.DslExecutionTest.testMultipleChildren"` | ❌ W0 | ⬜ pending |
| 01-09 | 02 | 2 | DSL-05 | unit | `./gradlew :dsl-runtime:test --tests "*.TopologicalSorterTest.*"` | ❌ W0 | ⬜ pending |
| 01-10 | 03 | 3 | DSL-06 | integration | `./gradlew :dsl-runtime:test --tests "*.DslExecutionTest.testTopologicalOrder"` | ❌ W0 | ⬜ pending |
| 01-11 | 02 | 2 | DSL-07 | integration | `./gradlew :dsl-runtime:test --tests "*.DslExecutionTest.testGeneratedKeyPopulated"` | ❌ W0 | ⬜ pending |
| 01-12 | 02 | 2 | DSL-08 | integration | `./gradlew :dsl-runtime:test --tests "*.DslExecutionTest.testDeclarationOrder"` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

**Note on DSL-06:** The original requirement ("batch insert per table") is satisfied by individual `store()` calls grouped in topological table order. Batch insert is architecturally incompatible because it does not reliably return generated keys across JDBC drivers, which are needed for FK resolution. The `DslExecutionTest.testTopologicalOrder` test verifies that records are grouped by table and inserted in the correct topological order via the integration test (org inserted before user, FK resolution proves ordering).

---

## Wave 0 Requirements

- [ ] `dsl-runtime/src/test/kotlin/com/example/declarativejooq/DslExecutionTest.kt` — stubs for DSL-01 through DSL-04, DSL-06, DSL-07, DSL-08
- [ ] `dsl-runtime/src/test/kotlin/com/example/declarativejooq/TopologicalSorterTest.kt` — stubs for DSL-05
- [ ] `dsl-runtime/src/test/kotlin/com/example/declarativejooq/TestSchema.kt` — shared H2 setup and hand-crafted jOOQ table/record classes
- [ ] `settings.gradle.kts` — root project file satisfying PROJ-01
- [ ] `dsl-runtime/build.gradle.kts`, `codegen/build.gradle.kts`, `gradle-plugin/build.gradle.kts` — module scaffolds

*If none: "Existing infrastructure covers all phase requirements."*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Module dependency isolation | PROJ-02, PROJ-04 | Structural verification of build config | Run `./gradlew :dsl-runtime:dependencies --configuration compileClasspath` and verify no KotlinPoet/Gradle API entries |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 15s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
