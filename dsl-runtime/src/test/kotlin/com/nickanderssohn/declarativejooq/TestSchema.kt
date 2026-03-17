package com.nickanderssohn.declarativejooq

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
// UserTable ("user" is a reserved keyword — always quote in SQL)
// ---------------------------------------------------------------------------

class UserTable private constructor() : TableImpl<UserRecord>(
    DSL.name("user")
) {
    val ID: TableField<UserRecord, Long?> =
        createField(DSL.name("id"), SQLDataType.BIGINT.identity(true), this)

    val NAME: TableField<UserRecord, String?> =
        createField(DSL.name("name"), SQLDataType.VARCHAR(255).nullable(false), this)

    val EMAIL: TableField<UserRecord, String?> =
        createField(DSL.name("email"), SQLDataType.VARCHAR(255).nullable(false), this)

    val ORGANIZATION_ID: TableField<UserRecord, Long?> =
        createField(DSL.name("organization_id"), SQLDataType.BIGINT.nullable(false), this)

    override fun getRecordType(): Class<UserRecord> = UserRecord::class.java

    override fun getPrimaryKey(): UniqueKey<UserRecord> =
        Internal.createUniqueKey(this, DSL.name("pk_user"), arrayOf(ID), true)

    override fun getIdentity(): Identity<UserRecord, *> =
        Internal.createIdentity(this, ID)

    override fun getReferences(): List<ForeignKey<UserRecord, *>> = listOf(
        Internal.createForeignKey(
            this,
            DSL.name("fk_user_organization"),
            arrayOf(ORGANIZATION_ID),
            OrganizationTable.ORGANIZATION.primaryKey,
            arrayOf(OrganizationTable.ORGANIZATION.ID),
            false
        )
    )

    companion object {
        val USER = UserTable()
    }
}

// ---------------------------------------------------------------------------
// UserRecord
// ---------------------------------------------------------------------------

/**
 * No-arg constructor required by jOOQ's reflective record factory.
 * Must be declared after UserTable to avoid forward-reference at class load.
 */
class UserRecord() :
    UpdatableRecordImpl<UserRecord>(UserTable.USER) {

    var id: Long?
        get() = get(UserTable.USER.ID)
        set(value) = set(UserTable.USER.ID, value)

    var name: String?
        get() = get(UserTable.USER.NAME)
        set(value) = set(UserTable.USER.NAME, value)

    var email: String?
        get() = get(UserTable.USER.EMAIL)
        set(value) = set(UserTable.USER.EMAIL, value)

    var organizationId: Long?
        get() = get(UserTable.USER.ORGANIZATION_ID)
        set(value) = set(UserTable.USER.ORGANIZATION_ID, value)
}

// ---------------------------------------------------------------------------
// CategoryTable (self-referential FK: parent_id -> category.id)
// ---------------------------------------------------------------------------

class CategoryTable private constructor() : TableImpl<CategoryRecord>(DSL.name("category")) {
    val ID: TableField<CategoryRecord, Long?> =
        createField(DSL.name("id"), SQLDataType.BIGINT.identity(true), this)
    val NAME: TableField<CategoryRecord, String?> =
        createField(DSL.name("name"), SQLDataType.VARCHAR(255).nullable(false), this)
    val PARENT_ID: TableField<CategoryRecord, Long?> =
        createField(DSL.name("parent_id"), SQLDataType.BIGINT.nullable(true), this)

    override fun getRecordType(): Class<CategoryRecord> = CategoryRecord::class.java

    override fun getPrimaryKey(): UniqueKey<CategoryRecord> =
        Internal.createUniqueKey(this, DSL.name("pk_category"), arrayOf(ID), true)

    override fun getIdentity(): Identity<CategoryRecord, *> =
        Internal.createIdentity(this, ID)

    override fun getReferences(): List<ForeignKey<CategoryRecord, *>> = listOf(
        Internal.createForeignKey(
            this, DSL.name("fk_category_parent"), arrayOf(PARENT_ID),
            this.primaryKey, arrayOf(ID), false
        )
    )

