package com.nickanderssohn.todolist.jooq

import org.jooq.impl.UpdatableRecordImpl

class SharedWithRecord : UpdatableRecordImpl<SharedWithRecord>(SharedWithTable.SHARED_WITH) {

    var id: Long?
        get() = get(SharedWithTable.SHARED_WITH.ID) as Long?
        set(value) { set(SharedWithTable.SHARED_WITH.ID, value) }

    var todoListId: Long?
        get() = get(SharedWithTable.SHARED_WITH.TODO_LIST_ID) as Long?
        set(value) { set(SharedWithTable.SHARED_WITH.TODO_LIST_ID, value) }

    var userId: Long?
        get() = get(SharedWithTable.SHARED_WITH.USER_ID) as Long?
        set(value) { set(SharedWithTable.SHARED_WITH.USER_ID, value) }
}
