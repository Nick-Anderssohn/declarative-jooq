package com.nickanderssohn.todolist.jooq

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

    override fun getRecordType(): Class<TodoListRecord> = TodoListRecord::class.java

    override fun getPrimaryKey() =
        Internal.createUniqueKey(this, DSL.name("todo_list_pkey"), arrayOf(ID), true)

    override fun `as`(alias: String) = TodoListTable(DSL.name(alias))
    override fun `as`(alias: Name) = TodoListTable(alias)
}
