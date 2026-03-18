package com.nickanderssohn.todolist.jooq

import org.jooq.impl.UpdatableRecordImpl

class UserRecord : UpdatableRecordImpl<UserRecord>(UserTable.APP_USER) {

    var id: Long?
        get() = get(UserTable.APP_USER.ID) as Long?
        set(value) { set(UserTable.APP_USER.ID, value) }

    var name: String?
        get() = get(UserTable.APP_USER.NAME) as String?
        set(value) { set(UserTable.APP_USER.NAME, value) }

    var email: String?
        get() = get(UserTable.APP_USER.EMAIL) as String?
        set(value) { set(UserTable.APP_USER.EMAIL, value) }
}
