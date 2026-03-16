package com.example.declarativejooq

import org.h2.jdbcx.JdbcDataSource
import org.jooq.DSLContext
import org.jooq.DataType
import org.jooq.ForeignKey
import org.jooq.Identity
import org.jooq.Record
import org.jooq.SQLDialect
import org.jooq.Schema
import org.jooq.Table
import org.jooq.TableField
import org.jooq.UniqueKey
import org.jooq.impl.DSL
import org.jooq.impl.Internal
import org.jooq.impl.SQLDataType
import org.jooq.impl.TableImpl
import org.jooq.impl.UpdatableRecordImpl

// ---------------------------------------------------------------------------
// OrganizationRecord
// ---------------------------------------------------------------------------

class OrganizationRecord(table: OrganizationTable) :
    UpdatableRecordImpl<OrganizationRecord>(table) {

    var id: Long?
        get() = get(table().ID)
        set(value) = set(table().ID, value)

    var name: String?
        get() = get(table().NAME)
        set(value) = set(table().NAME, value)

    @Suppress("UNCHECKED_CAST")
    private fun table(): OrganizationTable = table as OrganizationTable
}

// ---------------------------------------------------------------------------
// OrganizationTable
// ---------------------------------------------------------------------------

class OrganizationTable private constructor() : TableImpl<OrganizationRecord>(
    DSL.name("organization")
) {
    val ID: TableField<OrganizationRecord, Long?> =
        createField(DSL.name("id"), SQLDataType.BIGINT.identity(true), this)

    val NAME: TableField<OrganizationRecord, String?> =
        createField(DSL.name("name"), SQLDataType.VARCHAR(255).nullable(false), this)

    override fun getRecordType(): Class<OrganizationRecord> = OrganizationRecord::class.java

    override fun getPrimaryKey(): UniqueKey<OrganizationRecord> =
        Internal.createUniqueKey(this, DSL.name("pk_organization"), arrayOf(ID), true)

    override fun getIdentity(): Identity<OrganizationRecord, *> =
        Internal.createIdentity(this, ID)

    override fun getReferences(): List<ForeignKey<OrganizationRecord, *>> = emptyList()

    companion object {
        val ORGANIZATION = OrganizationTable()
    }
}

// ---------------------------------------------------------------------------
// AppUserRecord
// ---------------------------------------------------------------------------

class AppUserRecord(table: AppUserTable) :
    UpdatableRecordImpl<AppUserRecord>(table) {

    var id: Long?
        get() = get(table().ID)
        set(value) = set(table().ID, value)

    var name: String?
        get() = get(table().NAME)
        set(value) = set(table().NAME, value)

    var email: String?
        get() = get(table().EMAIL)
        set(value) = set(table().EMAIL, value)

    var organizationId: Long?
        get() = get(table().ORGANIZATION_ID)
        set(value) = set(table().ORGANIZATION_ID, value)

    @Suppress("UNCHECKED_CAST")
    private fun table(): AppUserTable = table as AppUserTable
}

// ---------------------------------------------------------------------------
// AppUserTable
// ---------------------------------------------------------------------------

class AppUserTable private constructor() : TableImpl<AppUserRecord>(
    DSL.name("app_user")
) {
    val ID: TableField<AppUserRecord, Long?> =
        createField(DSL.name("id"), SQLDataType.BIGINT.identity(true), this)

    val NAME: TableField<AppUserRecord, String?> =
        createField(DSL.name("name"), SQLDataType.VARCHAR(255).nullable(false), this)

    val EMAIL: TableField<AppUserRecord, String?> =
        createField(DSL.name("email"), SQLDataType.VARCHAR(255).nullable(false), this)

    val ORGANIZATION_ID: TableField<AppUserRecord, Long?> =
        createField(DSL.name("organization_id"), SQLDataType.BIGINT.nullable(false), this)

    override fun getRecordType(): Class<AppUserRecord> = AppUserRecord::class.java

    override fun getPrimaryKey(): UniqueKey<AppUserRecord> =
        Internal.createUniqueKey(this, DSL.name("pk_app_user"), arrayOf(ID), true)

    override fun getIdentity(): Identity<AppUserRecord, *> =
        Internal.createIdentity(this, ID)

    override fun getReferences(): List<ForeignKey<AppUserRecord, *>> = listOf(
        Internal.createForeignKey(
            this,
            DSL.name("fk_app_user_organization"),
            arrayOf(ORGANIZATION_ID),
            OrganizationTable.ORGANIZATION.primaryKey,
            arrayOf(OrganizationTable.ORGANIZATION.ID),
            false
        )
    )

    companion object {
        val APP_USER = AppUserTable()
    }
}

// ---------------------------------------------------------------------------
// TestSchema — database setup
// ---------------------------------------------------------------------------

object TestSchema {
    private const val JDBC_URL =
        "jdbc:h2:mem:declarative_jooq_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"

    fun createDslContext(): DSLContext {
        val ds = JdbcDataSource().apply { setURL(JDBC_URL) }
        val ctx = DSL.using(ds, SQLDialect.H2)

        ctx.execute(
            """
            CREATE TABLE IF NOT EXISTS organization (
                id   BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                name VARCHAR(255) NOT NULL
            )
            """.trimIndent()
        )

        ctx.execute(
            """
            CREATE TABLE IF NOT EXISTS app_user (
                id              BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                name            VARCHAR(255) NOT NULL,
                email           VARCHAR(255) NOT NULL,
                organization_id BIGINT NOT NULL REFERENCES organization(id)
            )
            """.trimIndent()
        )

        return ctx
    }
}
