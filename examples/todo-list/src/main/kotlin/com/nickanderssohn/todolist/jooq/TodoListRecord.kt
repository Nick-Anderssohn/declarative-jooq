package com.nickanderssohn.todolist.jooq

import org.jooq.impl.UpdatableRecordImpl

class TodoListRecord : UpdatableRecordImpl<TodoListRecord>(TodoListTable.TODO_LIST) {

    var id: Long?
        get() = get(TodoListTable.TODO_LIST.ID) as Long?
        set(value) { set(TodoListTable.TODO_LIST.ID, value) }

    var title: String?
        get() = get(TodoListTable.TODO_LIST.TITLE) as String?
        set(value) { set(TodoListTable.TODO_LIST.TITLE, value) }

    var description: String?
        get() = get(TodoListTable.TODO_LIST.DESCRIPTION) as String?
        set(value) { set(TodoListTable.TODO_LIST.DESCRIPTION, value) }

    var createdBy: Long?
        get() = get(TodoListTable.TODO_LIST.CREATED_BY) as Long?
        set(value) { set(TodoListTable.TODO_LIST.CREATED_BY, value) }

    var updatedBy: Long?
        get() = get(TodoListTable.TODO_LIST.UPDATED_BY) as Long?
        set(value) { set(TodoListTable.TODO_LIST.UPDATED_BY, value) }
}
