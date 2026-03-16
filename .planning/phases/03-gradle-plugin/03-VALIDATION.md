---
phase: 3
slug: gradle-plugin
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-03-15
---

# Phase 3 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5.11.4 |
| **Config file** | none — `useJUnitPlatform()` in `tasks.test` |
| **Quick run command** | `./gradlew :gradle-plugin:test` |
| **Full suite command** | `./gradlew :gradle-plugin:test` |
| **Estimated runtime** | ~30 seconds |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew :gradle-plugin:test`
- **After every plan wave:** Run `./gradlew :gradle-plugin:test`
- **Before `/gsd:verify-work`:** Full suite must be green + `./gradlew :gradle-plugin:publishToMavenLocal` succeeds
- **Max feedback latency:** 30 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 03-01-01 | 01 | 1 | CODEGEN-01 | functional (TestKit) | `./gradlew :gradle-plugin:test` | ❌ W0 | ⬜ pending |
| 03-01-02 | 01 | 1 | CODEGEN-01 | functional (TestKit) | `./gradlew :gradle-plugin:test` | ❌ W0 | ⬜ pending |
| 03-01-03 | 01 | 1 | CODEGEN-01 | smoke | `./gradlew :gradle-plugin:publishToMavenLocal` | ❌ W0 | ⬜ pending |
| 03-01-04 | 01 | 1 | TEST-02 | functional (TestKit) | `./gradlew :gradle-plugin:test` | ❌ W0 | ⬜ pending |
| 03-01-05 | 01 | 1 | TEST-02 | functional (TestKit) | `./gradlew :gradle-plugin:test` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `gradle-plugin/src/test/kotlin/com/example/declarativejooq/gradle/DeclarativeJooqPluginFunctionalTest.kt` — covers CODEGEN-01, TEST-02
- [ ] Test fixture project files written by test code to `@TempDir` — no separate file needed
- [ ] No new test framework install needed — `gradleTestKit()` + `junit-jupiter:5.11.4` already used in sibling modules

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
