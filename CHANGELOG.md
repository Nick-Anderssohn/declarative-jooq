# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.1.0] - 2026-03-20

### Added

- Composite foreign key support

## [1.0.1] - 2026-03-19

### Fixed

- Classpath scanner now filters out inner classes (e.g. jOOQ `*Path` classes) that extend `TableImpl` but lack a companion-object singleton
- `buildRecord()` uses null-safe assignment so unset builder properties don't override database `DEFAULT` values
- Record class imports now use a separate `recordSourcePackage`, supporting jOOQ's standard `tables/records` sub-package layout

### Changed

- `todo-list` example replaced hand-written jOOQ files with jOOQ code generation against a live Postgres database in Docker
- `todo-list` example now includes a `Makefile` with targets for the full development workflow

## [1.0.0] - 2026-03-19

First public release on Maven Central.

### Added

- Declarative DSL for jOOQ test data creation with automatic FK resolution
- Topological insert ordering — records inserted in dependency order regardless of declaration order
- Self-referential FK support via two-pass insert strategy
- Multiple FKs to the same table with `TableField` disambiguation
- Placeholder objects for explicit cross-tree FK wiring
- Typed result accessors via `DslResult`
- Code generator that scans compiled jOOQ record classes and produces typed Kotlin DSL builders
- Gradle plugin (`com.nickanderssohn.declarative-jooq`) with `generateDeclarativeJooqDsl` task
- Three published modules: `declarative-jooq-dsl-runtime`, `declarative-jooq-codegen`, `declarative-jooq-gradle-plugin`

[1.1.0]: https://github.com/Nick-Anderssohn/declarative-jooq/releases/tag/v1.1.0
[1.0.1]: https://github.com/Nick-Anderssohn/declarative-jooq/releases/tag/v1.0.1
[1.0.0]: https://github.com/Nick-Anderssohn/declarative-jooq/releases/tag/v1.0.0
