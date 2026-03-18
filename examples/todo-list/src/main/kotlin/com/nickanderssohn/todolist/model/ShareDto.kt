package com.nickanderssohn.todolist.model

import com.nickanderssohn.todolist.jooq.SharedWithRecord

data class ShareDto(val id: Long?, val todoListId: Long?, val userId: Long?)

fun SharedWithRecord.toDto() = ShareDto(id = id, todoListId = todoListId, userId = userId)
