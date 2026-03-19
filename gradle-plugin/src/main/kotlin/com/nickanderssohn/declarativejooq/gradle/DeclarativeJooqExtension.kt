package com.nickanderssohn.declarativejooq.gradle

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property

abstract class DeclarativeJooqExtension {
    /** Directory containing compiled jOOQ record classes (e.g., build/classes/kotlin/main). Required. */
    abstract val classesDir: DirectoryProperty

    /** Output package for generated DSL code (e.g., "com.nickanderssohn.generated"). Required. */
    abstract val outputPackage: Property<String>

    /** Optional package filter to restrict which jOOQ packages are scanned. */
    abstract val packageFilter: Property<String>

    /** Output directory for generated DSL sources. Defaults to build/generated/declarative-jooq. */
    abstract val outputDir: DirectoryProperty

    /** Source set to wire generated sources into. Defaults to "test". */
    abstract val sourceSet: Property<String>
}
