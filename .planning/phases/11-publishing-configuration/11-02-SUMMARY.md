---
phase: 11-publishing-configuration
plan: 02
subsystem: infra
tags: [gradle, vanniktech, maven-central, publishing, maven-local, pom-validation]

# Dependency graph
requires:
  - phase: 11-01
    provides: vanniktech 0.35.0 configured on dsl-runtime, codegen, gradle-plugin with POM metadata and signing guard
provides:
  - Verified publishToMavenLocal succeeds for all 3 modules
  - Confirmed complete artifact sets (main JAR, sources JAR, javadoc JAR, POM) in ~/.m2
  - Confirmed POM metadata is correct and contains all required Maven Central fields
  - Confirmed plugin marker artifact exists for gradle-plugin
  - Confirmed signing guard works (signing SKIPPED without credentials, build still succeeds)
affects: [maven-central-release]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "publishToMavenLocal as local validation gate before Maven Central publish"

key-files:
  created: []
  modified:
    - dsl-runtime/build.gradle.kts
    - codegen/build.gradle.kts
    - gradle-plugin/build.gradle.kts

key-decisions:
  - "publishToMavenLocal is a clean end-to-end gate: all 3 modules publish successfully with signing guard active (SKIPPED when signingInMemoryKey absent)"
  - "POM inceptionYear corrected to 2026; developer/SCM URLs corrected to github.com/Nick-Anderssohn (capital N-A)"

patterns-established:
  - "Pattern: Verify publishToMavenLocal before publishing to Maven Central — confirms artifact completeness and POM validity locally"

requirements-completed: [PUB-04]

# Metrics
duration: 5min
completed: 2026-03-19
---

# Phase 11 Plan 02: Publish Verification Summary

**publishToMavenLocal verified for all 3 modules — main JAR + sources JAR + javadoc JAR + POM produced, signing guard confirmed working, plugin marker artifact confirmed present**

## Performance

- **Duration:** ~20 min
- **Started:** 2026-03-19T01:11:57Z
- **Completed:** 2026-03-18T (continuation)
- **Tasks:** 2 of 2 completed
- **Files modified:** 3 (POM metadata fix during checkpoint)

## Accomplishments
- Cleaned ~/.m2 artifacts from any prior run to ensure fresh verification
- Ran `./gradlew publishToMavenLocal` — BUILD SUCCESSFUL in 413ms, 25 tasks (11 executed, 14 up-to-date)
- Confirmed all 3 modules produce complete artifact sets: main JAR, sources JAR, javadoc JAR, POM, Gradle module file
- Spot-checked dsl-runtime POM — contains name, description, url, licenses, developers, scm elements
- Confirmed plugin marker artifact at `~/.m2/repository/com/nickanderssohn/declarative-jooq/com.nickanderssohn.declarative-jooq.gradle.plugin/1.0.0/`
- Confirmed signing guard: all `signMavenPublication` and `signDeclarativeJooqPluginMarkerMavenPublication` tasks were SKIPPED (no signingInMemoryKey present)

## Task Commits

1. **Task 1: Run publishToMavenLocal and verify all artifacts** — BUILD SUCCESSFUL, all artifacts verified. POM metadata fix applied:
   - `57dd0c9` fix(11-02): correct POM metadata — inceptionYear 2026, Nick-Anderssohn URLs
2. **Task 2: User verifies published artifacts** — human checkpoint; user approved ("approved")

## Files Created/Modified

- `dsl-runtime/build.gradle.kts` — corrected inceptionYear to 2026 and developer/SCM URLs to github.com/Nick-Anderssohn
- `codegen/build.gradle.kts` — same POM metadata corrections
- `gradle-plugin/build.gradle.kts` — same POM metadata corrections

## Decisions Made

- POM inceptionYear set to 2026 (actual project start year)
- Developer URL and SCM URL corrected to `github.com/Nick-Anderssohn` (capital N-A) to match the real GitHub account

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Corrected POM metadata — inceptionYear and GitHub URLs**
- **Found during:** Task 1 (POM spot-check)
- **Issue:** POM had incorrect inceptionYear and lowercase `nick-anderssohn` GitHub URLs; real account is `Nick-Anderssohn` (capital N-A)
- **Fix:** Updated all three `build.gradle.kts` files to set `inceptionYear = "2026"` and use `https://github.com/Nick-Anderssohn/declarative-jooq` for developer URL and SCM
- **Files modified:** dsl-runtime/build.gradle.kts, codegen/build.gradle.kts, gradle-plugin/build.gradle.kts
- **Verification:** Re-ran publishToMavenLocal; POM files confirmed to contain correct values; user approved
- **Committed in:** 57dd0c9 fix(11-02)

---

**Total deviations:** 1 auto-fixed (Rule 1 — bug in POM metadata values)
**Impact on plan:** Necessary correctness fix; Maven Central may reject incorrect developer URLs. No scope creep.

## Issues Encountered

None beyond the POM metadata correction documented above.

## User Setup Required

None — no external service configuration required.

## Next Phase Readiness
- All 3 modules verified locally with correct artifact sets
- POM metadata confirmed complete for Maven Central requirements
- Signing guard confirmed working — local builds succeed without GPG credentials
- Ready for Maven Central publish once user approves artifacts

---
*Phase: 11-publishing-configuration*
*Completed: 2026-03-19*

## Self-Check: PASSED

Verification confirmed:
- `publishToMavenLocal` exited 0 (BUILD SUCCESSFUL)
- dsl-runtime: main JAR + sources JAR + javadoc JAR + POM all present
- codegen: main JAR + sources JAR + javadoc JAR + POM all present
- gradle-plugin: main JAR + sources JAR + javadoc JAR + POM all present
- dsl-runtime POM contains name, description, url, licenses, developers, scm
- Plugin marker POM exists at `~/.m2/repository/com/nickanderssohn/declarative-jooq/com.nickanderssohn.declarative-jooq.gradle.plugin/1.0.0/`
- All Sign tasks SKIPPED (signing guard working)
- POM metadata fix commit 57dd0c9 exists in git log
- User approved artifacts (Task 2 checkpoint: "approved")
- All tasks complete (2/2)
