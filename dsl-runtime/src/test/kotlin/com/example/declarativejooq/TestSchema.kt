package com.example.declarativejooq

import org.h2.jdbcx.JdbcDataSource
import org.jooq.DSLContext
import org.jooq.ForeignKey
import org.jooq.Identity
import org.jooq.SQLDialect
import org.jooq.TableField
import org.jooq.UniqueKey
import org.jooq.impl.DSL
import org.jooq.impl.Internal
import org.jooq.impl.SQLDataType
import org.jooq.impl.TableImpl
import org.jooq.impl.UpdatableRecordImpl

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
// OrganizationRecord
// ---------------------------------------------------------------------------

/**
 * No-arg constructor required by jOOQ's reflective record factory.
 * Must be declared after OrganizationTable to avoid forward-reference at class load.
 */
class OrganizationRecord() :
    UpdatableRecordImpl<OrganizationRecord>(OrganizationTable.ORGANIZATION) {

    var id: Long?
        get() = get(OrganizationTable.ORGANIZATION.ID)
        set(value) = set(OrganizationTable.ORGANIZATION.ID, value)

    var name: String?
        get() = get(OrganizationTable.ORGANIZATION.NAME)
        set(value) = set(OrganizationTable.ORGANIZATION.NAME, value)
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
// AppUserRecord
// ---------------------------------------------------------------------------

/**
 * No-arg constructor required by jOOQ's reflective record factory.
 * Must be declared after AppUserTable to avoid forward-reference at class load.
 */
class AppUserRecord() :
    UpdatableRecordImpl<AppUserRecord>(AppUserTable.APP_USER) {

    var id: Long?
        get() = get(AppUserTable.APP_USER.ID)
        set(value) = set(AppUserTable.APP_USER.ID, value)

    var name: String?
        get() = get(AppUserTable.APP_USER.NAME)
        set(value) = set(AppUserTable.APP_USER.NAME, value)

    var email: String?
        get() = get(AppUserTable.APP_USER.EMAIL)
        set(value) = set(AppUserTable.APP_USER.EMAIL, value)

    var organizationId: Long?
        get() = get(AppUserTable.APP_USER.ORGANIZATION_ID)
        set(value) = set(AppUserTable.APP_USER.ORGANIZATION_ID, value)
}

// ---------------------------------------------------------------------------
// TestSchema — database setup
// ---------------------------------------------------------------------------

object TestSchema {
    private const val JDBC_URL =
        "jdbc:h2:mem:declarative_jooq_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=FALSE"

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
