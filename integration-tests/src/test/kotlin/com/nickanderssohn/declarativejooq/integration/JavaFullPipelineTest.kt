@file:OptIn(org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi::class)

package com.nickanderssohn.declarativejooq.integration

import com.nickanderssohn.declarativejooq.codegen.CodeGenerator
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.io.File

@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JavaFullPipelineTest {

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer<Nothing>("postgres:16-alpine")
    }

    private val classDir = File("../dsl-runtime/build/classes/java/test")
    private val outputPackage = "com.nickanderssohn.generated.java"
    private lateinit var compilationResult: KotlinCompilation.Result
    private lateinit var ctx: DSLContext

    @BeforeAll
    fun setup() {
        assertTrue(
            classDir.exists(),
            "Run ./gradlew :dsl-runtime:testClasses first — missing: ${classDir.absolutePath}"
        )

        // Connect to Postgres via jOOQ DSLContext
        ctx = DSL.using(postgres.jdbcUrl, postgres.username, postgres.password)

        // Create schema in Postgres (same tables as FullPipelineTest — same SQL table names)
        ctx.execute("CREATE TABLE organization (id BIGSERIAL PRIMARY KEY, name VARCHAR(255) NOT NULL)")
        ctx.execute(
            "CREATE TABLE \"user\" (" +
                "id BIGSERIAL PRIMARY KEY, " +
                "name VARCHAR(255) NOT NULL, " +
                "email VARCHAR(255) NOT NULL, " +
                "organization_id BIGINT NOT NULL REFERENCES organization(id)" +
                ")"
        )
        ctx.execute(
            "CREATE TABLE category (" +
                "id BIGSERIAL PRIMARY KEY, " +
                "name VARCHAR(255) NOT NULL, " +
                "parent_id BIGINT REFERENCES category(id)" +
                ")"
        )
        ctx.execute(
            "CREATE TABLE task (" +
                "id BIGSERIAL PRIMARY KEY, " +
                "title VARCHAR(255) NOT NULL, " +
                "created_by BIGINT NOT NULL REFERENCES \"user\"(id), " +
                "updated_by BIGINT REFERENCES \"user\"(id)" +
                ")"
        )

        // Generate DSL sources from the Java fixture classes
        val generatedSources = CodeGenerator().generateSource(classDir, outputPackage, "com.nickanderssohn.declarativejooq")
        assertTrue(generatedSources.isNotEmpty(), "CodeGenerator produced no source files")

        // Compile generated DSL sources + harness together
        val sources = generatedSources.map { (name, code) -> SourceFile.kotlin(name, code) } +
            listOf(javaIntegrationHarnessSource())
        compilationResult = KotlinCompilation().apply {
            this.sources = sources
            inheritClassPath = true
            messageOutputStream = System.out
            kotlincArguments = listOf("-Xskip-metadata-version-check")
            classpaths = classpaths + listOf(classDir)
        }.compile()

        assertEquals(
            KotlinCompilation.ExitCode.OK,
            compilationResult.exitCode,
            "Compilation failed:\n${compilationResult.messages}"
        )
    }

    @BeforeEach
    fun truncateTables() {
        // Truncate all tables in reverse FK dependency order
        ctx.execute("TRUNCATE task CASCADE")
        ctx.execute("TRUNCATE \"user\" CASCADE")
        ctx.execute("TRUNCATE category CASCADE")
        ctx.execute("TRUNCATE organization CASCADE")
    }

    // -----------------------------------------------------------------------
    // Harness: compiled alongside generated sources, single classloader
    // -----------------------------------------------------------------------

    private fun javaIntegrationHarnessSource(): SourceFile = SourceFile.kotlin(
        "JavaIntegrationHarness.kt",
        """
        package com.nickanderssohn.generated.java

        import com.nickanderssohn.declarativejooq.DslResult
        import com.nickanderssohn.declarativejooq.execute
        import com.nickanderssohn.declarativejooq.JavaTaskTable
        import org.jooq.DSLContext

        object JavaIntegrationHarness {

            /** One org + one user (root + nested) */
            fun runBasic(ctx: DSLContext): DslResult {
                return execute(ctx) {
                    organization {
                        name = "Acme"
                        user {
                            name = "Alice"
                            email = "alice@acme.com"
                        }
                    }
                }
            }

            /** One org + two users */
            fun runMultipleSameType(ctx: DSLContext): DslResult {
                return execute(ctx) {
                    organization {
                        name = "Acme"
                        user {
                            name = "Alice"
                            email = "alice@acme.com"
                        }
                        user {
                            name = "Bob"
                            email = "bob@acme.com"
                        }
                    }
                }
            }

            /** Three-level chain: org -> user -> task (via CREATED_BY FK field) */
            fun runMultiLevel(ctx: DSLContext): DslResult {
                return execute(ctx) {
                    organization {
                        name = "Acme"
                        user {
                            name = "Alice"
                            email = "alice@acme.com"
                            task(JavaTaskTable.JAVA_TASK.CREATED_BY) {
                                title = "Alice's Task"
                            }
                        }
                    }
                }
            }

            /** Three-level self-ref: Electronics -> Phones -> Smartphones */
            fun runSelfRef(ctx: DSLContext): DslResult {
                return execute(ctx) {
                    category {
                        name = "Electronics"
                        category {
                            name = "Phones"
                            category {
                                name = "Smartphones"
                            }
                        }
                    }
                }
            }

            /** Org + user + task via CREATED_BY (multi-FK: created_by set, updated_by NULL) */
            fun runMultiFk(ctx: DSLContext): DslResult {
                return execute(ctx) {
                    organization {
                        name = "Acme"
                        user {
                            name = "Alice"
                            email = "alice@acme.com"
                            task(JavaTaskTable.JAVA_TASK.CREATED_BY) {
                                title = "Created Task"
                            }
                        }
                    }
                }
            }

            /** Mixed graph: org with two users, category tree, task */
            fun runMixedGraph(ctx: DSLContext): DslResult {
                return execute(ctx) {
                    organization {
                        name = "Acme"
                        user {
                            name = "Alice"
                            email = "alice@acme.com"
                            task(JavaTaskTable.JAVA_TASK.CREATED_BY) {
                                title = "Alice's Task"
                            }
                        }
                        user {
                            name = "Bob"
                            email = "bob@acme.com"
                        }
                    }
                    category {
                        name = "Tech"
                        category {
                            name = "Software"
                        }
                    }
                }
            }
        }
        """.trimIndent()
    )

    // -----------------------------------------------------------------------
    // Helper: invoke harness via reflection
    // -----------------------------------------------------------------------

    private fun invokeHarness(method: String) {
        val harnessClass = compilationResult.classLoader.loadClass("$outputPackage.JavaIntegrationHarness")
        val harnessInstance = harnessClass.getField("INSTANCE").get(null)
        harnessClass.getMethod(method, DSLContext::class.java).invoke(harnessInstance, ctx)
    }

    // -----------------------------------------------------------------------
    // Test 1: Root and nested records
    // -----------------------------------------------------------------------

    @Test
    fun rootAndNestedRecords() {
        invokeHarness("runBasic")

        val orgCount = ctx.selectCount().from(DSL.table("organization")).fetchOne(0, Int::class.java)!!
        val userCount = ctx.selectCount().from(DSL.table(DSL.name("user"))).fetchOne(0, Int::class.java)!!
        assertEquals(1, orgCount, "Expected 1 organization row")
        assertEquals(1, userCount, "Expected 1 user row")

        // Verify FK: user.organization_id = organization.id
        val orgId = ctx.select(DSL.field("id")).from(DSL.table("organization"))
            .fetchOne()?.get(DSL.field("id"))
        val orgIdFk = ctx.select(DSL.field("organization_id")).from(DSL.table(DSL.name("user")))
            .fetchOne()?.get(DSL.field("organization_id"))
        assertNotNull(orgId, "Expected organization row")
        assertNotNull(orgIdFk, "Expected user row")
        assertEquals(orgId, orgIdFk, "user.organization_id should match organization.id")
    }

    // -----------------------------------------------------------------------
    // Test 2: Multiple same-type records
    // -----------------------------------------------------------------------

    @Test
    fun multipleSameTypeRecords() {
        invokeHarness("runMultipleSameType")

        val orgCount = ctx.selectCount().from(DSL.table("organization")).fetchOne(0, Int::class.java)!!
        val userCount = ctx.selectCount().from(DSL.table(DSL.name("user"))).fetchOne(0, Int::class.java)!!
        assertEquals(1, orgCount, "Expected 1 organization row")
        assertEquals(2, userCount, "Expected 2 user rows")

        // Both users should point to the same org
        val orgId = ctx.select(DSL.field("id")).from(DSL.table("organization"))
            .fetchOne()?.get(DSL.field("id"))
        val orgIds = ctx.select(DSL.field("organization_id")).from(DSL.table(DSL.name("user")))
            .fetch().map { it.get(DSL.field("organization_id")) }
        assertEquals(2, orgIds.size, "Expected 2 organization_id FK values")
        assertTrue(orgIds.all { it == orgId }, "Both users should reference the same organization")
    }

    // -----------------------------------------------------------------------
    // Test 3: Multi-level nesting
    // -----------------------------------------------------------------------

    @Test
    fun multiLevelNesting() {
        invokeHarness("runMultiLevel")

        val orgCount = ctx.selectCount().from(DSL.table("organization")).fetchOne(0, Int::class.java)!!
        val userCount = ctx.selectCount().from(DSL.table(DSL.name("user"))).fetchOne(0, Int::class.java)!!
        val taskCount = ctx.selectCount().from(DSL.table("task")).fetchOne(0, Int::class.java)!!
        assertEquals(1, orgCount, "Expected 1 organization row")
        assertEquals(1, userCount, "Expected 1 user row")
        assertEquals(1, taskCount, "Expected 1 task row")

        // Verify task.created_by = user.id
        val userId = ctx.select(DSL.field("id")).from(DSL.table(DSL.name("user")))
            .fetchOne()?.get(DSL.field("id"))
        val taskCreatedBy = ctx.select(DSL.field("created_by")).from(DSL.table("task"))
            .fetchOne()?.get(DSL.field("created_by"))
        assertEquals(userId, taskCreatedBy, "task.created_by should equal user.id")
    }

    // -----------------------------------------------------------------------
    // Test 4: Self-referential FK two-pass insert
    // -----------------------------------------------------------------------

    @Test
    fun selfReferentialFkTwoPassInsert() {
        invokeHarness("runSelfRef")

        val count = ctx.selectCount().from(DSL.table("category")).fetchOne(0, Int::class.java)!!
        assertEquals(3, count, "Expected 3 category rows")

        val rows = ctx.select(DSL.field("name"), DSL.field("parent_id"))
            .from(DSL.table("category"))
            .fetch()
        val byName = rows.associateBy(
            { it.get(DSL.field("name")) as String },
            { it.get(DSL.field("parent_id")) }
        )

        assertNull(byName["Electronics"], "Electronics should have NULL parent_id (root)")
        assertNotNull(byName["Phones"], "Phones should have non-null parent_id")
        assertNotNull(byName["Smartphones"], "Smartphones should have non-null parent_id")

        val electronicsId = ctx.select(DSL.field("id")).from(DSL.table("category"))
            .where(DSL.field("name").eq("Electronics"))
            .fetchOne()?.get(DSL.field("id"))
        val phonesId = ctx.select(DSL.field("id")).from(DSL.table("category"))
            .where(DSL.field("name").eq("Phones"))
            .fetchOne()?.get(DSL.field("id"))

        assertEquals(electronicsId, byName["Phones"], "Phones.parent_id should equal Electronics.id")
        assertEquals(phonesId, byName["Smartphones"], "Smartphones.parent_id should equal Phones.id")
    }

    // -----------------------------------------------------------------------
    // Test 5: Multiple FKs to same table
    // -----------------------------------------------------------------------

    @Test
    fun multipleFksToSameTable() {
        invokeHarness("runMultiFk")

        val taskCount = ctx.selectCount().from(DSL.table("task")).fetchOne(0, Int::class.java)!!
        assertEquals(1, taskCount, "Expected 1 task row")

        val aliceId = ctx.select(DSL.field("id")).from(DSL.table(DSL.name("user")))
            .where(DSL.field("name").eq("Alice"))
            .fetchOne()?.get(DSL.field("id"))
        assertNotNull(aliceId, "Alice should be in the DB")

        val taskRow = ctx.select(DSL.field("created_by"), DSL.field("updated_by"))
            .from(DSL.table("task"))
            .fetchOne()
        assertEquals(aliceId, taskRow?.get(DSL.field("created_by")), "task.created_by should equal Alice's id")
        assertNull(taskRow?.get(DSL.field("updated_by")), "task.updated_by should be NULL")
    }

    // -----------------------------------------------------------------------
    // Test 6: Mixed graph
    // -----------------------------------------------------------------------

    @Test
    fun mixedGraph() {
        invokeHarness("runMixedGraph")

        val orgCount = ctx.selectCount().from(DSL.table("organization")).fetchOne(0, Int::class.java)!!
        val userCount = ctx.selectCount().from(DSL.table(DSL.name("user"))).fetchOne(0, Int::class.java)!!
        val taskCount = ctx.selectCount().from(DSL.table("task")).fetchOne(0, Int::class.java)!!
        val categoryCount = ctx.selectCount().from(DSL.table("category")).fetchOne(0, Int::class.java)!!

        assertEquals(1, orgCount, "Expected 1 organization row")
        assertEquals(2, userCount, "Expected 2 user rows")
        assertEquals(1, taskCount, "Expected 1 task row")
        assertEquals(2, categoryCount, "Expected 2 category rows")

        val techId = ctx.select(DSL.field("id")).from(DSL.table("category"))
            .where(DSL.field("name").eq("Tech"))
            .fetchOne()?.get(DSL.field("id"))
        val softwareParentId = ctx.select(DSL.field("parent_id")).from(DSL.table("category"))
            .where(DSL.field("name").eq("Software"))
            .fetchOne()?.get(DSL.field("parent_id"))
        assertEquals(techId, softwareParentId, "Software.parent_id should equal Tech.id")

        val aliceId = ctx.select(DSL.field("id")).from(DSL.table(DSL.name("user")))
            .where(DSL.field("name").eq("Alice"))
            .fetchOne()?.get(DSL.field("id"))
        val taskCreatedBy = ctx.select(DSL.field("created_by")).from(DSL.table("task"))
            .fetchOne()?.get(DSL.field("created_by"))
        assertEquals(aliceId, taskCreatedBy, "task.created_by should equal Alice's id")
    }
}
