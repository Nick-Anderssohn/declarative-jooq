package com.nickanderssohn.declarativejooq.codegen

import com.nickanderssohn.declarativejooq.codegen.scanner.ClasspathScanner
import com.nickanderssohn.declarativejooq.codegen.scanner.MetadataExtractor
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.File

class JavaScannerTest {

    private val classDir = File("../dsl-runtime/build/classes/java/test")

    private fun requireClassDir() {
        assertTrue(
            classDir.exists(),
            "Java class directory does not exist: ${classDir.absolutePath}. Run ./gradlew :dsl-runtime:testClasses first"
        )
    }

    @Test
    fun scanFindsJavaTableClasses() {
        requireClassDir()
        val names = ClasspathScanner().findTableClassNames(classDir, "com.nickanderssohn.declarativejooq")
        assertTrue(
            names.contains("com.nickanderssohn.declarativejooq.JavaOrganizationTable"),
            "Expected JavaOrganizationTable but got: $names"
        )
        assertTrue(
            names.contains("com.nickanderssohn.declarativejooq.JavaUserTable"),
            "Expected JavaUserTable but got: $names"
        )
        assertTrue(
            names.contains("com.nickanderssohn.declarativejooq.JavaCategoryTable"),
            "Expected JavaCategoryTable but got: $names"
        )
        assertTrue(
            names.contains("com.nickanderssohn.declarativejooq.JavaTaskTable"),
            "Expected JavaTaskTable but got: $names"
        )
        assertEquals(4, names.size, "Expected exactly 4 Java table classes but got: $names")
    }

    @Test
    fun scanFindsJavaRecordClasses() {
        requireClassDir()
        val names = ClasspathScanner().findRecordClassNames(classDir, "com.nickanderssohn.declarativejooq")
        assertTrue(
            names.contains("com.nickanderssohn.declarativejooq.JavaOrganizationRecord"),
            "Expected JavaOrganizationRecord but got: $names"
        )
        assertTrue(
            names.contains("com.nickanderssohn.declarativejooq.JavaUserRecord"),
            "Expected JavaUserRecord but got: $names"
        )
        assertTrue(
            names.contains("com.nickanderssohn.declarativejooq.JavaCategoryRecord"),
            "Expected JavaCategoryRecord but got: $names"
        )
        assertTrue(
            names.contains("com.nickanderssohn.declarativejooq.JavaTaskRecord"),
            "Expected JavaTaskRecord but got: $names"
        )
        assertEquals(4, names.size, "Expected exactly 4 Java record classes but got: $names")
    }

    @Test
    fun extractorProducesCorrectIRFromJava() {
        requireClassDir()
        val tableNames = ClasspathScanner().findTableClassNames(classDir, "com.nickanderssohn.declarativejooq")
        val tables = MetadataExtractor().extract(classDir, tableNames)

        assertEquals(4, tables.size, "Expected 4 tables (organization, user, category, task)")

        val org = tables.find { it.tableName == "organization" }
            ?: fail("organization table not found")
        assertEquals("JavaOrganizationTable", org.tableClassName)
        assertEquals("JAVA_ORGANIZATION", org.tableConstantName)
        assertEquals("JavaOrganizationRecord", org.recordClassName)
        assertEquals("OrganizationBuilder", org.builderClassName)
        assertTrue(org.isRoot, "organization should be root (no outbound FKs)")
        assertEquals(2, org.columns.size, "organization should have 2 columns (id + name)")
        val idCol = org.columns.find { it.isIdentity }
        assertNotNull(idCol, "organization should have identity column")
        assertEquals("id", idCol!!.columnName)

        val user = tables.find { it.tableName == "user" }
            ?: fail("user table not found")
        assertEquals("JavaUserTable", user.tableClassName)
        assertEquals("JAVA_USER", user.tableConstantName)
        assertEquals("JavaUserRecord", user.recordClassName)
        assertFalse(user.isRoot, "user should not be root")
        assertEquals(4, user.columns.size, "user should have 4 columns")
        assertEquals(1, user.outboundFKs.size, "user should have 1 outbound FK")
        assertEquals("organization", user.outboundFKs[0].parentTableName)

        val category = tables.find { it.tableName == "category" }
            ?: fail("category table not found")
        assertEquals("JavaCategoryTable", category.tableClassName)
        assertEquals("JAVA_CATEGORY", category.tableConstantName)
        assertEquals(3, category.columns.size, "category should have 3 columns")

        val task = tables.find { it.tableName == "task" }
            ?: fail("task table not found")
        assertEquals("JavaTaskTable", task.tableClassName)
        assertEquals("JAVA_TASK", task.tableConstantName)
        assertEquals(4, task.columns.size, "task should have 4 columns")
        assertEquals(2, task.outboundFKs.size, "task should have 2 outbound FKs")
    }

    @Test
    fun javaFkExtractionWorks() {
        requireClassDir()
        val tableNames = ClasspathScanner().findTableClassNames(classDir, "com.nickanderssohn.declarativejooq")
        val tables = MetadataExtractor().extract(classDir, tableNames)

        val user = tables.find { it.tableName == "user" } ?: fail("user table not found")
        assertEquals(1, user.outboundFKs.size, "user should have 1 outbound FK")
        assertEquals("organization", user.outboundFKs[0].parentTableName)

        val task = tables.find { it.tableName == "task" } ?: fail("task table not found")
        assertEquals(2, task.outboundFKs.size, "task should have 2 outbound FKs")
        assertTrue(task.outboundFKs.all { it.parentTableName == "user" },
            "Both task FKs should reference 'user', got: ${task.outboundFKs.map { it.parentTableName }}")

        val category = tables.find { it.tableName == "category" } ?: fail("category table not found")
        assertEquals(1, category.outboundFKs.size, "category should have 1 outbound FK")
        assertEquals("category", category.outboundFKs[0].parentTableName,
            "category self-ref FK should point to 'category'")
    }

    @Test
    fun javaMultiFkDetection() {
        requireClassDir()
        val tableNames = ClasspathScanner().findTableClassNames(classDir, "com.nickanderssohn.declarativejooq")
        val tables = MetadataExtractor().extract(classDir, tableNames)

        val task = tables.find { it.tableName == "task" } ?: fail("task table not found")
        // Both FKs point to "user" => both should be multi-FK
        assertTrue(task.outboundFKs.all { it.isMultiFk },
            "Both task FKs should have isMultiFk = true")
        // Both should use child table name "task" as builderFunctionName
        assertTrue(task.outboundFKs.all { it.builderFunctionName == "task" },
            "Multi-FK builders should all be named 'task', got: ${task.outboundFKs.map { it.builderFunctionName }}")
        assertEquals(2, task.outboundFKs.size, "task should have exactly 2 outbound FKs")
    }

    @Test
    fun javaSelfRefDetection() {
        requireClassDir()
        val tableNames = ClasspathScanner().findTableClassNames(classDir, "com.nickanderssohn.declarativejooq")
        val tables = MetadataExtractor().extract(classDir, tableNames)

        val category = tables.find { it.tableName == "category" } ?: fail("category table not found")
        val selfRefFk = category.outboundFKs.find { it.isSelfReferential }
            ?: fail("Self-referential FK not found on JavaCategoryTable")
        assertTrue(selfRefFk.isSelfReferential,
            "category FK should be flagged as isSelfReferential = true")
        assertEquals("category", selfRefFk.builderFunctionName,
            "Self-referential FK builder should use table name 'category'")
    }
}
