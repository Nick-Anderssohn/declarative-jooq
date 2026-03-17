package com.nickanderssohn.declarativejooq.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.SourceSetContainer

class DeclarativeJooqPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        // 1. Create extension
        val extension = project.extensions.create(
            "declarativeJooq",
            DeclarativeJooqExtension::class.java
        )

        // 2. Determine output directory convention
        val outputDir = project.layout.buildDirectory.dir("generated/declarative-jooq")

        // 3. Register task, wire extension properties -> task properties via convention()
        val generateTask = project.tasks.register(
            "generateDeclarativeJooqDsl",
            GenerateDeclarativeJooqDslTask::class.java
        ) { task ->
            task.classesDir.convention(extension.classesDir)
            task.outputPackage.convention(extension.outputPackage)
            task.packageFilter.convention(extension.packageFilter)
            task.outputDir.convention(outputDir)
            task.group = "declarative-jooq"
            task.description = "Generate declarative jOOQ DSL sources"
        }

        // 4. Wire output directory into test source set automatically
        // afterEvaluate ensures java/kotlin plugin is applied before we access source sets
        project.afterEvaluate {
            project.extensions.findByType(SourceSetContainer::class.java)
                ?.getByName("test")
                ?.java
                ?.srcDir(generateTask.flatMap { it.outputDir })
        }
    }
}
