---
phase: 04-edge-cases-and-integration
plan: 02
subsystem: integration-tests
tags: [jooq, kotlin, testcontainers, postgres, integration-test, codegen, pipeline]

# Dependency graph
requires:
  - phase: 04-edge-cases-and-integration
    plan: 01
    provides: self-referential FK and multi-FK support in codegen + runtime
provides:
  - End-to-end integration test module against real Postgres via Testcontainers
  - Full pipeline validation: TestSchema jOOQ classes -> CodeGenerator -> KotlinCompilation -> execute() -> DB assertions
  - All FK scenarios validated against Postgres: root, nested, multiple same-type, self-referential, multi-FK
affects:
  - release readiness (all scenarios proven against real Postgres)

# Tech tracking
tech-stack:
  added:
    - Testcontainers 1.20.6 (PostgreSQLContainer)
    - org.postgresql:postgresql:42.7.4 driver
    - org.jooq:jooq-codegen:3.19.16 (for potential programmatic codegen)
    - org.slf4j:slf4j-simple:2.0.16 (test log visibility)
  patterns:
    - Shared Postgres container per-class (@TestInstance(PER_CLASS) + @Container @JvmStatic)
    - IntegrationHarness compiled alongside generated DSL in same KotlinCompilation classloader
    - Harness invoked via reflection at test/classloader boundary
    - TRUNCATE ... CASCADE in @BeforeEach for table isolation
    - Docker Desktop macOS compatibility: docker.raw.sock + api.version=1.44 + TESTCONTAINERS_RYUK_DISABLED

key-files:
  created:
    - integration-tests/build.gradle.kts
    - integration-tests/src/test/kotlin/com/example/declarativejooq/integration/FullPipelineTest.kt
    - integration-tests/src/test/resources/testcontainers.properties
    - .planning/phases/04-edge-cases-and-integration/04-02-SUMMARY.md
  modified:
    - settings.gradle.kts

key-decisions:
  - "Reuse TestSchema jOOQ classes from dsl-runtime/build/classes/kotlin/test rather than programmatic jOOQ codegen against Postgres — avoids javac/jOOQ-codegen complexity while still proving Postgres SQL dialect works"
  - "Testcontainers 1.20.x requires docker.raw.sock on macOS Docker Desktop — /var/run/docker.sock is a docker-cli proxy returning 400; docker.raw.sock is the real daemon"
  - "api.version=1.44 system property needed — shaded docker-java in Testcontainers defaults to API v1.32 which Docker Desktop 29.x rejects"
  - "TESTCONTAINERS_RYUK_DISABLED=true avoids Ryuk socket-mount failure (Docker Desktop VM cannot mount host socket path)"
  - "Build.gradle.kts detects real socket path dynamically (~/Library/Containers/com.docker.docker/Data/docker.raw.sock) with fallback chain"

patterns-established:
  - "Full pipeline test pattern: TestSchema classes -> CodeGenerator.generateSource() -> KotlinCompilation with harness -> reflection invocation -> SQL assertions"
  - "Docker Desktop macOS compatibility pattern: detect raw.sock, set api.version + disable Ryuk in Gradle test task environment"

requirements-completed: [TEST-03]

# Metrics
duration: 9min
completed: 2026-03-16
---

# Phase 4 Plan 2: Full Pipeline Integration Tests Summary

**Testcontainers Postgres integration tests validating the complete pipeline end-to-end: self-ref FK two-pass insert, multi-FK named builders, and FK chains all pass against real Postgres 16**

## Performance

- **Duration:** ~9 min
- **Started:** 2026-03-16T23:27:08Z
- **Completed:** 2026-03-16T23:36:11Z
- **Tasks:** 2
- **Files modified:** 5

## Accomplishments

- Created `:integration-tests` Gradle module with Testcontainers, jOOQ codegen, postgresql driver, kotlin-compile-testing
- Registered module in settings.gradle.kts
- Created `FullPipelineTest.kt` with 6 comprehensive tests against real Postgres 16 (Alpine)
- Shared Postgres container per-class for speed (single container for all 6 tests)
- IntegrationHarness compiled alongside generated DSL in same KotlinCompilation classloader
- `@BeforeEach` truncates all tables with CASCADE for full isolation
- All 6 tests pass: rootAndNestedRecords, multipleSameTypeRecords, multiLevelNesting, selfReferentialFkTwoPassInsert, multipleFksToSameTable, mixedGraph
- Resolved Docker Desktop macOS compatibility issues with Testcontainers (see Deviations)

