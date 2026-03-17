---
status: complete
phase: 06-placeholder-objects
source: [06-01-SUMMARY.md, 06-02-SUMMARY.md, 06-03-SUMMARY.md]
started: 2026-03-17T21:35:00Z
updated: 2026-03-17T21:50:00Z
---

## Current Test

[testing complete]

## Tests

### 1. All Tests Pass
expected: Run `./gradlew clean test` from the project root. All 30 tests should pass (20 codegen + 10 integration). No compilation errors, no test failures.
result: pass

### 2. Placeholder Capture Syntax
expected: In the generated DSL, calling a root builder like `val alice = appUser { name = "Alice" }` returns a typed `AppUserResult` object (not Unit). The Result object wraps the underlying record.
result: pass

### 3. FK Assignment via Placeholder
expected: Given a placeholder `val alice = appUser { ... }`, assigning `createdBy = alice` on a task builder compiles and correctly wires the FK. After `execute()`, the task row's `created_by` column contains Alice's auto-generated ID.
result: pass

### 4. Cross-Tree Placeholder Wiring
expected: A placeholder captured in one root tree (e.g., first `organization { }` block) can be assigned to an FK property in a completely separate root tree (second `organization { }` block). The topological sorter handles the cross-tree dependency correctly.
result: pass

### 5. Placeholder Override Semantics
expected: When a builder has both a parent-context FK (from nesting) and an explicit placeholder assignment to the same FK field, the placeholder wins. For example, a task nested under org A but with `createdBy = aliceFromOrgB` gets Alice's ID, not org A's auto-resolved value.
result: pass

### 6. README Documentation
expected: README.md contains a "Builder Naming" section explaining child-table-named builder functions and a "Placeholder Objects" section with working examples showing capture, cross-tree, override, and fan-out patterns. No stale examples using `childCategory { }` or incorrect table names.
result: pass

## Summary

total: 6
passed: 6
issues: 0
pending: 0
skipped: 0

## Gaps

[none yet]
