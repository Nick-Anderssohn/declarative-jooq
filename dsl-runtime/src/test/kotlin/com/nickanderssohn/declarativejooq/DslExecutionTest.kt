package com.nickanderssohn.declarativejooq

import org.jooq.DSLContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

/**
 * Integration tests for the full DSL flow.
 *
 * Covers DSL-01 through DSL-08:
 *   DSL-01  DecDsl.execute() entry point returns DslResult
 *   DSL-02  Root builder functions at execute block top level
 *   DSL-03  Child builder auto-populates FK from parent context
 *   DSL-04  Multiple records of same type in one block
 *   DSL-05  Topological insert order (parent before child) — validated via FK integrity
 *   DSL-07  DB-generated PKs are non-null after insert
 *   DSL-08  DslResult records appear in declaration order
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DslExecutionTest {

    private lateinit var dslContext: DSLContext

    @BeforeAll
    fun setup() {
        dslContext = TestSchema.createDslContext()
    }

    @BeforeEach
    fun cleanTables() {
        // Delete child rows first to satisfy FK constraint
        dslContext.deleteFrom(UserTable.USER).execute()
        dslContext.deleteFrom(OrganizationTable.ORGANIZATION).execute()
    }

    // -----------------------------------------------------------------------
    // DSL-01: DecDsl.execute() returns a DslResult with the inserted records
    // -----------------------------------------------------------------------

    @Test
    fun testBasicExecute() {
        val result = DecDsl.execute(dslContext) {
            organization { name = "Acme" }
        }

        val orgs = result.records("organization")
        assertEquals(1, orgs.size, "Expected 1 organization in result")
        val org = orgs[0] as OrganizationRecord
        assertEquals("Acme", org.name)
    }

    // -----------------------------------------------------------------------
    // DSL-02: Root builder function inserts the record into the database
    // -----------------------------------------------------------------------

    @Test
    fun testRootBuilder() {
        DecDsl.execute(dslContext) {
            organization { name = "RootCorp" }
        }

        val rows = dslContext.selectFrom(OrganizationTable.ORGANIZATION).fetch()
        assertEquals(1, rows.size, "Expected 1 organization row in DB")
        assertEquals("RootCorp", rows[0].get(OrganizationTable.ORGANIZATION.NAME))
    }

    // -----------------------------------------------------------------------
    // DSL-03: Child builder auto-populates FK from parent's generated PK
    // -----------------------------------------------------------------------

    @Test
    fun testFkResolution() {
        DecDsl.execute(dslContext) {
            organization {
                name = "Acme"
                user {
                    name = "Alice"
                    email = "alice@acme.com"
                }
            }
        }

        // Fetch the organization
        val orgRows = dslContext.selectFrom(OrganizationTable.ORGANIZATION).fetch()
        assertEquals(1, orgRows.size)
        val orgId = orgRows[0].get(OrganizationTable.ORGANIZATION.ID)
        assertNotNull(orgId, "Organization ID must be set after insert")

        // Fetch the user and verify FK
        val userRows = dslContext.selectFrom(UserTable.USER).fetch()
        assertEquals(1, userRows.size)
        val userOrgId = userRows[0].get(UserTable.USER.ORGANIZATION_ID)
        assertEquals(orgId, userOrgId, "User's organization_id must equal the organization's generated id")
    }

    // -----------------------------------------------------------------------
    // DSL-04: Multiple child records of same type under one parent
    // -----------------------------------------------------------------------

    @Test
    fun testMultipleChildren() {
        val result = DecDsl.execute(dslContext) {
            organization {
                name = "Acme"
                user { name = "Alice"; email = "a@a.com" }
                user { name = "Bob"; email = "b@b.com" }
            }
        }

        val users = result.records("user")
        assertEquals(2, users.size, "Expected 2 users in result")

        // Both users must reference the same organization
        val orgId = (result.records("organization")[0] as OrganizationRecord).id
        assertNotNull(orgId)

        val userRows = dslContext.selectFrom(UserTable.USER).fetch()
        assertEquals(2, userRows.size)
        for (row in userRows) {
            assertEquals(orgId, row.get(UserTable.USER.ORGANIZATION_ID),
                "Each user must have organization_id = $orgId")
        }
    }

    // -----------------------------------------------------------------------
    // DSL-07: DB-generated PKs are non-null (and > 0) after insert
    // -----------------------------------------------------------------------

    @Test
    fun testGeneratedKeyPopulated() {
        val result = DecDsl.execute(dslContext) {
            organization { name = "KeyCheck" }
        }

        val org = result.records("organization")[0] as OrganizationRecord
        assertNotNull(org.id, "Generated PK must be non-null after insert")
        assertTrue((org.id ?: 0L) > 0L, "Generated PK must be positive, got ${org.id}")
    }

    // -----------------------------------------------------------------------
    // DSL-08: DslResult records appear in declaration order
    // -----------------------------------------------------------------------

    @Test
    fun testDeclarationOrder() {
        val result = DecDsl.execute(dslContext) {
            organization {
                name = "Alpha"
                user { name = "Alice"; email = "alice@alpha.com" }
            }
            organization {
                name = "Beta"
                user { name = "Bob"; email = "bob@beta.com" }
            }
        }

        val orgs = result.records("organization")
        assertEquals(2, orgs.size)
        assertEquals("Alpha", (orgs[0] as OrganizationRecord).name,
            "First organization should be Alpha")
        assertEquals("Beta", (orgs[1] as OrganizationRecord).name,
            "Second organization should be Beta")

        val users = result.records("user")
        assertEquals(2, users.size)
        assertEquals("Alice", (users[0] as UserRecord).name,
            "First user should be Alice (declared under Alpha)")
        assertEquals("Bob", (users[1] as UserRecord).name,
            "Second user should be Bob (declared under Beta)")
    }

    // -----------------------------------------------------------------------
    // Multiple root records — both inserted, both in DslResult
    // -----------------------------------------------------------------------

    @Test
    fun testMultipleRootRecords() {
        val result = DecDsl.execute(dslContext) {
            organization { name = "Org1" }
            organization { name = "Org2" }
        }

        val orgs = result.records("organization")
        assertEquals(2, orgs.size, "Expected 2 organizations in result")

        val dbRows = dslContext.selectFrom(OrganizationTable.ORGANIZATION).fetch()
        assertEquals(2, dbRows.size, "Expected 2 organization rows in DB")
    }

    // -----------------------------------------------------------------------
    // DSL-05 (indirect): FK integrity confirms parent was inserted before child
    // -----------------------------------------------------------------------

    @Test
    fun testTopologicalOrder() {
        DecDsl.execute(dslContext) {
            organization {
                name = "TopoCorp"
                user { name = "Carol"; email = "carol@topo.com" }
            }
        }

        // If topological order is correct, the FK constraint would have rejected the
        // insert of "user" before organization. Verify the data is consistent.
        val orgRows = dslContext.selectFrom(OrganizationTable.ORGANIZATION).fetch()
        val userRows = dslContext.selectFrom(UserTable.USER).fetch()

        assertEquals(1, orgRows.size)
        assertEquals(1, userRows.size)

        val orgId = orgRows[0].get(OrganizationTable.ORGANIZATION.ID)
        val userOrgId = userRows[0].get(UserTable.USER.ORGANIZATION_ID)

        assertNotNull(orgId)
        assertEquals(orgId, userOrgId,
            "Topological order ensures org is inserted before user — FK must match")
    }
}
