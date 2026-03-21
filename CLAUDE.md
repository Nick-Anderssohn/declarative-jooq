## Code Style

- Prefer composition over inheritance. Use concrete classes with function parameters (lambdas) instead of abstract classes with overrides. Reserve `extends`/inheritance only when required by frameworks (e.g., Gradle's `DefaultTask`, jOOQ's `TableImpl`).
- Prefer functional chains over imperative loops. Use `.filter{}`, `.map{}`, `.mapNotNull{}` pipelines instead of `for` loops with `if`/`continue` and mutable accumulators. Return directly from the chain rather than building up a mutable variable.
- Use `.asSequence()` for lazy evaluation when chaining multiple operations over collections.
- Extract small extension functions for side concerns (e.g., error handling) to keep the main logic a clean, readable chain.
- Use scope functions (`let`, `onEach`, etc.) to keep transformations inside the chain rather than breaking out into intermediate variables.
