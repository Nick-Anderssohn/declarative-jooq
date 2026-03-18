package com.nickanderssohn.todolist.jooq

import org.jooq.ForeignKey
import org.jooq.Name
import org.jooq.Record
import org.jooq.TableField
import org.jooq.impl.DSL
import org.jooq.impl.Internal
import org.jooq.impl.SQLDataType
import org.jooq.impl.TableImpl

class TodoListTable private constructor(alias: Name) :
    TableImpl<TodoListRecord>(alias, null, null, null, DSL.comment("")) {

    companion object {
        @JvmField
        val TODO_LIST = TodoListTable(DSL.name("todo_list"))
    }

    val ID: TableField<TodoListRecord, Long?> =
        createField(DSL.name("id"), SQLDataType.BIGINT.identity(true), this, "")

    val TITLE: TableField<TodoListRecord, String?> =
        createField(DSL.name("title"), SQLDataType.VARCHAR(255).nullable(false), this, "")

    val DESCRIPTION: TableField<TodoListRecord, String?> =
        createField(DSL.name("description"), SQLDataType.CLOB, this, "")

    val CREATED_BY: TableField<TodoListRecord, Long?> =
        createField(DSL.name("created_by"), SQLDataType.BIGINT, this, "")

    val UPDATED_BY: TableField<TodoListRecord, Long?> =
        createField(DSL.name("updated_by"), SQLDataType.BIGINT, this, "")

    override fun getRecordType(): Class<TodoListRecord> = TodoListRecord::class.java

    override fun getPrimaryKey() =
        Internal.createUniqueKey(this, DSL.name("todo_list_pkey"), arrayOf(ID), true)

    override fun getReferences(): List<ForeignKey<TodoListRecord, *>> = listOf(
        Internal.createForeignKey(
            this,
            DSL.name("todo_list_created_by_fkey"),
            arrayOf(CREATED_BY),
            UserTable.APP_USER.primaryKey,
            arrayOf(UserTable.APP_USER.ID),
            false
        ),
        Internal.createForeignKey(
            this,
            DSL.name("todo_list_updated_by_fkey"),
            arrayOf(UPDATED_BY),
            UserTable.APP_USER.primaryKey,
            arrayOf(UserTable.APP_USER.ID),
            false
        )
    )

    override fun `as`(alias: String) = TodoListTable(DSL.name(alias))
    override fun `as`(alias: Name) = TodoListTable(alias)
}
