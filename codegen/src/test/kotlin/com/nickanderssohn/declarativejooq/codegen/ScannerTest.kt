package com.nickanderssohn.declarativejooq.codegen

import com.nickanderssohn.declarativejooq.codegen.scanner.ClasspathScanner
import com.nickanderssohn.declarativejooq.codegen.scanner.MetadataExtractor
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.io.File

class ScannerTest {

    private val classDir = File("../dsl-runtime/build/classes/kotlin/test")

    private fun requireClassDir() {
        assertTrue(
            classDir.exists(),
            "Class directory does not exist: ${classDir.absolutePath}. Run ./gradlew :dsl-runtime:testClasses first"
        )
    }

    @Test
    fun scanFindsTableClasses() {
        requireClassDir()
        val names = ClasspathScanner().findTableClassNames(classDir, "com.nickanderssohn.declarativejooq")
        assertTrue(
            names.contains("com.nickanderssohn.declarativejooq.OrganizationTable"),
            "Expected OrganizationTable but got: $names"
        )
        assertTrue(
            names.contains("com.nickanderssohn.declarativejooq.UserTable"),
            "Expected UserTable but got: $names"
        )
        assertTrue(
            names.contains("com.nickanderssohn.declarativejooq.CategoryTable"),
            "Expected CategoryTable but got: $names"
        )
        assertTrue(
            names.contains("com.nickanderssohn.declarativejooq.TaskTable"),
            "Expected TaskTable but got: $names"
        )
        assertEquals(4, names.size, "Expected exactly 4 table classes but got: $names")
    }

    @Test
    fun scanFindsRecordClasses() {
        requireClassDir()
        val names = ClasspathScanner().findRecordClassNames(classDir, "com.nickanderssohn.declarativejooq")
        assertTrue(
            names.contains("com.nickanderssohn.declarativejooq.OrganizationRecord"),
            "Expected OrganizationRecord but got: $names"
        )
        assertTrue(
            names.contains("com.nickanderssohn.declarativejooq.UserRecord"),
            "Expected UserRecord but got: $names"
        )
    }

    @Test
    fun extractorProducesCorrectIR() {
        requireClassDir()
        val tableNames = ClasspathScanner().findTableClassNames(classDir, "com.nickanderssohn.declarativejooq")
        val tables = MetadataExtractor().extract(classDir, tableNames)

        assertEquals(4, tables.size, "Expected 4 tables (organization, user, category, task)")

        val org = tables.find { it.tableName == "organization" }
            ?: fail("organization table not found")
        assertEquals("OrganizationTable", org.tableClassName)
        assertEquals("ORGANIZATION", org.tableConstantName)
        assertEquals("OrganizationRecord", org.recordClassName)
        assertEquals("OrganizationBuilder", org.builderClassName)
        assertTrue(org.isRoot, "organization should be root (no outbound FKs)")
        assertEquals(2, org.columns.size, "organization should have 2 columns (id + name)")
        val orgNonIdentityColumns = org.columns.filter { !it.isIdentity }
        assertEquals(1, orgNonIdentityColumns.size, "organization should have 1 non-identity column")
        assertEquals("name", orgNonIdentityColumns[0].propertyName)
        assertEquals(1, org.inboundFKs.size, "organization should have 1 inbound FK")

        val user = tables.find { it.tableName == "user" }
            ?: fail("user table not found")
        assertEquals("user", user.tableName)
        assertFalse(user.isRoot, "user should not be root")
        assertEquals(1, user.outboundFKs.size, "user should have 1 outbound FK")
        assertEquals("organization", user.outboundFKs[0].parentTableName)
        val userNonIdentityColumns = user.columns.filter { !it.isIdentity }
        assertEquals(3, userNonIdentityColumns.size, "user should have 3 non-identity columns (name, email, organization_id)")
        val orgIdColumn = user.columns.find { it.propertyName == "organizationId" }
        assertNotNull(orgIdColumn, "user should have organizationId column")
    }

