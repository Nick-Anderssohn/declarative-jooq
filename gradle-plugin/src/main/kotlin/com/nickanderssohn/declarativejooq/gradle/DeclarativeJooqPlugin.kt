package com.nickanderssohn.declarativejooq.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.SourceSetContainer

/**
 * Gradle plugin that registers the `generateDeclarativeJooqDsl` task and wires its output
 * into the configured source set (default: `test`). Provides the `declarativeJooq { }` extension
 * block for build script configuration.
 */
class DeclarativeJooqPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        // 1. Create extension
        val extension = project
            .extensions
            .create(
                "declarativeJooq",
                DeclarativeJooqExtension::class.java
            )
        extension
            .outputDir
            .convention(
                project.layout.buildDirectory.dir("generated/declarative-jooq")
            )
        extension
            .sourceSet
            .convention("test")

        // 2. Register task, wire extension properties -> task properties via convention()
        val generateTask = project
            .tasks
            .register(
                "generateDeclarativeJooqDsl",
                GenerateDeclarativeJooqDslTask::class.java
            ) { task ->
            task.classesDir.convention(extension.classesDir)
            task.outputPackage.convention(extension.outputPackage)
            task.packageFilter.convention(extension.packageFilter)
            task.outputDir.convention(extension.outputDir)
            task.group = "declarative-jooq"
            task.description = "Generate declarative jOOQ DSL sources"
        }

        // 3. Wire output directory into configured source set automatically
        // afterEvaluate ensures java/kotlin plugin is applied before we access source sets
        project.afterEvaluate {
            project
                .extensions
                .findByType(SourceSetContainer::class.java)
                ?.getByName(extension.sourceSet.get())
                ?.java
                ?.srcDir(generateTask.flatMap { it.outputDir })
        }
    }
}
