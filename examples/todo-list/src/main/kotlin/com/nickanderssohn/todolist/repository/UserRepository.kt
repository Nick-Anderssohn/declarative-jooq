package com.nickanderssohn.todolist.repository

import com.nickanderssohn.todolist.jooq.tables.AppUser.Companion.APP_USER
import com.nickanderssohn.todolist.jooq.tables.records.AppUserRecord
import org.jooq.DSLContext
import org.springframework.stereotype.Repository

@Repository
class UserRepository(private val dsl: DSLContext) {

    fun findAll(): List<AppUserRecord> =
        dsl.selectFrom(APP_USER).fetchInto(AppUserRecord::class.java)

    fun create(name: String, email: String): AppUserRecord {
        val record = dsl.newRecord(APP_USER)
        record.name = name
        record.email = email
        record.store()
        return record
    }
}
