package com.nickanderssohn.todolist.model

import com.nickanderssohn.todolist.jooq.UserRecord

data class UserDto(val id: Long?, val name: String?, val email: String?)

fun UserRecord.toDto() = UserDto(id = id, name = name, email = email)
