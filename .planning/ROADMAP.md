# Roadmap: declarative-jooq

## Milestones

- ✅ **v0.1** — Phases 1-4 (shipped 2026-03-16)
- ✅ **v0.2 Natural DSL Naming & Placeholders** — Phases 5-6 (shipped 2026-03-17)
- ✅ **v0.3 Richer Example** — Phases 7-9 (shipped 2026-03-18)
- 🚧 **v1.0 Maven Central Release** — Phases 10-14 (in progress)

## Phases

<details>
<summary>✅ v0.1 (Phases 1-4) — SHIPPED 2026-03-16</summary>

- [x] Phase 1: Runtime DSL Foundation (3/3 plans) — completed 2026-03-15
- [x] Phase 2: Code Generation Engine (3/3 plans) — completed 2026-03-16
- [x] Phase 3: Gradle Plugin (2/2 plans) — completed 2026-03-16
- [x] Phase 4: Edge Cases and Integration (2/2 plans) — completed 2026-03-16

</details>

<details>
<summary>✅ v0.2 Natural DSL Naming & Placeholders (Phases 5-6) — SHIPPED 2026-03-17</summary>

- [x] Phase 5: Child-Table-Named Builder Functions (2/2 plans) — completed 2026-03-17
- [x] Phase 6: Placeholder Objects (3/3 plans) — completed 2026-03-17

</details>

<details>
<summary>✅ v0.3 Richer Example (Phases 7-9) — SHIPPED 2026-03-18</summary>

- [x] Phase 7: Example Schema (1/1 plan) — completed 2026-03-18
- [x] Phase 8: Example API (2/2 plans) — completed 2026-03-18
- [x] Phase 9: Example Tests (2/2 plans) — completed 2026-03-18

</details>

### 🚧 v1.0 Maven Central Release (In Progress)

**Milestone Goal:** Publish declarative-jooq to Maven Central with GitHub CI automation and a polished README that positions the library as the go-to test data tool for jOOQ + Kotlin projects.

- [x] **Phase 10: Credentials Setup** - GPG key, Sonatype Portal token, and all GitHub Secrets configured (completed 2026-03-18)
- [x] **Phase 11: Publishing Configuration** - All 3 modules publishable to mavenLocal with correct JARs, POM, and signatures (completed 2026-03-19)
- [x] **Phase 12: CI Workflows** - GitHub Actions CI (build/test on PR) and publish (on v* tag) wired up (completed 2026-03-19)
- [x] **Phase 13: README and Docs** - README rewritten with Maven Central coordinates, usage guide, badges, and CHANGELOG (completed 2026-03-19)
- [x] **Phase 13.1: Configurable DSL output directory and todo-list example update** - outputDir/sourceSet extension properties, todo-list version bump, README docs (completed 2026-03-19)
- [ ] **Phase 14: First Publish Validation** - First-publish checklist for verifying artifacts after tagging v1.0.0

## Phase Details

### Phase 10: Credentials Setup
**Goal**: Developer has all credentials, keys, and secrets in place so that local signing and CI publishing can both succeed without trial-and-error
**Depends on**: Nothing (first phase of milestone)
**Requirements**: SETUP-01, SETUP-02
**Success Criteria** (what must be TRUE):
  1. Developer can follow SETUP.md to generate a GPG key and export the signing subkey correctly (with the `!` suffix) without corrupting the key material
  2. Developer can follow SETUP.md to generate a Sonatype Portal user token (not the account password) and distinguish it from login credentials
  3. All five GitHub Secrets (SONATYPE_USERNAME, SONATYPE_PASSWORD, SIGNING_KEY, SIGNING_KEY_ID, SIGNING_PASSWORD) are stored and a local `~/.gradle/gradle.properties` file holds the same values for local testing
  4. SETUP.md exists at the repo root and covers both the GPG and Portal token workflows with pitfall warnings
**Plans:** 1/1 plans complete

Plans:
- [ ] 10-01-PLAN.md — Write SETUP.md credential setup guide (GPG, Sonatype, GitHub Secrets, gradle.properties)

### Phase 11: Publishing Configuration
**Goal**: All three modules (dsl-runtime, codegen, gradle-plugin) publish correctly to mavenLocal, producing the full artifact set required by Maven Central
**Depends on**: Phase 10
**Requirements**: PUB-01, PUB-02, PUB-03, PUB-04
**Success Criteria** (what must be TRUE):
  1. `./gradlew publishToMavenLocal` succeeds for all three modules with no errors
  2. `~/.m2/repository/com/nickanderssohn/` contains main JAR, `-sources.jar`, `-javadoc.jar`, `.pom`, and `.asc` signature files for each module
  3. Each module's POM contains all required Maven Central fields: name, description, url, licenses, developers, and scm
  4. Running `./gradlew publishToMavenLocal` on a machine without GPG credentials configured completes without error (signing guard is in place)
  5. Version reads `1.0.0` (no SNAPSHOT suffix) across all modules
**Plans:** 2/2 plans complete

Plans:
- [ ] 11-01-PLAN.md — Configure vanniktech 0.35.0, version bump, POM metadata, signing guard, Gradle 8.13 upgrade
- [ ] 11-02-PLAN.md — Verify publishToMavenLocal and artifact inspection

