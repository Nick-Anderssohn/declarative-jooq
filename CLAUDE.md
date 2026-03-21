## Code Style

- Prefer composition over inheritance. Use concrete classes with function parameters (lambdas) instead of abstract classes with overrides. Reserve `extends`/inheritance only when required by frameworks (e.g., Gradle's `DefaultTask`, jOOQ's `TableImpl`).
