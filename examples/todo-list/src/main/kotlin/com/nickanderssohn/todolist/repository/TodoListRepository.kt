package com.nickanderssohn.todolist.repository

import com.nickanderssohn.todolist.jooq.TodoListRecord
import com.nickanderssohn.todolist.jooq.TodoListTable.Companion.TODO_LIST
import org.jooq.DSLContext
import org.springframework.stereotype.Repository

@Repository
class TodoListRepository(private val dsl: DSLContext) {

    fun findAll(): List<TodoListRecord> =
        dsl.selectFrom(TODO_LIST).fetchInto(TodoListRecord::class.java)

    fun findById(id: Long): TodoListRecord? =
        dsl.selectFrom(TODO_LIST)
            .where(TODO_LIST.ID.eq(id))
            .fetchOneInto(TodoListRecord::class.java)

    fun create(title: String, description: String?, createdBy: Long? = null): TodoListRecord {
        val record = dsl.newRecord(TODO_LIST)
        record.title = title
        record.description = description
        record.createdBy = createdBy
        record.store()
        return record
    }

    fun delete(id: Long) {
        dsl.deleteFrom(TODO_LIST)
            .where(TODO_LIST.ID.eq(id))
            .execute()
    }
}
