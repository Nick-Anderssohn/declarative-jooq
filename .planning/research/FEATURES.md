# Feature Landscape

**Domain:** jOOQ-specific declarative test data DSL (Kotlin, code-generated, FK-aware)
**Researched:** 2026-03-15
**Confidence note:** Research tools unavailable. Analysis drawn from training knowledge of: FactoryBot (Ruby), Instancio/EasyRandom/datafaker (JVM), kotlinx builder pattern conventions, jOOQ 3.x API, Object Mother and Test Data Builder patterns (Nat Pryce, Steve Freeman). Confidence is MEDIUM for ecosystem feature landscape; HIGH for jOOQ-specific constraints.

---

## Reference Ecosystem

### Libraries surveyed (via training knowledge)

| Library | Ecosystem | Core Approach |
|---------|-----------|---------------|
| FactoryBot | Ruby | Factory definitions with traits, associations, sequences |
| Instancio | Java/JVM | Reflection-based random population, model-based overrides |
| EasyRandom | Java/JVM | Random POJO population with customization hooks |
| datafaker | JVM | Locale-aware fake data generation (names, addresses, etc.) |
| kotlin-faker | Kotlin | Kotlin-idiomatic fake data, DSL for providers |
| Kotest property | Kotlin | Arb-based generators for property-based testing |
| jFixture | JVM | Fixture creation via reflection |
| Test Data Builder pattern | Language-agnostic | Builder pattern with sensible defaults, override-by-exception |
| Object Mother | Language-agnostic | Named factory methods for canonical test objects |

---

## Table Stakes

Features users expect. Missing = product feels incomplete or forces workarounds.

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| **Type-safe record creation** | jOOQ's core promise is type safety; a test helper that loses it is a step backward | Med | Generated DSL must mirror jOOQ record types precisely |
| **Automatic FK resolution** | The #1 pain point in test data setup without a library; manual FK wiring is the entire reason to adopt | High | Topological sort + context propagation required; must handle multi-level graphs |
| **Sensible column defaults** | Users shouldn't have to specify every NOT NULL column; only set what the test cares about | Med | Defaults must be deterministic (not random) for debuggable tests |
| **Override specific fields** | Tests focus on the one field that matters; everything else should "just work" | Low | Named parameter passing or builder setter; standard Kotlin DSL pattern |
| **Multiple records of same type** | Real scenarios require multiple users, multiple orders, etc. | Low | Repeated builder blocks in the same parent scope |
| **Insert ordering (topological)** | FKs require parents before children; hand-managing this is error-prone | High | DAG traversal at execution time; already in requirements |
| **DB-generated value capture** | IDs (sequences/auto-increment), timestamps, defaults — must be available post-insert | Med | Record refresh after insert; already in requirements |
| **Typed result access** | After insertion, tests need to reference the created records (e.g., `result.users[0].id`) | Med | Typed result wrappers per table; already in requirements |
| **Gradle plugin integration** | Code generation is useless without build integration; manual codegen is a non-starter | Med | Plugin with extension config, ties into `compileKotlin` task |
| **Works with existing jOOQ setup** | Users already have jOOQ configured; the library must not require a parallel configuration | Low | Accept `DSLContext` as a parameter, not own it |
| **Nested child builders** | Declaring a user's orders inside the user block is the natural mental model | Med | Scope-controlled builder context with FK flowing down |
| **Result ordering matches declaration** | Tests that reference `result.users[0]` must get the first-declared user | Low | Maintain insertion order in result lists; already in requirements |

---

## Differentiators

Features that set this library apart. Not universally expected, but create clear competitive advantage over manual approaches or generic libraries.

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| **Code-generated DSL (not reflection)** | Compile-time errors for nonexistent columns/tables; no runtime surprises; IDE autocomplete for every field | High | The core architectural bet of this project; Instancio/EasyRandom use reflection and lose type safety |
| **Single-column FK auto-wiring** | Zero ceremony for the common case — nest a child under a parent and the FK is set automatically | High | Requires FK metadata from jOOQ table references; most libraries require explicit association declaration |
| **Self-referential FK support** | Hierarchical data (categories, org trees) is common; most tools break or require manual workarounds | High | Needs cycle-detection and deferred update strategy |
| **Multiple FKs to same table** | `created_by`/`updated_by` → `user.id` is a common audit pattern; most tools conflate which FK to use | High | Disambiguation required at DSL level |
| **DslResult typed container** | After execution, get a structured result object with typed lists per table — not just `List<Any>` | Med | Better than raw jOOQ `Result<Record>` or untyped maps |
| **Topological batch insert** | Batches by table in dependency order rather than one-at-a-time or one-table-at-a-time naively | Med | Fewer round trips vs. per-record insert; important for large test datasets |
| **jOOQ-native (not ORM-agnostic)** | Deep integration with jOOQ FK metadata means no re-declaration of schema knowledge | Med | Contrast with generic factories that require explicit association configuration |
| **Kotlin DSL syntax** | Lambda-with-receiver blocks feel idiomatic; no verbose builder().set().set().build() chains | Low | Standard Kotlin DSL convention; low complexity since jOOQ already uses similar patterns |

---

## Anti-Features

Features to explicitly NOT build. Each has a reason grounded in the project's goals.

