package com.nickanderssohn.todolist.model

import com.nickanderssohn.todolist.jooq.tables.records.TodoItemRecord
import com.nickanderssohn.todolist.jooq.tables.records.TodoListRecord

data class TodoListDto(
    val id: Long?,
    val title: String?,
    val description: String?,
    val createdBy: Long? = null
)

data class TodoItemDto(
    val id: Long?,
    val todoListId: Long?,
    val title: String?,
    val completed: Boolean?,
    val createdBy: Long? = null
)

fun TodoListRecord.toDto() = TodoListDto(id = id, title = title, description = description, createdBy = createdBy)
fun TodoItemRecord.toDto() = TodoItemDto(id = id, todoListId = todoListId, title = title, completed = completed, createdBy = createdBy)
