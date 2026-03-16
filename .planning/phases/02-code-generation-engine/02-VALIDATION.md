---
phase: 2
slug: code-generation-engine
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-03-15
---

# Phase 2 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit Jupiter 5.11.4 |
| **Config file** | `codegen/build.gradle.kts` — `tasks.test { useJUnitPlatform() }` (needs adding) |
| **Quick run command** | `./gradlew :codegen:test --tests "*.ScannerTest"` |
| **Full suite command** | `./gradlew :codegen:test` |
| **Estimated runtime** | ~30 seconds |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew :codegen:test`
- **After every plan wave:** Run `./gradlew :codegen:test`
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** 30 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 02-01-01 | 01 | 0 | TEST-01 | setup | `./gradlew :codegen:test` | ❌ W0 | ⬜ pending |
| 02-02-01 | 02 | 1 | CODEGEN-02 | unit | `./gradlew :codegen:test --tests "*.ScannerTest"` | ❌ W0 | ⬜ pending |
| 02-03-01 | 03 | 2 | CODEGEN-03 | compile-and-run | `./gradlew :codegen:test --tests "*.CodeGeneratorTest.builderCompilesAndHasTypedProperties"` | ❌ W0 | ⬜ pending |
| 02-03-02 | 03 | 2 | CODEGEN-04 | compile-and-run | `./gradlew :codegen:test --tests "*.CodeGeneratorTest.resultClassCompilesAndAccessorsWork"` | ❌ W0 | ⬜ pending |
| 02-03-03 | 03 | 2 | CODEGEN-05 | compile-and-run | `./gradlew :codegen:test --tests "*.CodeGeneratorTest.dslResultContainsOrderedLists"` | ❌ W0 | ⬜ pending |
| 02-04-01 | 04 | 3 | CODEGEN-06 | compile-and-run | `./gradlew :codegen:test --tests "*.CodeGeneratorTest.nestedFkBuilderGeneratedAndWiresFK"` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `codegen/build.gradle.kts` — full dependency declarations + `tasks.test { useJUnitPlatform() }`
- [ ] `codegen/src/test/kotlin/com/example/declarativejooq/codegen/ScannerTest.kt` — stubs for CODEGEN-02
- [ ] `codegen/src/test/kotlin/com/example/declarativejooq/codegen/CodeGeneratorTest.kt` — stubs for CODEGEN-03, CODEGEN-04, CODEGEN-05, CODEGEN-06, TEST-01

---

## Manual-Only Verifications

*All phase behaviors have automated verification.*

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 30s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
