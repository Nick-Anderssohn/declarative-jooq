package com.nickanderssohn.todolist.jooq

import org.jooq.ForeignKey
import org.jooq.Name
import org.jooq.TableField
import org.jooq.impl.DSL
import org.jooq.impl.Internal
import org.jooq.impl.SQLDataType
import org.jooq.impl.TableImpl

class TodoItemTable private constructor(alias: Name) :
    TableImpl<TodoItemRecord>(alias, null, null, null, DSL.comment("")) {

    companion object {
        @JvmField
        val TODO_ITEM = TodoItemTable(DSL.name("todo_item"))
    }

    val ID: TableField<TodoItemRecord, Long?> =
        createField(DSL.name("id"), SQLDataType.BIGINT.identity(true), this, "")

    val TODO_LIST_ID: TableField<TodoItemRecord, Long?> =
        createField(DSL.name("todo_list_id"), SQLDataType.BIGINT.nullable(false), this, "")

    val TITLE: TableField<TodoItemRecord, String?> =
        createField(DSL.name("title"), SQLDataType.VARCHAR(255).nullable(false), this, "")

    val COMPLETED: TableField<TodoItemRecord, Boolean?> =
        createField(DSL.name("completed"), SQLDataType.BOOLEAN, this, "")

    override fun getRecordType(): Class<TodoItemRecord> = TodoItemRecord::class.java

    override fun getPrimaryKey() =
        Internal.createUniqueKey(this, DSL.name("todo_item_pkey"), arrayOf(ID), true)

    override fun getReferences(): List<ForeignKey<TodoItemRecord, *>> = listOf(
        Internal.createForeignKey(
            this,
            DSL.name("todo_item_todo_list_id_fkey"),
            arrayOf(TODO_LIST_ID),
            TodoListTable.TODO_LIST.primaryKey,
            arrayOf(TodoListTable.TODO_LIST.ID),
            false
        )
    )

    override fun `as`(alias: String) = TodoItemTable(DSL.name(alias))
    override fun `as`(alias: Name) = TodoItemTable(alias)
}
