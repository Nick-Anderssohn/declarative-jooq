---
phase: 12-ci-workflows
plan: 01
subsystem: infra
tags: [github-actions, ci, gradle, testcontainers, maven-central, vanniktech, publishing]

requires:
  - phase: 11-publishing
    provides: vanniktech publishToMavenCentral task configured with automaticRelease=false and ORG_GRADLE_PROJECT_ credential pattern

provides:
  - ci.yml workflow — PR and push-to-main build gate running ./gradlew build with Testcontainers
  - publish.yml workflow — v* tag-triggered Maven Central publish via publishToMavenCentral

affects:
  - any future phase involving CI or automated releases

tech-stack:
  added: [github-actions, actions/checkout@v4, actions/setup-java@v4, gradle/actions/setup-gradle@v5]
  patterns: [ORG_GRADLE_PROJECT_ env var injection for Gradle credential passing, separate ci/publish workflows with zero overlap]

key-files:
  created:
    - .github/workflows/ci.yml
    - .github/workflows/publish.yml
  modified: []

key-decisions:
  - "ci.yml and publish.yml are kept as completely separate files with zero overlap — ci.yml never publishes, publish.yml never runs on PRs or branch pushes"
  - "publish.yml uses publishToMavenCentral (not publishAndReleaseToMavenCentral) because automaticRelease=false is set across all 3 modules — manual portal release is preserved"
  - "TESTCONTAINERS_RYUK_DISABLED=true is set in CI to match local tested configuration; ubuntu-latest has Docker pre-installed so no Docker setup step is needed"
  - "ORG_GRADLE_PROJECT_ prefix maps all 5 GitHub Secrets to Gradle project properties directly — no temp files, no -P flags needed"

patterns-established:
  - "CI workflow pattern: pull_request trigger + push branches: [main], ubuntu-latest, setup-java@v4 + setup-gradle@v5, single ./gradlew build command"
  - "Publish workflow pattern: push tags: [v*] only (no branches key), ORG_GRADLE_PROJECT_ env vars for credentials, publishToMavenCentral task"

requirements-completed: [CI-01, CI-02]

duration: 2min
completed: 2026-03-19
---

# Phase 12 Plan 01: CI Workflows Summary

**Two GitHub Actions workflows providing automated build gating on PRs and tag-triggered Maven Central publishing via vanniktech publishToMavenCentral with ORG_GRADLE_PROJECT_ secret injection**

## Performance

- **Duration:** 2 min
- **Started:** 2026-03-19T02:31:18Z
- **Completed:** 2026-03-19T02:33:00Z
- **Tasks:** 2
- **Files modified:** 2

## Accomplishments
- ci.yml created — triggers on all pull_request events and pushes to main, runs ./gradlew build with TESTCONTAINERS_RYUK_DISABLED=true, no publishing logic
- publish.yml created — triggers only on v* tag pushes, runs publishToMavenCentral with all 5 credentials mapped via ORG_GRADLE_PROJECT_ env vars, no branch/PR triggers
- All phase-level verification checks passed: pull_request in ci.yml, no branches in publish.yml, correct task name, no publish logic in ci.yml

## Task Commits

Each task was committed atomically:

1. **Task 1: Create CI build gate workflow** - `c8566cf` (feat)
2. **Task 2: Create publish workflow for tag-triggered Maven Central release** - `f42abdf` (feat)

**Plan metadata:** (docs commit follows)

## Files Created/Modified
- `.github/workflows/ci.yml` - PR and push-to-main build gate; runs ./gradlew build with Testcontainers support
- `.github/workflows/publish.yml` - v* tag-triggered publish; runs publishToMavenCentral with 5 GitHub Secret mappings

## Decisions Made
- Kept ci.yml and publish.yml as fully separate files with zero overlap — cleanest separation of concerns, no conditional logic needed
- Used publishToMavenCentral (not publishAndReleaseToMavenCentral) to preserve the manual-release decision from Phase 11
- Set TESTCONTAINERS_RYUK_DISABLED=true in CI for consistency with the local tested configuration; ubuntu-latest Docker pre-installation makes no Docker setup step necessary
- No `branches:` key in publish.yml push trigger — only `tags: ['v*']` — prevents accidental branch-push publishing

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None.

## User Setup Required

Before the publish workflow can succeed, GitHub repository secrets must be configured:
- `SONATYPE_USERNAME` — Sonatype Central Portal user token username
- `SONATYPE_PASSWORD` — Sonatype Central Portal user token password
- `SIGNING_KEY` — base64-encoded ASCII-armored GPG signing subkey
- `SIGNING_KEY_ID` — last 8 hex chars of subkey fingerprint
- `SIGNING_PASSWORD` — GPG key passphrase

See `SETUP.md` for the exact commands to generate and encode these values.

## Next Phase Readiness

- Both workflow files are in place and ready for GitHub to pick up
- The first CI run will trigger automatically on the next PR or push to main
- The first publish run will trigger when a v* tag is pushed (e.g., `git tag -a v1.0.0 -m "Release 1.0.0" && git push origin v1.0.0`)
- Phase 12 is the final planned phase — the project is now feature-complete for v1.0

## Self-Check: PASSED

All files verified present. All task commits verified in git history.

---
*Phase: 12-ci-workflows*
*Completed: 2026-03-19*
