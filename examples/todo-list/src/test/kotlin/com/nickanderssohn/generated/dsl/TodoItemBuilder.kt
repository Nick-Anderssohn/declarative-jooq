package com.nickanderssohn.generated.dsl

import com.nickanderssohn.declarativejooq.DeclarativeJooqDsl
import com.nickanderssohn.declarativejooq.PendingPlaceholderRef
import com.nickanderssohn.declarativejooq.RecordBuilder
import com.nickanderssohn.declarativejooq.RecordGraph
import com.nickanderssohn.declarativejooq.RecordNode
import com.nickanderssohn.todolist.jooq.tables.AppUser
import com.nickanderssohn.todolist.jooq.tables.TodoItem
import com.nickanderssohn.todolist.jooq.tables.TodoItemLabel
import com.nickanderssohn.todolist.jooq.tables.TodoList
import com.nickanderssohn.todolist.jooq.tables.records.TodoItemRecord
import java.time.LocalDateTime
import kotlin.Boolean
import kotlin.Long
import kotlin.String
import kotlin.Unit
import kotlin.collections.List
import kotlin.collections.MutableList
import org.jooq.TableField

@DeclarativeJooqDsl
public class TodoItemBuilder(
  recordGraph: RecordGraph,
  parentNode: RecordNode?,
  parentFkFields: List<TableField<*, *>> = emptyList(),
  parentRefFields: List<TableField<*, *>> = emptyList(),
  isSelfReferential: Boolean = false,
) {
  public var todoListId: Long? = null

  public var title: String? = null

  public var completed: Boolean? = null

  public var createdAt: LocalDateTime? = null

  public var createdBy: AppUserResult? = null
    set(`value`) {
      field = value
      if (value != null) {
        recordBuilder.pendingPlaceholderRefs.add(PendingPlaceholderRef(listOf(TodoItem.TODO_ITEM.CREATED_BY as TableField<*, *>), listOf(AppUser.APP_USER.ID as TableField<*, *>), value.record))
      }
    }

  public var todoList: TodoListResult? = null
    set(`value`) {
      field = value
      if (value != null) {
        recordBuilder.pendingPlaceholderRefs.add(PendingPlaceholderRef(listOf(TodoItem.TODO_ITEM.TODO_LIST_ID as TableField<*, *>), listOf(TodoList.TODO_LIST.ID as TableField<*, *>), value.record))
      }
    }

  public var updatedBy: AppUserResult? = null
    set(`value`) {
      field = value
      if (value != null) {
        recordBuilder.pendingPlaceholderRefs.add(PendingPlaceholderRef(listOf(TodoItem.TODO_ITEM.UPDATED_BY as TableField<*, *>), listOf(AppUser.APP_USER.ID as TableField<*, *>), value.record))
      }
    }

  internal val recordBuilder: RecordBuilder<TodoItemRecord> = RecordBuilder(
    table = TodoItem.TODO_ITEM,
    parentNode = parentNode,
    parentFkFields = parentFkFields,
    parentRefFields = parentRefFields,
    recordGraph = recordGraph,
    isSelfReferential = isSelfReferential,
    buildRecord = {
      val record = TodoItemRecord()
      todoListId?.let { record.set(TodoItem.TODO_ITEM.TODO_LIST_ID, it) }
      title?.let { record.set(TodoItem.TODO_ITEM.TITLE, it) }
      completed?.let { record.set(TodoItem.TODO_ITEM.COMPLETED, it) }
      createdAt?.let { record.set(TodoItem.TODO_ITEM.CREATED_AT, it) }
      record
    }
  )

  private val childBlocks: MutableList<(RecordNode) -> Unit> = mutableListOf()

  public fun todoItem(block: TodoItemLabelBuilder.() -> Unit): TodoItemLabelResult {
    val builder = TodoItemLabelBuilder(recordGraph = recordBuilder.recordGraph, parentNode = null, parentFkFields = listOf(TodoItemLabel.TODO_ITEM_LABEL.TODO_ITEM_ID as TableField<*, *>), parentRefFields = listOf(TodoItem.TODO_ITEM.ID as TableField<*, *>))
    builder.block()
    val placeholderRecord = builder.recordBuilder.getOrBuildRecord()
    childBlocks.add { parentNode ->
      builder.recordBuilder.parentNode = parentNode
      builder.buildWithChildren()
    }
    return TodoItemLabelResult(placeholderRecord)
  }

  public fun buildWithChildren(): RecordNode {
    val node = recordBuilder.build()
    childBlocks.forEach { it(node) }
    return node
  }
}

public class TodoItemResult(
  internal val record: TodoItemRecord,
) {
  public val id: Long?
    get() = record.get(TodoItem.TODO_ITEM.ID)

  public val todoListId: Long?
    get() = record.get(TodoItem.TODO_ITEM.TODO_LIST_ID)

  public val title: String?
    get() = record.get(TodoItem.TODO_ITEM.TITLE)

  public val completed: Boolean?
    get() = record.get(TodoItem.TODO_ITEM.COMPLETED)

  public val createdBy: Long?
    get() = record.get(TodoItem.TODO_ITEM.CREATED_BY)

  public val updatedBy: Long?
    get() = record.get(TodoItem.TODO_ITEM.UPDATED_BY)

  public val createdAt: LocalDateTime?
    get() = record.get(TodoItem.TODO_ITEM.CREATED_AT)
}
