package com.nickanderssohn.todolist.jooq

import org.jooq.Name
import org.jooq.TableField
import org.jooq.impl.DSL
import org.jooq.impl.Internal
import org.jooq.impl.SQLDataType
import org.jooq.impl.TableImpl

class UserTable private constructor(alias: Name) :
    TableImpl<UserRecord>(alias, null, null, null, DSL.comment("")) {

    companion object {
        @JvmField
        val APP_USER = UserTable(DSL.name("app_user"))
    }

    val ID: TableField<UserRecord, Long?> =
        createField(DSL.name("id"), SQLDataType.BIGINT.identity(true), this, "")

    val NAME: TableField<UserRecord, String?> =
        createField(DSL.name("name"), SQLDataType.VARCHAR(255).nullable(false), this, "")

    val EMAIL: TableField<UserRecord, String?> =
        createField(DSL.name("email"), SQLDataType.VARCHAR(255).nullable(false), this, "")

    override fun getRecordType(): Class<UserRecord> = UserRecord::class.java

    override fun getPrimaryKey() =
        Internal.createUniqueKey(this, DSL.name("app_user_pkey"), arrayOf(ID), true)

    override fun `as`(alias: String) = UserTable(DSL.name(alias))
    override fun `as`(alias: Name) = UserTable(alias)
}
