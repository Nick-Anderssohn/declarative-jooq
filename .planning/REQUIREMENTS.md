# Requirements: declarative-jooq

**Defined:** 2026-03-17
**Milestone:** v1.0 Maven Central Release
**Core Value:** Eliminate boilerplate and manual FK wiring when creating test data — declare what records you want and how they relate, and the library handles insertion order, FK assignment, and result assembly.

## v1.0 Requirements

### Setup

- [x] **SETUP-01**: Developer can follow a SETUP.md guide to generate a GPG key, export the signing subkey correctly, and avoid common CI signing failures
- [x] **SETUP-02**: Developer can follow SETUP.md to generate a Sonatype Portal user token and configure all required GitHub Secrets (SONATYPE_USERNAME, SONATYPE_PASSWORD, SIGNING_KEY, SIGNING_KEY_ID, SIGNING_PASSWORD)

### Publishing

- [x] **PUB-01**: Library version is bumped to `1.0.0` (SNAPSHOT removed, Maven Central ready)
- [x] **PUB-02**: All 3 modules (dsl-runtime, codegen, gradle-plugin) are configured with the vanniktech maven-publish plugin and complete POM metadata (name, description, url, licenses, developers, scm)
- [x] **PUB-03**: GPG signing is configured via in-memory keys on all 3 modules, with a guard so local builds without signing credentials still work
- [x] **PUB-04**: `./gradlew publishToMavenLocal` succeeds for all 3 modules, producing main JAR, sources JAR, Javadoc JAR, `.asc` signatures, and valid POM

### CI

- [x] **CI-01**: `ci.yml` GitHub Actions workflow runs `./gradlew build` (including Testcontainers integration tests) on every PR and push to `main`
- [x] **CI-02**: `publish.yml` GitHub Actions workflow publishes all 3 modules to Maven Central when a `v*` tag is pushed, using GitHub Secrets for credentials

### Documentation

- [x] **DOCS-01**: README.md is rewritten with Maven Central dependency coordinates, `pluginManagement` setup block, DSL usage guide, and a clear value proposition for jOOQ + Kotlin test data
- [x] **DOCS-02**: README header includes a GitHub Actions build status badge and Maven Central version badge
- [x] **DOCS-03**: `CHANGELOG.md` documents v1.0.0 as the first public release

### Configuration

- [x] **CONFIG-01**: `DeclarativeJooqExtension` exposes `outputDir: DirectoryProperty` (default: `build/generated/declarative-jooq`) and `sourceSet: Property<String>` (default: `"test"`), both wired into plugin and task via `convention()`
- [x] **CONFIG-02**: Functional tests verify custom `outputDir` produces output at the configured path and custom `sourceSet` wires into the specified source set
- [x] **CONFIG-03**: `examples/todo-list` uses version `1.0.0` for plugin and runtime dependency, and demonstrates `outputDir` in its `declarativeJooq` block
- [x] **CONFIG-04**: README "Configure the extension" section documents `outputDir` and `sourceSet` with defaults and usage guidance

### Validation

- [ ] **VALID-01**: A first-publish checklist exists documenting what to verify after tagging v1.0.0: portal UI artifacts, consumer resolution, and plugin marker resolution

## Future Requirements

### Gradle Plugin Portal

- **PLUGIN-01**: Gradle plugin published to the Gradle Plugin Portal (allows `plugins {}` without `pluginManagement` mavenCentral block)

### Extended Documentation

- **DOCS-04**: KDoc published to GitHub Pages via Dokka
- **DOCS-05**: CONTRIBUTING.md and GitHub issue templates

## Out of Scope

| Feature | Reason |
|---------|--------|
| Gradle Plugin Portal registration | Requires separate account + workflow; users can configure `pluginManagement { mavenCentral() }` |
| Dependabot / Renovate | Not blocking v1.0 release |
| Multi-column composite FK support | Rare, high complexity — deferred from v0.x |
| Production record creation | Test data tool only |

## Traceability

| Requirement | Phase | Status |
|-------------|-------|--------|
| SETUP-01 | Phase 10 | Complete |
| SETUP-02 | Phase 10 | Complete |
| PUB-01 | Phase 11 | Complete |
| PUB-02 | Phase 11 | Complete |
| PUB-03 | Phase 11 | Complete |
| PUB-04 | Phase 11 | Complete |
| CI-01 | Phase 12 | Complete |
| CI-02 | Phase 12 | Complete |
| DOCS-01 | Phase 13 | Complete |
| DOCS-02 | Phase 13 | Complete |
| DOCS-03 | Phase 13 | Complete |
| CONFIG-01 | Phase 13.1 | Complete |
| CONFIG-02 | Phase 13.1 | Complete |
| CONFIG-03 | Phase 13.1 | Complete |
| CONFIG-04 | Phase 13.1 | Complete |
| VALID-01 | Phase 14 | Pending |

**Coverage:**
- v1.0 requirements: 16 total
- Mapped to phases: 16
- Unmapped: 0

---
*Requirements defined: 2026-03-17*
*Last updated: 2026-03-18 after Phase 13.1 requirement definition*