### Phase 12: CI Workflows
**Goal**: Every PR gets a build-and-test gate, and pushing a v* tag triggers a fully automated publish to Maven Central
**Depends on**: Phase 11
**Requirements**: CI-01, CI-02
**Success Criteria** (what must be TRUE):
  1. Opening or updating a PR triggers `ci.yml` and the GitHub Actions check runs `./gradlew build` including Testcontainers integration tests
  2. Pushing a `v*` tag triggers `publish.yml` and the workflow publishes all three modules to Maven Central using GitHub Secrets (no manual credential entry)
  3. The CI workflow never attempts to publish and the publish workflow never runs on plain pushes or PRs
**Plans:** 1/1 plans complete

Plans:
- [ ] 12-01-PLAN.md — Create ci.yml (PR/push build gate) and publish.yml (v* tag Maven Central publish)

### Phase 13: README and Docs
**Goal**: A developer who has never seen the project can understand its value, add the dependency, configure the plugin, and write their first DSL block from the README alone
**Depends on**: Phase 11
**Requirements**: DOCS-01, DOCS-02, DOCS-03
**Success Criteria** (what must be TRUE):
  1. README opens with a one-paragraph value proposition for jOOQ + Kotlin test data setup, followed by Maven Central dependency coordinates and a `pluginManagement` setup block
  2. README includes a DSL usage example showing `DecDsl.execute {}` with at least one nested builder and FK resolution
  3. README header displays a GitHub Actions build status badge and a Maven Central version badge that link to their respective sources
  4. `CHANGELOG.md` exists and documents v1.0.0 as the first public release with a summary of capabilities
**Plans:** 1/1 plans complete

Plans:
- [ ] 13-01-PLAN.md — Rewrite README.md for Maven Central and create CHANGELOG.md

### Phase 13.1: Configurable DSL output directory and todo-list example update (INSERTED)

**Goal:** Users can configure the Gradle plugin's output directory and target source set via the `declarativeJooq` extension, and the todo-list example is updated to version 1.0.0 with the new properties demonstrated
**Requirements**: CONFIG-01, CONFIG-02, CONFIG-03, CONFIG-04
**Depends on:** Phase 13
**Success Criteria** (what must be TRUE):
  1. `DeclarativeJooqExtension` has `outputDir: DirectoryProperty` (default: `build/generated/declarative-jooq`) and `sourceSet: Property<String>` (default: `"test"`)
  2. Setting a custom `outputDir` in the extension produces generated sources at the specified path
  3. Setting a custom `sourceSet` in the extension wires generated sources into the specified source set
  4. Existing configurations without `outputDir` or `sourceSet` continue to work identically (backward compatible)
  5. `examples/todo-list/build.gradle.kts` uses version `1.0.0` and demonstrates `outputDir`
  6. README "Configure the extension" section documents `outputDir` and `sourceSet` with defaults
**Plans:** 2/2 plans complete

Plans:
- [ ] 13.1-01-PLAN.md — Add outputDir and sourceSet to extension, wire in plugin, add functional tests
- [ ] 13.1-02-PLAN.md — Update todo-list example to 1.0.0 and document new properties in README

### Phase 14: First Publish Validation
**Goal**: Developer has a concrete checklist to verify the first publish succeeded end-to-end before announcing the release
**Depends on**: Phase 12, Phase 13
**Requirements**: VALID-01
**Success Criteria** (what must be TRUE):
  1. A first-publish checklist document exists that covers: portal UI shows all artifacts and signatures, `-sources.jar` and `-javadoc.jar` are present, plugin marker artifact resolves at the correct Maven path
  2. The checklist includes a consumer resolution test: a minimal Gradle project with `pluginManagement { repositories { mavenCentral() } }` can resolve the plugin and runtime dependency at version `1.0.0`
**Plans**: TBD

## Progress

| Phase | Milestone | Plans Complete | Status | Completed |
|-------|-----------|----------------|--------|-----------|
| 1. Runtime DSL Foundation | v0.1 | 3/3 | Complete | 2026-03-15 |
| 2. Code Generation Engine | v0.1 | 3/3 | Complete | 2026-03-16 |
| 3. Gradle Plugin | v0.1 | 2/2 | Complete | 2026-03-16 |
| 4. Edge Cases and Integration | v0.1 | 2/2 | Complete | 2026-03-16 |
| 5. Child-Table-Named Builder Functions | v0.2 | 2/2 | Complete | 2026-03-17 |
| 6. Placeholder Objects | v0.2 | 3/3 | Complete | 2026-03-17 |
| 7. Example Schema | v0.3 | 1/1 | Complete | 2026-03-18 |
| 8. Example API | v0.3 | 2/2 | Complete | 2026-03-18 |
| 9. Example Tests | v0.3 | 2/2 | Complete | 2026-03-18 |
| 10. Credentials Setup | v1.0 | 1/1 | Complete | 2026-03-18 |
| 11. Publishing Configuration | v1.0 | 2/2 | Complete | 2026-03-19 |
| 12. CI Workflows | v1.0 | 1/1 | Complete | 2026-03-19 |
| 13. README and Docs | v1.0 | 1/1 | Complete | 2026-03-19 |
| 13.1. Configurable DSL output directory | 2/2 | Complete   | 2026-03-19 | - |
| 14. First Publish Validation | v1.0 | 0/? | Not started | - |
