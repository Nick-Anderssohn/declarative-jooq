---
phase: 11-publishing-configuration
plan: 01
subsystem: infra
tags: [gradle, vanniktech, maven-central, publishing, gpg-signing, pom-metadata]

# Dependency graph
requires: []
provides:
  - vanniktech 0.35.0 configured on dsl-runtime, codegen, gradle-plugin modules
  - Complete POM metadata (name, description, url, licenses, developers, scm) on all 3 modules
  - In-memory GPG signing with signing guard (skips when signingInMemoryKey absent)
  - Version bumped to 1.0.0 (no SNAPSHOT) in root build.gradle.kts
  - Gradle wrapper upgraded to 8.13 (required by vanniktech 0.35.0)
  - SETUP.md updated with vanniktech property names (signingInMemoryKey, mavenCentralUsername/Password)
affects: [11-02-publish-workflow]

# Tech tracking
tech-stack:
  added:
    - "com.vanniktech:gradle-maven-publish-plugin:0.35.0 (Maven Central publishing with auto sources/javadoc/signing)"
  patterns:
    - "Per-module vanniktech plugin application (NOT in root subprojects block)"
    - "Signing guard: tasks.withType<Sign>().configureEach { enabled = findProperty('signingInMemoryKey') != null }"
    - "version derived from project.version.toString() to read from root allprojects block"

key-files:
  created: []
  modified:
    - build.gradle.kts
    - gradle/wrapper/gradle-wrapper.properties
    - dsl-runtime/build.gradle.kts
    - codegen/build.gradle.kts
    - gradle-plugin/build.gradle.kts
    - SETUP.md

key-decisions:
  - "Use publishToMavenCentral(automaticRelease = false) — no SonatypeHost enum parameter in vanniktech 0.35.0 API"
  - "Sign guard uses findProperty('signingInMemoryKey') != null — publishToMavenLocal works without credentials"
  - "Artifact IDs prefixed with project name: declarative-jooq-dsl-runtime, declarative-jooq-codegen, declarative-jooq-gradle-plugin"

patterns-established:
  - "Pattern: Vanniktech per-module publishing — apply id('com.vanniktech.maven.publish') in each publishable module, never in root"
  - "Pattern: Signing guard — tasks.withType<Sign>().configureEach { enabled = findProperty('signingInMemoryKey') != null } after mavenPublishing block"

requirements-completed: [PUB-01, PUB-02, PUB-03]

# Metrics
duration: 15min
completed: 2026-03-18
---

# Phase 11 Plan 01: Publishing Configuration Summary

**vanniktech 0.35.0 configured on all 3 modules with complete Maven Central POM metadata, in-memory signing guard, version 1.0.0, and Gradle wrapper 8.13**

## Performance

- **Duration:** 15 min
- **Started:** 2026-03-18T00:00:00Z
- **Completed:** 2026-03-18T00:15:00Z
- **Tasks:** 2
- **Files modified:** 6

## Accomplishments
- Upgraded Gradle wrapper from 8.12 to 8.13 (required by vanniktech 0.35.0)
- Bumped version from 0.1.0-SNAPSHOT to 1.0.0 in root build.gradle.kts
- Configured vanniktech 0.35.0 on dsl-runtime, codegen, and gradle-plugin with full POM metadata
- Added signing guard so publishToMavenLocal succeeds without GPG credentials
- Updated SETUP.md with correct vanniktech property names (signingInMemoryKey, mavenCentralUsername/Password)
- Validated all 3 modules via publishToMavenLocal — BUILD SUCCESSFUL, signing SKIPPED (no credentials, guard working)

## Task Commits

Each task was committed atomically:

1. **Task 1: Upgrade Gradle wrapper, bump version, update SETUP.md property names** - `02c50ce` (chore)
2. **Task 2: Configure vanniktech publishing on all 3 modules with POM metadata and signing guard** - `f886fb0` (feat)

## Files Created/Modified
- `build.gradle.kts` - Version changed from 0.1.0-SNAPSHOT to 1.0.0
- `gradle/wrapper/gradle-wrapper.properties` - Gradle 8.12 upgraded to 8.13
- `dsl-runtime/build.gradle.kts` - maven-publish replaced with vanniktech 0.35.0 + full POM + signing guard
- `codegen/build.gradle.kts` - maven-publish replaced with vanniktech 0.35.0 + full POM + signing guard
- `gradle-plugin/build.gradle.kts` - maven-publish replaced with vanniktech 0.35.0 + full POM + signing guard
- `SETUP.md` - Section 5 updated with vanniktech property names; keyring alternative noted as incompatible

## Decisions Made
- Use `publishToMavenCentral(automaticRelease = false)` — the vanniktech 0.35.0 API removed the `SonatypeHost` enum parameter; `publishToMavenCentral()` now defaults to Central Portal. This required a Rule 1 auto-fix (see Deviations section).
- Artifact IDs use `declarative-jooq-` prefix for Maven Central discoverability.
- `project.version.toString()` used in coordinates() so version stays in sync with root build.gradle.kts.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed wrong API call: publishToMavenCentral with SonatypeHost enum**
- **Found during:** Task 2 (vanniktech publishing configuration)
- **Issue:** The plan prescribed `publishToMavenCentral(com.vanniktech.maven.publish.SonatypeHost.CENTRAL_PORTAL, automaticRelease = false)` but vanniktech 0.35.0 removed the `SonatypeHost` class from the API. The build failed with "Unresolved reference: SonatypeHost". Research file (11-RESEARCH.md) documents the correct API as `publishToMavenCentral(automaticRelease = false)`.
- **Fix:** Replaced the SonatypeHost call with `publishToMavenCentral(automaticRelease = false)` in all 3 module build files.
- **Files modified:** dsl-runtime/build.gradle.kts, codegen/build.gradle.kts, gradle-plugin/build.gradle.kts
- **Verification:** `./gradlew :dsl-runtime:publishToMavenLocal :codegen:publishToMavenLocal :gradle-plugin:publishToMavenLocal` exits 0 — BUILD SUCCESSFUL
- **Committed in:** f886fb0 (Task 2 commit)

---

**Total deviations:** 1 auto-fixed (Rule 1 - Bug)
**Impact on plan:** Auto-fix essential for correctness. The PLAN.md contained an outdated API call that doesn't exist in vanniktech 0.35.0. The research file had the correct pattern. No scope creep.

## Issues Encountered
- vanniktech 0.35.0 API changed from the pattern in PLAN.md: `SonatypeHost` enum was removed; `publishToMavenCentral()` now defaults to Central Portal without any enum argument. Fixed immediately via Rule 1 (wrong API = bug).

## User Setup Required
None — no external service configuration required during this plan. Credentials setup was handled in Phase 10 via SETUP.md.

## Next Phase Readiness
- All 3 modules are ready to publish to Maven Central with correct POM metadata
- Signing guard ensures local/CI builds without credentials still succeed
- Ready for Phase 11 Plan 02: CI publish workflow (GitHub Actions)

---
*Phase: 11-publishing-configuration*
*Completed: 2026-03-18*

## Self-Check: PASSED

All files found, all commits verified.
