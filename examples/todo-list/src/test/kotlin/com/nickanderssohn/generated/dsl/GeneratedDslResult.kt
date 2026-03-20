package com.nickanderssohn.generated.dsl

import com.nickanderssohn.declarativejooq.DslResult
import com.nickanderssohn.todolist.jooq.tables.records.AppUserRecord
import com.nickanderssohn.todolist.jooq.tables.records.SharedWithRecord
import com.nickanderssohn.todolist.jooq.tables.records.TodoItemRecord
import com.nickanderssohn.todolist.jooq.tables.records.TodoListRecord
import kotlin.collections.List

public class GeneratedDslResult(
  private val result: DslResult,
) {
  public fun appUsers(): List<AppUserResult> = result.records<AppUserRecord>("app_user").map { AppUserResult(it as AppUserRecord) }

  public fun sharedWiths(): List<SharedWithResult> = result.records<SharedWithRecord>("shared_with").map { SharedWithResult(it as SharedWithRecord) }

  public fun todoItems(): List<TodoItemResult> = result.records<TodoItemRecord>("todo_item").map { TodoItemResult(it as TodoItemRecord) }

  public fun todoLists(): List<TodoListResult> = result.records<TodoListRecord>("todo_list").map { TodoListResult(it as TodoListRecord) }
}
