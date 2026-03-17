package com.nickanderssohn.todolist.repository

import com.nickanderssohn.todolist.jooq.TodoItemRecord
import com.nickanderssohn.todolist.jooq.TodoItemTable.Companion.TODO_ITEM
import org.jooq.DSLContext
import org.springframework.stereotype.Repository

@Repository
class TodoItemRepository(private val dsl: DSLContext) {

    fun findByListId(todoListId: Long): List<TodoItemRecord> =
        dsl.selectFrom(TODO_ITEM)
            .where(TODO_ITEM.TODO_LIST_ID.eq(todoListId))
            .fetchInto(TodoItemRecord::class.java)

    fun create(todoListId: Long, title: String): TodoItemRecord {
        val record = dsl.newRecord(TODO_ITEM)
        record.todoListId = todoListId
        record.title = title
        record.completed = false
        record.store()
        return record
    }

    fun updateCompleted(id: Long, completed: Boolean): TodoItemRecord? {
        dsl.update(TODO_ITEM)
            .set(TODO_ITEM.COMPLETED, completed)
            .where(TODO_ITEM.ID.eq(id))
            .execute()
        return dsl.selectFrom(TODO_ITEM)
            .where(TODO_ITEM.ID.eq(id))
            .fetchOneInto(TodoItemRecord::class.java)
    }

    fun delete(id: Long) {
        dsl.deleteFrom(TODO_ITEM)
            .where(TODO_ITEM.ID.eq(id))
            .execute()
    }
}
