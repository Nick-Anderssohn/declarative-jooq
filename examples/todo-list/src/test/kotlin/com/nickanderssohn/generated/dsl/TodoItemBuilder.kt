package com.nickanderssohn.generated.dsl

import com.nickanderssohn.declarativejooq.PendingPlaceholderRef
import com.nickanderssohn.declarativejooq.RecordBuilder
import com.nickanderssohn.declarativejooq.RecordGraph
import com.nickanderssohn.declarativejooq.RecordNode
import com.nickanderssohn.todolist.jooq.TodoItemRecord
import com.nickanderssohn.todolist.jooq.TodoItemTable
import kotlin.Boolean
import kotlin.Long
import kotlin.String
import kotlin.Unit
import kotlin.collections.List
import kotlin.collections.MutableList
import org.jooq.TableField

public class TodoItemBuilder(
  recordGraph: RecordGraph,
  parentNode: RecordNode?,
  parentFkFields: List<TableField<*, *>> = emptyList(),
  isSelfReferential: Boolean = false,
) : RecordBuilder<TodoItemRecord>(table = TodoItemTable.TODO_ITEM, parentNode = parentNode, parentFkFields = parentFkFields, recordGraph = recordGraph, isSelfReferential = isSelfReferential) {
  public var todoListId: Long? = null

  public var title: String? = null

  public var completed: Boolean? = null

  public var todoList: TodoListResult? = null
    set(`value`) {
      field = value
      if (value != null) {
        pendingPlaceholderRefs.add(PendingPlaceholderRef(listOf(TodoItemTable.TODO_ITEM.TODO_LIST_ID as TableField<*, *>), value.record))
      }
    }

  public var createdBy: AppUserResult? = null
    set(`value`) {
      field = value
      if (value != null) {
        pendingPlaceholderRefs.add(PendingPlaceholderRef(listOf(TodoItemTable.TODO_ITEM.CREATED_BY as TableField<*, *>), value.record))
      }
    }

  public var updatedBy: AppUserResult? = null
    set(`value`) {
      field = value
      if (value != null) {
        pendingPlaceholderRefs.add(PendingPlaceholderRef(listOf(TodoItemTable.TODO_ITEM.UPDATED_BY as TableField<*, *>), value.record))
      }
    }

  private val childBlocks: MutableList<(RecordNode) -> Unit> = mutableListOf()

  override fun buildRecord(): TodoItemRecord {
    val record = TodoItemRecord()
    record.set(TodoItemTable.TODO_ITEM.TODO_LIST_ID, todoListId)
    record.set(TodoItemTable.TODO_ITEM.TITLE, title)
    record.set(TodoItemTable.TODO_ITEM.COMPLETED, completed)
    return record
  }

  public fun buildWithChildren(): RecordNode {
    val node = build()
    childBlocks.forEach { it(node) }
    return node
  }
}

public class TodoItemResult(
  internal val record: TodoItemRecord,
) {
  public val id: Long?
    get() = record.get(TodoItemTable.TODO_ITEM.ID)

  public val todoListId: Long?
    get() = record.get(TodoItemTable.TODO_ITEM.TODO_LIST_ID)

  public val title: String?
    get() = record.get(TodoItemTable.TODO_ITEM.TITLE)

  public val completed: Boolean?
    get() = record.get(TodoItemTable.TODO_ITEM.COMPLETED)

  public val createdBy: Long?
    get() = record.get(TodoItemTable.TODO_ITEM.CREATED_BY)

  public val updatedBy: Long?
    get() = record.get(TodoItemTable.TODO_ITEM.UPDATED_BY)
}
