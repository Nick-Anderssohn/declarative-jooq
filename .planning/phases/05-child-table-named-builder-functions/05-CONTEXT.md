# Phase 5: Child-Table-Named Builder Functions - Context

**Gathered:** 2026-03-16
**Status:** Ready for planning

<domain>
## Phase Boundary

Update `MetadataExtractor.builderFunctionName` computation so generated DSL builder functions use the child table name by default, with fallback to FK column name when the FK column carries semantic meaning that doesn't match the parent table name, plus explicit collision detection. Self-referential FKs use the table name (no `child` prefix). All existing test harness strings updated to match new names.

</domain>

<decisions>
## Implementation Decisions

### Self-referential FK naming (NAME-04)
- Self-ref builders use the **table name**, not the `child` prefix
- `category { }` inside `category { }` — not `childCategory { }`
- Applies to all self-referential FKs unconditionally
- Updates needed: 4 test harness call sites (`childCategory` → `category`)

### Fallback condition rule (NAME-01 / NAME-02)
- **Primary rule:** strip `_id` suffix from FK column name; compare result to parent table name (exact snake_case match)
  - Match → use child table name (camelCase): `organization_id` stripped → `organization` == `organization` table → `appUser { }`
  - No match → use FK column name (camelCase, strip `_id`): `created_by` stripped → `created_by` ≠ `app_user` → `createdBy { }`
- The ARCHITECTURE.md "multiple FKs to same parent" condition is NOT the primary rule — NAME-02 wins
- A single semantically-named FK (e.g., solo `created_by → app_user`) should produce `createdBy { }`, not `task { }`
- Comparison is always exact snake_case (no camelCase transform before comparing)

### NAME-03 collision detection
- Run as a **separate second pass** within each child table, after NAME-02 candidate names are computed
- If two FKs from the same child table produce the same candidate name → both fall back to FK column name
- Example: `user` and `user_id` both pointing to `user` → both stripped match `user` → collision → use `user { }` and `userId { }` (FK col names)
- This is additive to NAME-02, not a replacement

### Algorithm (complete, ordered)
Per child table T, two passes:
1. **Compute candidates:** For each FK, apply:
   - If self-ref: candidate = `toCamelCase(T.tableName)`  ← NEW (was `"child" + toPascalCase`)
   - Else if `fkColumnName.removeSuffix("_id") == parentTableName`: candidate = `toCamelCase(T.tableName)` (child table name)
   - Else: candidate = `toCamelCase(fkColumnName.removeSuffix("_id"))` (FK column name)
2. **Collision pass:** Find duplicates in candidate names. For each FK in a colliding group, override with `toCamelCase(fkColumnName.removeSuffix("_id"))`

### Test coverage
- Update all existing call sites in `CodeGeneratorTest.kt` and `FullPipelineTest.kt` test harness strings:
  - `organization { }` (child-of-organization builder for app_user) → `appUser { }` (6 occurrences)
  - `childCategory { }` → `category { }` (4 occurrences)
- Add naming-specific tests to **existing `ScannerTest.kt`** (not a new file):
  - Child table name wins: e.g., `organization_id → organization` → builder is `appUser`
  - FK col fallback: `created_by → app_user` (single FK) → builder is `createdBy`
  - Self-ref table name: `parent_id → category` (self-ref) → builder is `category`
  - NAME-03 collision: two FKs producing same candidate → both use FK col names

### Claude's Discretion
- Exact refactoring approach for the two-pass logic inside `MetadataExtractor.extract()` (inline vs extracted method)
- How to structure the collision detection data structures

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Requirements
- `.planning/REQUIREMENTS.md` — NAME-01, NAME-02, NAME-03, NAME-04 specs (builder naming requirements)

### Architecture
- `.planning/research/ARCHITECTURE.md` — v0.1 baseline, the `MetadataExtractor` current naming logic (lines 72-76), the `ForeignKeyIR.builderFunctionName` field, emitter consumers, and the originally proposed algorithm (note: fallback condition rule superseded by NAME-02 per user decision)

### Key source files
- `codegen/src/main/kotlin/com/nickanderssohn/declarativejooq/codegen/scanner/MetadataExtractor.kt` — single change location (lines 72-76 and surrounding loop)
- `codegen/src/test/kotlin/com/nickanderssohn/declarativejooq/codegen/CodeGeneratorTest.kt` — test harness strings to update + `multipleFkNaming` test
- `codegen/src/test/kotlin/com/nickanderssohn/declarativejooq/codegen/ScannerTest.kt` — new naming tests go here
- `integration-tests/src/test/kotlin/com/nickanderssohn/declarativejooq/integration/FullPipelineTest.kt` — integration harness strings to update

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `MetadataExtractor.toCamelCase()` / `toPascalCase()` — already available, used for builder function name generation
- `ForeignKeyIR.builderFunctionName` — the single field that stores the computed name; consumed as a black box by `BuilderEmitter` and `DslScopeEmitter`

### Established Patterns
- Naming computation is currently inline inside the FK loop (lines 61-86 of `MetadataExtractor.extract()`). The two-pass refactor needs to happen within this block — first collect FKs, then compute names in a separate step.
- `BuilderEmitter` and `DslScopeEmitter` consume `fk.builderFunctionName` verbatim — no changes needed there.
- `inboundFKs` cross-linking is the second pass in the current code; the new two-pass naming must fit before that.

### Integration Points
- `MetadataExtractor.extract()` outbound FK loop — the only change location
- Test harnesses in `CodeGeneratorTest` and `FullPipelineTest` are embedded Kotlin source strings that reference builder function names directly — need manual string updates

</code_context>

<specifics>
## Specific Ideas

- The two-pass within each child table should be a refactor of the inline FK map/collect block, not a separate method call on `ForeignKeyIR` — keep it in `MetadataExtractor`
- `multipleFkNaming()` test in `CodeGeneratorTest` already asserts `createdBy` and `updatedBy` in `AppUserBuilder` and `task !in methodNames` — these assertions remain valid after Phase 5 (disambiguation still applies, `task` is still absent since two FKs from task to app_user trigger collision → FK col fallback)

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope.

</deferred>

---

*Phase: 05-child-table-named-builder-functions*
*Context gathered: 2026-03-16*
