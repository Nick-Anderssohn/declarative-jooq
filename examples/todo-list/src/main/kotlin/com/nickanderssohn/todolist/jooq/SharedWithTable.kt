package com.nickanderssohn.todolist.jooq

import org.jooq.ForeignKey
import org.jooq.Name
import org.jooq.TableField
import org.jooq.impl.DSL
import org.jooq.impl.Internal
import org.jooq.impl.SQLDataType
import org.jooq.impl.TableImpl

class SharedWithTable private constructor(alias: Name) :
    TableImpl<SharedWithRecord>(alias, null, null, null, DSL.comment("")) {

    companion object {
        @JvmField
        val SHARED_WITH = SharedWithTable(DSL.name("shared_with"))
    }

    val ID: TableField<SharedWithRecord, Long?> =
        createField(DSL.name("id"), SQLDataType.BIGINT.identity(true), this, "")

    val TODO_LIST_ID: TableField<SharedWithRecord, Long?> =
        createField(DSL.name("todo_list_id"), SQLDataType.BIGINT.nullable(false), this, "")

    val USER_ID: TableField<SharedWithRecord, Long?> =
        createField(DSL.name("user_id"), SQLDataType.BIGINT.nullable(false), this, "")

    override fun getRecordType(): Class<SharedWithRecord> = SharedWithRecord::class.java

    override fun getPrimaryKey() =
        Internal.createUniqueKey(this, DSL.name("shared_with_pkey"), arrayOf(ID), true)

    override fun getReferences(): List<ForeignKey<SharedWithRecord, *>> = listOf(
        Internal.createForeignKey(
            this,
            DSL.name("shared_with_todo_list_id_fkey"),
            arrayOf(TODO_LIST_ID),
            TodoListTable.TODO_LIST.primaryKey,
            arrayOf(TodoListTable.TODO_LIST.ID),
            false
        ),
        Internal.createForeignKey(
            this,
            DSL.name("shared_with_user_id_fkey"),
            arrayOf(USER_ID),
            UserTable.APP_USER.primaryKey,
            arrayOf(UserTable.APP_USER.ID),
            false
        )
    )

    override fun `as`(alias: String) = SharedWithTable(DSL.name(alias))
    override fun `as`(alias: Name) = SharedWithTable(alias)
}