    companion object {
        val CATEGORY = CategoryTable()
    }
}

class CategoryRecord() : UpdatableRecordImpl<CategoryRecord>(CategoryTable.CATEGORY) {
    var id: Long?
        get() = get(CategoryTable.CATEGORY.ID)
        set(value) = set(CategoryTable.CATEGORY.ID, value)
    var name: String?
        get() = get(CategoryTable.CATEGORY.NAME)
        set(value) = set(CategoryTable.CATEGORY.NAME, value)
    var parentId: Long?
        get() = get(CategoryTable.CATEGORY.PARENT_ID)
        set(value) = set(CategoryTable.CATEGORY.PARENT_ID, value)
}

// ---------------------------------------------------------------------------
// TaskTable (two FKs to "user": created_by and updated_by)
// ---------------------------------------------------------------------------

class TaskTable private constructor() : TableImpl<TaskRecord>(DSL.name("task")) {
    val ID: TableField<TaskRecord, Long?> =
        createField(DSL.name("id"), SQLDataType.BIGINT.identity(true), this)
    val TITLE: TableField<TaskRecord, String?> =
        createField(DSL.name("title"), SQLDataType.VARCHAR(255).nullable(false), this)
    val CREATED_BY: TableField<TaskRecord, Long?> =
        createField(DSL.name("created_by"), SQLDataType.BIGINT.nullable(false), this)
    val UPDATED_BY: TableField<TaskRecord, Long?> =
        createField(DSL.name("updated_by"), SQLDataType.BIGINT.nullable(true), this)

    override fun getRecordType(): Class<TaskRecord> = TaskRecord::class.java

    override fun getPrimaryKey(): UniqueKey<TaskRecord> =
        Internal.createUniqueKey(this, DSL.name("pk_task"), arrayOf(ID), true)

    override fun getIdentity(): Identity<TaskRecord, *> =
        Internal.createIdentity(this, ID)

    override fun getReferences(): List<ForeignKey<TaskRecord, *>> = listOf(
        Internal.createForeignKey(
            this, DSL.name("fk_task_created_by"), arrayOf(CREATED_BY),
            UserTable.USER.primaryKey, arrayOf(UserTable.USER.ID), false
        ),
        Internal.createForeignKey(
            this, DSL.name("fk_task_updated_by"), arrayOf(UPDATED_BY),
            UserTable.USER.primaryKey, arrayOf(UserTable.USER.ID), false
        )
    )

    companion object {
        val TASK = TaskTable()
    }
}

class TaskRecord() : UpdatableRecordImpl<TaskRecord>(TaskTable.TASK) {
    var id: Long?
        get() = get(TaskTable.TASK.ID)
        set(value) = set(TaskTable.TASK.ID, value)
    var title: String?
        get() = get(TaskTable.TASK.TITLE)
        set(value) = set(TaskTable.TASK.TITLE, value)
    var createdBy: Long?
        get() = get(TaskTable.TASK.CREATED_BY)
        set(value) = set(TaskTable.TASK.CREATED_BY, value)
    var updatedBy: Long?
        get() = get(TaskTable.TASK.UPDATED_BY)
        set(value) = set(TaskTable.TASK.UPDATED_BY, value)
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
            CREATE TABLE IF NOT EXISTS "user" (
                id              BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                name            VARCHAR(255) NOT NULL,
                email           VARCHAR(255) NOT NULL,
                organization_id BIGINT NOT NULL REFERENCES organization(id)
            )
            """.trimIndent()
        )

        ctx.execute(
            """
            CREATE TABLE IF NOT EXISTS category (
                id        BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                name      VARCHAR(255) NOT NULL,
                parent_id BIGINT REFERENCES category(id)
            )
            """.trimIndent()
        )

        ctx.execute(
            """
            CREATE TABLE IF NOT EXISTS task (
                id         BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                title      VARCHAR(255) NOT NULL,
                created_by BIGINT NOT NULL REFERENCES "user"(id),
                updated_by BIGINT REFERENCES "user"(id)
            )
            """.trimIndent()
        )

        return ctx
    }
}
