package com.nickanderssohn.generated.dsl

import com.nickanderssohn.declarativejooq.PendingPlaceholderRef
import com.nickanderssohn.declarativejooq.RecordBuilder
import com.nickanderssohn.declarativejooq.RecordGraph
import com.nickanderssohn.declarativejooq.RecordNode
import com.nickanderssohn.todolist.jooq.tables.SharedWith
import com.nickanderssohn.todolist.jooq.tables.TodoItem
import com.nickanderssohn.todolist.jooq.tables.TodoList
import com.nickanderssohn.todolist.jooq.tables.records.TodoListRecord
import java.time.LocalDateTime
import kotlin.Boolean
import kotlin.Long
import kotlin.String
import kotlin.Unit
import kotlin.collections.MutableList
import org.jooq.TableField

public class TodoListBuilder(
  recordGraph: RecordGraph,
  parentNode: RecordNode?,
  parentFkField: TableField<*, *>?,
  isSelfReferential: Boolean = false,
) : RecordBuilder<TodoListRecord>(table = TodoList.TODO_LIST, parentNode = parentNode, parentFkField = parentFkField, recordGraph = recordGraph, isSelfReferential = isSelfReferential) {
  public var title: String? = null

  public var description: String? = null

  public var createdAt: LocalDateTime? = null

  public var createdBy: AppUserResult? = null
    set(`value`) {
      field = value
      if (value != null) {
        pendingPlaceholderRefs.add(PendingPlaceholderRef(TodoList.TODO_LIST.CREATED_BY as TableField<*, *>, value.record))
      }
    }

  public var updatedBy: AppUserResult? = null
    set(`value`) {
      field = value
      if (value != null) {
        pendingPlaceholderRefs.add(PendingPlaceholderRef(TodoList.TODO_LIST.UPDATED_BY as TableField<*, *>, value.record))
      }
    }

  private val childBlocks: MutableList<(RecordNode) -> Unit> = mutableListOf()

  override fun buildRecord(): TodoListRecord {
    val record = TodoListRecord()
    title?.let { record.set(TodoList.TODO_LIST.TITLE, it) }
    description?.let { record.set(TodoList.TODO_LIST.DESCRIPTION, it) }
    createdAt?.let { record.set(TodoList.TODO_LIST.CREATED_AT, it) }
    return record
  }

  public fun sharedWith(block: SharedWithBuilder.() -> Unit): SharedWithResult {
    val builder = SharedWithBuilder(recordGraph = recordGraph, parentNode = null, parentFkField = SharedWith.SHARED_WITH.TODO_LIST_ID)
    builder.block()
    val placeholderRecord = builder.getOrBuildRecord()
    childBlocks.add { parentNode ->
      builder.parentNode = parentNode
      builder.buildWithChildren()
    }
    return SharedWithResult(placeholderRecord)
  }

  public fun todoItem(block: TodoItemBuilder.() -> Unit): TodoItemResult {
    val builder = TodoItemBuilder(recordGraph = recordGraph, parentNode = null, parentFkField = TodoItem.TODO_ITEM.TODO_LIST_ID)
    builder.block()
    val placeholderRecord = builder.getOrBuildRecord()
    childBlocks.add { parentNode ->
      builder.parentNode = parentNode
      builder.buildWithChildren()
    }
    return TodoItemResult(placeholderRecord)
  }

  public fun buildWithChildren(): RecordNode {
    val node = build()
    childBlocks.forEach { it(node) }
    return node
  }
}

public class TodoListResult(
  internal val record: TodoListRecord,
) {
  public val id: Long?
    get() = record.get(TodoList.TODO_LIST.ID)

  public val title: String?
    get() = record.get(TodoList.TODO_LIST.TITLE)

  public val description: String?
    get() = record.get(TodoList.TODO_LIST.DESCRIPTION)

  public val createdBy: Long?
    get() = record.get(TodoList.TODO_LIST.CREATED_BY)

  public val updatedBy: Long?
    get() = record.get(TodoList.TODO_LIST.UPDATED_BY)

  public val createdAt: LocalDateTime?
    get() = record.get(TodoList.TODO_LIST.CREATED_AT)
}
