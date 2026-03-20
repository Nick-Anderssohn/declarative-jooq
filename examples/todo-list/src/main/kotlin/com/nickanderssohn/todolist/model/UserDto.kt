package com.nickanderssohn.todolist.model

import com.nickanderssohn.todolist.jooq.tables.records.AppUserRecord

data class UserDto(val id: Long?, val name: String?, val email: String?)

fun AppUserRecord.toDto() = UserDto(id = id, name = name, email = email)
