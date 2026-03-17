package com.nickanderssohn.declarativejooq.gradle

import com.nickanderssohn.declarativejooq.codegen.CodeGenerator
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*

abstract class GenerateDeclarativeJooqDslTask : DefaultTask() {

    @get:InputDirectory
    abstract val classesDir: DirectoryProperty

    @get:Input
    abstract val outputPackage: Property<String>

    @get:Input
    @get:Optional
    abstract val packageFilter: Property<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        CodeGenerator().generate(
            classesDir.get().asFile,
            outputDir.get().asFile,
            outputPackage.get(),
            packageFilter.orNull
        )
    }
}
