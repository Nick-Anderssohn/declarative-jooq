package com.example.declarativejooq.gradle

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class DeclarativeJooqPluginFunctionalTest {

    @field:TempDir
    lateinit var testProjectDir: File

    private lateinit var buildFile: File
    private lateinit var settingsFile: File

    @BeforeEach
    fun setup() {
        settingsFile = testProjectDir.resolve("settings.gradle.kts")
        settingsFile.writeText("""rootProject.name = "test-project"""")

        buildFile = testProjectDir.resolve("build.gradle.kts")

        // Create empty classesDir so InputDirectory annotation does not fail
        testProjectDir.resolve("jooq-classes").mkdirs()
    }

    private fun writeBuildFile() {
        buildFile.writeText(
            """
            plugins {
                id("com.example.declarative-jooq")
            }
            declarativeJooq {
                classesDir.set(file("jooq-classes"))
                outputPackage.set("com.example.generated")
            }
            """.trimIndent()
        )
    }

    private fun createRunner(vararg args: String) = GradleRunner.create()
        .forwardOutput()
        .withPluginClasspath()
        .withProjectDir(testProjectDir)
        .withArguments(*args)

    @Test
    fun `task succeeds with empty classesDir`() {
        writeBuildFile()

        val result = createRunner("generateDeclarativeJooqDsl").build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":generateDeclarativeJooqDsl")?.outcome)
    }

    @Test
    fun `output directory is created`() {
        writeBuildFile()

        createRunner("generateDeclarativeJooqDsl").build()

        val outputDir = testProjectDir.resolve("build/generated/declarative-jooq")
        assertTrue(outputDir.exists(), "Output directory build/generated/declarative-jooq should exist")
    }

    @Test
    fun `task is up-to-date on second run`() {
        writeBuildFile()

        // First run
        createRunner("generateDeclarativeJooqDsl").build()

        // Second run without changes
        val result = createRunner("generateDeclarativeJooqDsl").build()

        assertEquals(TaskOutcome.UP_TO_DATE, result.task(":generateDeclarativeJooqDsl")?.outcome)
    }

    @Test
    fun `task is configuration cache compatible`() {
        writeBuildFile()

        // First run: stores configuration cache entry
        val first = createRunner("generateDeclarativeJooqDsl", "--configuration-cache").build()
        assertEquals(TaskOutcome.SUCCESS, first.task(":generateDeclarativeJooqDsl")?.outcome)

        // Delete output to force re-execution (otherwise UP_TO_DATE skips TaskAction)
        testProjectDir.resolve("build/generated/declarative-jooq").deleteRecursively()

        // Second run: reuses configuration cache entry
        val second = createRunner("generateDeclarativeJooqDsl", "--configuration-cache").build()
        assertTrue(
            second.output.contains("Reusing configuration cache") ||
                second.output.contains("Configuration cache entry reused"),
            "Second run should reuse configuration cache. Output: ${second.output}"
        )
    }

    @Test
    fun `plugin can be applied without extension configuration`() {
        // Plugin applies without error even before extension is configured
        // (task will fail at execution time if required properties missing, but apply succeeds)
        buildFile.writeText(
            """
            plugins {
                id("com.example.declarative-jooq")
            }
            """.trimIndent()
        )

        val result = createRunner("tasks", "--group=declarative-jooq").build()

        assertTrue(result.output.contains("generateDeclarativeJooqDsl"))
    }
}
