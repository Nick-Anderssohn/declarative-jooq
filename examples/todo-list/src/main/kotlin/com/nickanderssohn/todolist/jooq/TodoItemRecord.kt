package com.nickanderssohn.todolist.jooq

import org.jooq.impl.UpdatableRecordImpl

class TodoItemRecord : UpdatableRecordImpl<TodoItemRecord>(TodoItemTable.TODO_ITEM) {

    var id: Long?
        get() = get(TodoItemTable.TODO_ITEM.ID) as Long?
        set(value) { set(TodoItemTable.TODO_ITEM.ID, value) }

    var todoListId: Long?
        get() = get(TodoItemTable.TODO_ITEM.TODO_LIST_ID) as Long?
        set(value) { set(TodoItemTable.TODO_ITEM.TODO_LIST_ID, value) }

    var title: String?
        get() = get(TodoItemTable.TODO_ITEM.TITLE) as String?
        set(value) { set(TodoItemTable.TODO_ITEM.TITLE, value) }

    var completed: Boolean?
        get() = get(TodoItemTable.TODO_ITEM.COMPLETED) as Boolean?
        set(value) { set(TodoItemTable.TODO_ITEM.COMPLETED, value) }

    var createdBy: Long?
        get() = get(TodoItemTable.TODO_ITEM.CREATED_BY) as Long?
        set(value) { set(TodoItemTable.TODO_ITEM.CREATED_BY, value) }

    var updatedBy: Long?
        get() = get(TodoItemTable.TODO_ITEM.UPDATED_BY) as Long?
        set(value) { set(TodoItemTable.TODO_ITEM.UPDATED_BY, value) }
}