## Task Commits

1. **Task 1: Integration test module scaffold** - `0832be2` (feat)
2. **Task 2: Full pipeline integration tests** - `331e155` (feat)

## Files Created/Modified

- `settings.gradle.kts` — Added `:integration-tests` to include list
- `integration-tests/build.gradle.kts` — New module with all dependencies + Docker detection
- `integration-tests/src/test/kotlin/com/example/declarativejooq/integration/FullPipelineTest.kt` — 6 end-to-end tests
- `integration-tests/src/test/resources/testcontainers.properties` — EnvironmentAndSystemProperty strategy

## Decisions Made

- **Reuse TestSchema classes instead of programmatic jOOQ codegen**: The plan suggested two approaches; the preferred one (reuse dsl-runtime test classes + Postgres backend) was chosen. This avoids javac compilation of jOOQ-generated Java sources while still proving the Postgres SQL dialect.
- **Docker Desktop macOS: docker.raw.sock required**: `/var/run/docker.sock` on macOS Docker Desktop is a docker-cli proxy that returns empty 400 responses. The real Docker daemon socket is `~/Library/Containers/com.docker.docker/Data/docker.raw.sock`. Build.gradle.kts detects this dynamically.
- **api.version=1.44 required**: The shaded docker-java in Testcontainers 1.20.x uses API version 1.32 by default; Docker Desktop 29.x requires minimum 1.44. Setting `api.version` system property overrides the default.
- **TESTCONTAINERS_RYUK_DISABLED=true**: Ryuk attempts to mount the docker socket path into a container, but Docker Desktop's Linux VM cannot resolve macOS host paths. Disabling Ryuk is safe for test environments (containers cleaned up on JVM exit).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Docker Desktop macOS: /var/run/docker.sock is not a real Docker daemon socket**
- **Found during:** Task 2 verification
- **Issue:** Testcontainers could not find a valid Docker environment. `/var/run/docker.sock` is a symlink to `~/.docker/run/docker.sock` which is a docker-cli proxy returning 400 empty responses. All strategies (UnixSocket, DockerDesktop, EnvironmentAndSystemProperty) failed.
- **Fix:** Detect `~/Library/Containers/com.docker.docker/Data/docker.raw.sock` (real daemon socket), set `docker.host` system property and `DOCKER_HOST` env var. Also set `api.version=1.44` (shaded docker-java defaults to 1.32, rejected by Docker Desktop 29.x). Set `TESTCONTAINERS_RYUK_DISABLED=true` to avoid Ryuk socket-mount failure.
- **Files modified:** `integration-tests/build.gradle.kts`, `integration-tests/src/test/resources/testcontainers.properties`
- **Commit:** 331e155

**2. [Rule 3 - Blocking] SLF4J NOP logger hid Testcontainers diagnostic output**
- **Found during:** Task 2 debugging
- **Issue:** Testcontainers logs all strategy attempts to SLF4J; NOP logger suppressed them, making diagnosis impossible.
- **Fix:** Added `org.slf4j:slf4j-simple:2.0.16` to testImplementation to reveal strategy failure details.
- **Files modified:** `integration-tests/build.gradle.kts`
- **Commit:** 331e155 (included in Task 2 commit)

## Issues Encountered

- Docker Desktop macOS compatibility required multi-step debugging: SLF4J logging to see strategies, then identifying docker.raw.sock vs docker-cli.sock distinction, then api.version mismatch, then Ryuk socket-mount error. Each discovered and resolved in sequence.

## Next Phase Readiness

- All edge cases (self-ref FK, multi-FK) proven end-to-end against real Postgres 16
- Phase 4 complete — full pipeline validated from TestSchema jOOQ classes through CodeGenerator to DB assertions

---
*Phase: 04-edge-cases-and-integration*
*Completed: 2026-03-16*