| Anti-Feature | Why Avoid | What to Do Instead |
|--------------|-----------|-------------------|
| **Random/fake data generation** | Datafaker/kotlin-faker are already excellent; random data makes tests non-deterministic and harder to debug | Require explicit values or use `null`/zero defaults; document that users should combine with datafaker for fakes |
| **Trait/factory inheritance system** | FactoryBot's trait system adds significant API surface and complexity; not needed for insert-only test data | Kotlin functions returning pre-configured builder blocks serve the same purpose with zero library complexity |
| **Composite FK support** | Rare in practice (~2-5% of schemas), disproportionately complex to implement correctly, blocker for shipping | Document the limitation clearly; users with composite PKs can manually set FKs |
| **Query/read DSL** | Out of scope per PROJECT.md; jOOQ already handles reads well | Intentionally out of scope |
| **Record update/delete** | Test data setup doesn't need CRUD; cleanup is handled by transaction rollback or Testcontainers lifecycle | Intentionally out of scope |
| **Production use / non-test contexts** | Opinionated defaults (always refresh, always insert) are wrong for production; keeping test-only allows shortcuts | Document test-only intent clearly; don't add production-safe guards that complicate the API |
| **Framework-specific integrations** | Spring `@Transactional`, Micronaut DI, etc. add dependencies and narrow audience | Accept `DSLContext` — caller manages lifecycle and transaction |
| **Schema migration** | Flyway/Liquibase/jOOQ Migrations own this problem | Explicitly out of scope |
| **Soft-delete / logical-delete awareness** | Knowing which rows are "active" is domain logic, not test data setup | Users set `deleted_at = null` explicitly if they need it |
| **Sequence/auto-ID management** | DB handles ID generation; always refreshing post-insert captures it without extra library logic | The refresh-after-insert strategy makes manual ID management unnecessary |
| **Lazy/deferred insertion** | Inserting on `.execute()` call is simpler than lazy evaluation and easier to debug | Eager insertion at `execute()` time |
| **Annotation-based configuration** | Annotations need reflection scanning; conflicts with code-generation approach | Pure Kotlin DSL, no annotations |

---

## Feature Dependencies

```
Code generator (scans jOOQ classes)
  └─→ Generated DSL builders (table-specific entry points)
        └─→ FK metadata extraction (from jOOQ table references)
              └─→ Topological sort (determines insert order)
                    └─→ Batch insert per table
                          └─→ Record refresh after insert
                                └─→ Typed DslResult construction
                                      └─→ Typed result accessors

Nested child builders
  └─→ FK auto-wiring (requires FK metadata + parent context propagation)
        └─→ Self-referential FK support (special case: parent === child table)
        └─→ Multiple FK disambiguation (special case: >1 FK to same table)

Gradle plugin
  └─→ Code generator (plugin invokes generator as build step)
  └─→ Source set registration (generated code must be on compile classpath)
```

---

## MVP Recommendation

The following table stakes features are the absolute core. Anything missing here means the library cannot be used for its stated purpose:

**Must ship in MVP:**
1. Code generator + Gradle plugin (without this, nothing else exists)
2. Generated DSL builders with typed field setters
3. Sensible column defaults (deterministic, not random)
4. Automatic single-column FK resolution from parent context
5. Nested child builder blocks
6. Topological batch insert
7. Record refresh after insert
8. Typed DslResult with ordered result lists per table

**Can defer without blocking adoption:**
- Self-referential FK support (needed for hierarchical schemas, not universal)
- Multiple-FK-to-same-table disambiguation (needed for audit columns, not universal)
- Multiple records of same type in one block (useful but workaround exists: call execute twice)

**Validated defers (from PROJECT.md Out of Scope):**
- Composite FK support
- Query/read DSL
- Maven Central publishing

---

## Comparison: This Library vs. Alternatives

| Capability | declarative-jooq | Manual jOOQ | Instancio | FactoryBot (Ruby) |
|------------|-----------------|-------------|-----------|-------------------|
| Type safety | Generated, compile-time | Manual, compile-time | Reflection, runtime | None (Ruby) |
| FK auto-wiring | Automatic | Manual | None | Explicit association config |
| Schema awareness | Deep (jOOQ FK metadata) | Deep | None | None |
| Random data | No (intentional) | Manual | Yes | Via Faker |
| Gradle integration | Yes | N/A | N/A | N/A |
| Kotlin DSL | Yes | Partial (jOOQ DSL) | No | N/A |
| Topological insert | Automatic | Manual | N/A | Automatic |
| Result typing | Typed wrappers | Raw jOOQ Records | Plain POJOs | AR objects |

---

## Sources

- Training knowledge: jOOQ 3.x documentation and FK metadata API (MEDIUM confidence)
- Training knowledge: FactoryBot feature set (HIGH confidence — stable, well-documented)
- Training knowledge: Instancio and EasyRandom feature sets (MEDIUM confidence)
- Training knowledge: Kotlin DSL builder pattern conventions (HIGH confidence)
- Training knowledge: Test Data Builder and Object Mother patterns (HIGH confidence — seminal literature)
- Project requirements: `/Users/nick/Projects/declarative-jooq/.planning/PROJECT.md` (HIGH confidence)
- Note: WebFetch, WebSearch, and Brave Search were all unavailable during research. External source verification was not possible. All ecosystem claims should be validated against current library documentation before finalizing the roadmap.
