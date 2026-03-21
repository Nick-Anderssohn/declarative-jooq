package com.nickanderssohn.generated.dsl

import com.nickanderssohn.declarativejooq.DslResult
import com.nickanderssohn.todolist.jooq.tables.records.AppUserRecord
import com.nickanderssohn.todolist.jooq.tables.records.LabelRecord
import com.nickanderssohn.todolist.jooq.tables.records.SharedWithRecord
import com.nickanderssohn.todolist.jooq.tables.records.TodoItemLabelRecord
import com.nickanderssohn.todolist.jooq.tables.records.TodoItemRecord
import com.nickanderssohn.todolist.jooq.tables.records.TodoListRecord
import kotlin.collections.List

public class GeneratedDslResult(
  private val result: DslResult,
) {
  public fun appUsers(): List<AppUserResult> = result.records<AppUserRecord>("app_user").map { AppUserResult(it as AppUserRecord) }

  public fun labels(): List<LabelResult> = result.records<LabelRecord>("label").map { LabelResult(it as LabelRecord) }

  public fun sharedWiths(): List<SharedWithResult> = result.records<SharedWithRecord>("shared_with").map { SharedWithResult(it as SharedWithRecord) }

  public fun todoItems(): List<TodoItemResult> = result.records<TodoItemRecord>("todo_item").map { TodoItemResult(it as TodoItemRecord) }

  public fun todoItemLabels(): List<TodoItemLabelResult> = result.records<TodoItemLabelRecord>("todo_item_label").map { TodoItemLabelResult(it as TodoItemLabelRecord) }

  public fun todoLists(): List<TodoListResult> = result.records<TodoListRecord>("todo_list").map { TodoListResult(it as TodoListRecord) }
}
