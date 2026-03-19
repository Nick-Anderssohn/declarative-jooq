---
gsd_state_version: 1.0
milestone: v1.0
milestone_name: Maven Central Release
status: unknown
stopped_at: Completed 11-01-PLAN.md
last_updated: "2026-03-19T01:10:11.470Z"
progress:
  total_phases: 5
  completed_phases: 1
  total_plans: 3
  completed_plans: 2
---

# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-03-17)

**Core value:** Eliminate boilerplate and manual FK wiring when creating test data — declare what records you want and how they relate, and the library handles insertion order, FK assignment, and result assembly.
**Current focus:** Phase 11 — publishing-configuration

## Current Position

Phase: 11 (publishing-configuration) — EXECUTING
Plan: 1 of 2

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

### Pending Todos

None.

### Blockers/Concerns

None.

## Session Continuity

Last session: 2026-03-19T01:10:11.469Z
Stopped at: Completed 11-01-PLAN.md
Resume file: None
