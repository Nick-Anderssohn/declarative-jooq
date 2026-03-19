---
gsd_state_version: 1.0
milestone: v1.0
milestone_name: Maven Central Release
status: unknown
stopped_at: Completed 13-01-PLAN.md
last_updated: "2026-03-19T03:12:37.124Z"
progress:
  total_phases: 5
  completed_phases: 4
  total_plans: 5
  completed_plans: 5
---

# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-03-17)

**Core value:** Eliminate boilerplate and manual FK wiring when creating test data — declare what records you want and how they relate, and the library handles insertion order, FK assignment, and result assembly.
**Current focus:** Phase 13 — readme-and-docs

## Current Position

Phase: 13 (readme-and-docs) — EXECUTING
Plan: 1 of 1

## Performance Metrics

**Velocity:**

- Total plans completed: 0 (v1.0)
- Average duration: —
- Total execution time: —

**By Phase:**

| Phase | Plans | Total | Avg/Plan |
|-------|-------|-------|----------|
| - | - | - | - |

*Updated after each plan completion*
| Phase 10 P01 | 1 | 1 tasks | 1 files |
| Phase 11 P01 | 15 | 2 tasks | 6 files |
| Phase 11 P02 | 20 | 2 tasks | 3 files |
| Phase 12 P01 | 2 | 2 tasks | 2 files |
| Phase 13 P01 | 2 | 2 tasks | 2 files |

## Accumulated Context

### Decisions

All v0.1/v0.2/v0.3 decisions archived in PROJECT.md Key Decisions table.

Key v1.0 context:

- Sonatype namespace `com.nickanderssohn` is already verified in the Central Portal (head start on Phase 10)
- Group ID is `com.nickanderssohn` (decision locked — all POM metadata and README coordinates use this)
- OSSRH is dead — use `SonatypeHost.CENTRAL_PORTAL` via vanniktech plugin only
- First publish should be manual-release (not auto-release) to allow portal inspection before going live
- [Phase 10]: Use gpg --clearsign as GPG smoke test (simpler than Gradle task, works before Phase 11 build config)
- [Phase 10]: Document in-memory gradle.properties variant as preferred since Phase 11 vanniktech config uses signing.key
- [Phase 11]: Use publishToMavenCentral(automaticRelease = false) — vanniktech 0.35.0 removed SonatypeHost enum; Central Portal is now the default
- [Phase 11]: Artifact IDs use declarative-jooq- prefix for Maven Central discoverability
- [Phase 11]: Signing guard: tasks.withType<Sign>().configureEach { enabled = findProperty('signingInMemoryKey') \!= null } — publishToMavenLocal works without credentials
- [Phase 11]: POM inceptionYear corrected to 2026; developer/SCM URLs corrected to github.com/Nick-Anderssohn (capital N-A) — fix committed in 57dd0c9
- [Phase 12]: ci.yml and publish.yml are separate files with zero overlap — ci.yml never publishes, publish.yml never runs on PRs or branch pushes
- [Phase 12]: publish.yml uses publishToMavenCentral (not publishAndReleaseToMavenCentral) to preserve manual portal release (automaticRelease=false in all 3 modules)
- [Phase 13]: Remove mavenLocal instructions entirely — Maven Central is now the sole distribution channel
- [Phase 13]: Version 1.0.0 used in all README coordinate examples to match the published artifact
- [Phase 13]: Gradle 8.13 in Tech Stack table to reflect actual version used (upgraded in Phase 11)

### Pending Todos

None.

### Blockers/Concerns

None.

## Session Continuity

Last session: 2026-03-19T03:12:37.122Z
Stopped at: Completed 13-01-PLAN.md
Resume file: None
