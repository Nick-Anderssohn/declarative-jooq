package com.example.declarativejooq.codegen

import com.example.declarativejooq.codegen.scanner.ClasspathScanner
import com.example.declarativejooq.codegen.scanner.MetadataExtractor
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
        val names = ClasspathScanner().findTableClassNames(classDir, "com.example.declarativejooq")
        assertTrue(
            names.contains("com.example.declarativejooq.OrganizationTable"),
            "Expected OrganizationTable but got: $names"
        )
        assertTrue(
            names.contains("com.example.declarativejooq.AppUserTable"),
            "Expected AppUserTable but got: $names"
        )
        assertTrue(
            names.contains("com.example.declarativejooq.CategoryTable"),
            "Expected CategoryTable but got: $names"
        )
        assertTrue(
            names.contains("com.example.declarativejooq.TaskTable"),
            "Expected TaskTable but got: $names"
        )
        assertEquals(4, names.size, "Expected exactly 4 table classes but got: $names")
    }

    @Test
    fun scanFindsRecordClasses() {
        requireClassDir()
        val names = ClasspathScanner().findRecordClassNames(classDir, "com.example.declarativejooq")
        assertTrue(
            names.contains("com.example.declarativejooq.OrganizationRecord"),
            "Expected OrganizationRecord but got: $names"
        )
        assertTrue(
            names.contains("com.example.declarativejooq.AppUserRecord"),
            "Expected AppUserRecord but got: $names"
        )
    }

    @Test
    fun extractorProducesCorrectIR() {
        requireClassDir()
        val tableNames = ClasspathScanner().findTableClassNames(classDir, "com.example.declarativejooq")
        val tables = MetadataExtractor().extract(classDir, tableNames)

        assertEquals(4, tables.size, "Expected 4 tables (organization, app_user, category, task)")

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

        val appUser = tables.find { it.tableName == "app_user" }
            ?: fail("app_user table not found")
        assertEquals("app_user", appUser.tableName)
        assertFalse(appUser.isRoot, "app_user should not be root")
        assertEquals(1, appUser.outboundFKs.size, "app_user should have 1 outbound FK")
        assertEquals("organization", appUser.outboundFKs[0].parentTableName)
        val appUserNonIdentityColumns = appUser.columns.filter { !it.isIdentity }
        assertEquals(3, appUserNonIdentityColumns.size, "app_user should have 3 non-identity columns (name, email, organization_id)")
        val orgIdColumn = appUser.columns.find { it.propertyName == "organizationId" }
        assertNotNull(orgIdColumn, "app_user should have organizationId column")
    }

    @Test
    fun snakeToCamelConversion() {
        requireClassDir()
        val tableNames = ClasspathScanner().findTableClassNames(classDir, "com.example.declarativejooq")
        val tables = MetadataExtractor().extract(classDir, tableNames)

        val appUser = tables.find { it.tableName == "app_user" }
            ?: fail("app_user table not found")

        // organization_id -> organizationId
        assertNotNull(appUser.columns.find { it.propertyName == "organizationId" })
        // name -> name
        assertNotNull(appUser.columns.find { it.propertyName == "name" })
        // id -> id
        assertNotNull(appUser.columns.find { it.propertyName == "id" })
    }
}
