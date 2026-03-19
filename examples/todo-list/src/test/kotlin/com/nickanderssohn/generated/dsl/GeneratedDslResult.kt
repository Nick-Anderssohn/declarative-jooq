package com.nickanderssohn.generated.dsl

import com.nickanderssohn.declarativejooq.DslResult
import com.nickanderssohn.todolist.jooq.SharedWithRecord
import com.nickanderssohn.todolist.jooq.TodoItemRecord
import com.nickanderssohn.todolist.jooq.TodoListRecord
import com.nickanderssohn.todolist.jooq.UserRecord
import kotlin.collections.List

public class GeneratedDslResult(
  private val result: DslResult,
) {
  public fun sharedWiths(): List<SharedWithResult> = result.records<SharedWithRecord>("shared_with").map { SharedWithResult(it as SharedWithRecord) }

  public fun todoItems(): List<TodoItemResult> = result.records<TodoItemRecord>("todo_item").map { TodoItemResult(it as TodoItemRecord) }

  public fun todoLists(): List<TodoListResult> = result.records<TodoListRecord>("todo_list").map { TodoListResult(it as TodoListRecord) }

  public fun appUsers(): List<AppUserResult> = result.records<UserRecord>("app_user").map { AppUserResult(it as UserRecord) }
}