    @Test
    fun childTableNameWins() {
        requireClassDir()
        val tableNames = ClasspathScanner().findTableClassNames(classDir, "com.nickanderssohn.declarativejooq")
        val tables = MetadataExtractor().extract(classDir, tableNames)

        val user = tables.find { it.tableName == "user" } ?: fail("user table not found")
        // organization_id stripped = "organization" == parent "organization" => child table name "user"
        val orgFk = user.outboundFKs.find { it.parentTableName == "organization" }
            ?: fail("FK to organization not found")
        assertEquals("user", orgFk.builderFunctionName,
            "When FK col stripped matches parent table, builder should use child table name")
    }

    @Test
    fun fkColumnFallback() {
        requireClassDir()
        val tableNames = ClasspathScanner().findTableClassNames(classDir, "com.nickanderssohn.declarativejooq")
        val tables = MetadataExtractor().extract(classDir, tableNames)

        val task = tables.find { it.tableName == "task" } ?: fail("task table not found")
        // task has two FKs to "user" (created_by, updated_by) — both are multi-FK, so both get "task" as builder name
        val createdByFk = task.outboundFKs.find { it.fkName.contains("created_by", ignoreCase = true) }
            ?: fail("created_by FK not found on task")
        assertEquals("task", createdByFk.builderFunctionName,
            "Multi-FK: builder function should use child table name 'task', not FK column name")
        assertTrue(createdByFk.isMultiFk,
            "created_by FK should be flagged as isMultiFk = true")
    }

    @Test
    fun selfRefUsesTableName() {
        requireClassDir()
        val tableNames = ClasspathScanner().findTableClassNames(classDir, "com.nickanderssohn.declarativejooq")
        val tables = MetadataExtractor().extract(classDir, tableNames)

        val category = tables.find { it.tableName == "category" } ?: fail("category table not found")
        val selfRefFk = category.outboundFKs.find { it.isSelfReferential }
            ?: fail("Self-referential FK not found on category")
        assertEquals("category", selfRefFk.builderFunctionName,
            "Self-referential FK builder should use table name, not 'childCategory'")
    }

    @Test
    fun multiFkUsesChildTableName() {
        requireClassDir()
        val tableNames = ClasspathScanner().findTableClassNames(classDir, "com.nickanderssohn.declarativejooq")
        val tables = MetadataExtractor().extract(classDir, tableNames)

        val task = tables.find { it.tableName == "task" } ?: fail("task table not found")
        // task has created_by -> "user" and updated_by -> "user"
        // Both are multi-FK (same child table, same parent table) — both should use child table name "task"
        val builderNames = task.outboundFKs.map { it.builderFunctionName }
        assertTrue(builderNames.all { it == "task" }, "All multi-FK builders should be named 'task', got: $builderNames")
        assertEquals(2, builderNames.size, "task should have exactly 2 outbound FKs")
        assertTrue(task.outboundFKs.all { it.isMultiFk }, "Both task FKs should have isMultiFk = true")
    }

    @Test
    fun multiFkFlagSetCorrectly() {
        requireClassDir()
        val tableNames = ClasspathScanner().findTableClassNames(classDir, "com.nickanderssohn.declarativejooq")
        val tables = MetadataExtractor().extract(classDir, tableNames)

        val task = tables.find { it.tableName == "task" } ?: fail("task table not found")
        // task has two FKs to "user" => both should be isMultiFk = true
        task.outboundFKs.forEach { fk ->
            assertTrue(fk.isMultiFk, "task FK '${fk.fkName}' should have isMultiFk = true")
        }

        val user = tables.find { it.tableName == "user" } ?: fail("user table not found")
        // user has one FK to organization => isMultiFk = false
        val orgFk = user.outboundFKs.find { it.parentTableName == "organization" }
            ?: fail("FK to organization not found")
        assertFalse(orgFk.isMultiFk, "user -> organization FK should have isMultiFk = false (single FK)")
    }

    @Test
    fun snakeToCamelConversion() {
        requireClassDir()
        val tableNames = ClasspathScanner().findTableClassNames(classDir, "com.nickanderssohn.declarativejooq")
        val tables = MetadataExtractor().extract(classDir, tableNames)

        val user = tables.find { it.tableName == "user" }
            ?: fail("user table not found")

        // organization_id -> organizationId
        assertNotNull(user.columns.find { it.propertyName == "organizationId" })
        // name -> name
        assertNotNull(user.columns.find { it.propertyName == "name" })
        // id -> id
        assertNotNull(user.columns.find { it.propertyName == "id" })
    }
}
