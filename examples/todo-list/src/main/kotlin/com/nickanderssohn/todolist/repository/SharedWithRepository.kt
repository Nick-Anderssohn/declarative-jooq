package com.nickanderssohn.todolist.repository

import com.nickanderssohn.todolist.jooq.tables.SharedWith.Companion.SHARED_WITH
import com.nickanderssohn.todolist.jooq.tables.records.SharedWithRecord
import org.jooq.DSLContext
import org.springframework.stereotype.Repository

@Repository
class SharedWithRepository(private val dsl: DSLContext) {

    fun findByListId(todoListId: Long): List<SharedWithRecord> =
        dsl.selectFrom(SHARED_WITH)
            .where(SHARED_WITH.TODO_LIST_ID.eq(todoListId))
            .fetchInto(SharedWithRecord::class.java)

    fun create(todoListId: Long, userId: Long): SharedWithRecord {
        val record = dsl.newRecord(SHARED_WITH)
        record.todoListId = todoListId
        record.userId = userId
        record.store()
        return record
    }
}
