package com.nickanderssohn.todolist.repository

import com.nickanderssohn.todolist.jooq.UserRecord
import com.nickanderssohn.todolist.jooq.UserTable.Companion.APP_USER
import org.jooq.DSLContext
import org.springframework.stereotype.Repository

@Repository
class UserRepository(private val dsl: DSLContext) {

    fun findAll(): List<UserRecord> =
        dsl.selectFrom(APP_USER).fetchInto(UserRecord::class.java)

    fun create(name: String, email: String): UserRecord {
        val record = dsl.newRecord(APP_USER)
        record.name = name
        record.email = email
        record.store()
        return record
    }
}
