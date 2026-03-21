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
        assertTrue(
            names.contains("com.nickanderssohn.declarativejooq.ProjectTable"),
            "Expected ProjectTable but got: $names"
        )
        assertTrue(
            names.contains("com.nickanderssohn.declarativejooq.MilestoneTable"),
            "Expected MilestoneTable but got: $names"
        )
        assertTrue(
            names.contains("com.nickanderssohn.declarativejooq.DepartmentTable"),
            "Expected DepartmentTable but got: $names"
        )
        assertTrue(
            names.contains("com.nickanderssohn.declarativejooq.EmployeeTable"),
            "Expected EmployeeTable but got: $names"
        )
        assertEquals(8, names.size, "Expected exactly 8 table classes but got: $names")
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

        assertEquals(8, tables.size, "Expected 8 tables (organization, user, category, task, Project, milestone, department, employee)")

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
        assertTrue(org.inboundFKs.size >= 1, "organization should have at least 1 inbound FK (from user, and from Project)")

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

    @Test
    fun pascalCaseTableProducesCorrectIR() {
        requireClassDir()
        val tableNames = ClasspathScanner().findTableClassNames(classDir, "com.nickanderssohn.declarativejooq")
        val tables = MetadataExtractor().extract(classDir, tableNames)

        val project = tables.find { it.tableName == "Project" }
            ?: fail("Project table not found")

        assertEquals("Project", project.tableName)
        assertEquals("ProjectBuilder", project.builderClassName)
        assertEquals("ProjectResult", project.resultClassName)
        assertEquals("project", project.dslFunctionName, "dslFunctionName should be camelCase 'project'")

        val orgIdCol = project.columns.find { it.columnName == "OrganizationId" }
            ?: fail("OrganizationId column not found")
        assertEquals("organizationId", orgIdCol.propertyName, "PascalCase column OrganizationId -> camelCase property organizationId")

        assertEquals(1, project.outboundFKs.size, "Project should have 1 outbound FK")
        val fk = project.outboundFKs[0]
        assertEquals("organization", fk.parentTableName)
        // NAME-01: OrganizationId stripped -> Organization -> normalized matches parent "organization"
        // -> use child table name "Project" -> camelCase "project"
        assertEquals("project", fk.builderFunctionName,
            "FK to organization via OrganizationId (NAME-01): should use child table name 'project'")
    }

    @Test
    fun camelCaseColumnsProduceCorrectIR() {
        requireClassDir()
        val tableNames = ClasspathScanner().findTableClassNames(classDir, "com.nickanderssohn.declarativejooq")
        val tables = MetadataExtractor().extract(classDir, tableNames)

        val milestone = tables.find { it.tableName == "milestone" }
            ?: fail("milestone table not found")

        assertEquals("milestone", milestone.dslFunctionName)

        val projectIdCol = milestone.columns.find { it.columnName == "projectId" }
            ?: fail("projectId column not found")
        assertEquals("projectId", projectIdCol.propertyName, "camelCase column 'projectId' -> property 'projectId'")

        assertEquals(1, milestone.outboundFKs.size, "milestone should have 1 outbound FK")
        val fk = milestone.outboundFKs[0]
        // projectId stripped -> project -> normalized matches parent "Project" -> NAME-01 -> use child table name "milestone"
        assertEquals("milestone", fk.builderFunctionName,
            "FK to Project via projectId (NAME-01): should use child table name 'milestone'")
        assertEquals("project", fk.placeholderPropertyName,
            "placeholderPropertyName: projectId stripped -> 'project'")
    }

    @Test
    fun compositeFkProducesCorrectIR() {
        requireClassDir()
        val tableNames = ClasspathScanner().findTableClassNames(classDir, "com.nickanderssohn.declarativejooq")
        val tables = MetadataExtractor().extract(classDir, tableNames)

        val employee = tables.find { it.tableName == "employee" }
            ?: fail("employee table not found")

        assertFalse(employee.isRoot, "employee should not be root (has outbound FK)")
        assertEquals(1, employee.outboundFKs.size, "employee should have 1 outbound FK (composite to department)")

        val fk = employee.outboundFKs[0]
        assertEquals("department", fk.parentTableName)
        assertTrue(fk.isComposite, "FK should be composite (2 columns)")
        assertEquals(2, fk.childFieldExpressions.size, "Should have 2 child field expressions")
        assertEquals(2, fk.parentFieldExpressions.size, "Should have 2 parent field expressions")
        assertEquals(listOf("organization_id", "department_id"), fk.fkColumnNames)
        assertEquals("department", fk.placeholderPropertyName,
            "Composite FK placeholder should use parent table name 'department'")
        assertEquals("employee", fk.builderFunctionName,
            "Composite FK builder function should use child table name 'employee'")

        assertTrue(fk.childFieldExpressions[0].contains("ORGANIZATION_ID"),
            "First child field expr should reference ORGANIZATION_ID")
        assertTrue(fk.childFieldExpressions[1].contains("DEPARTMENT_ID"),
            "Second child field expr should reference DEPARTMENT_ID")
        assertTrue(fk.parentFieldExpressions[0].contains("ORGANIZATION_ID"),
            "First parent field expr should reference ORGANIZATION_ID")
        assertTrue(fk.parentFieldExpressions[1].contains("DEPARTMENT_ID"),
            "Second parent field expr should reference DEPARTMENT_ID")
    }

    @Test
    fun compositePkTableHasSingleColumnFk() {
        requireClassDir()
        val tableNames = ClasspathScanner().findTableClassNames(classDir, "com.nickanderssohn.declarativejooq")
        val tables = MetadataExtractor().extract(classDir, tableNames)

        val department = tables.find { it.tableName == "department" }
            ?: fail("department table not found")

        assertFalse(department.isRoot, "department should not be root (has outbound FK to organization)")
        assertEquals(1, department.outboundFKs.size, "department should have 1 outbound FK")

        val fk = department.outboundFKs[0]
        assertEquals("organization", fk.parentTableName)
        assertFalse(fk.isComposite, "FK to organization should be single-column")
        assertEquals(1, fk.childFieldExpressions.size)
        assertEquals(1, fk.parentFieldExpressions.size)

        assertTrue(department.inboundFKs.isNotEmpty(), "department should have inbound FK from employee")
        val inboundFk = department.inboundFKs.find { it.childTableName == "employee" }
            ?: fail("Inbound FK from employee not found on department")
        assertTrue(inboundFk.isComposite, "Inbound FK from employee should be composite")
    }
}
