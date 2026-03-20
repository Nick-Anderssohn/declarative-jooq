package com.nickanderssohn.generated.dsl

import com.nickanderssohn.declarativejooq.PendingPlaceholderRef
import com.nickanderssohn.declarativejooq.RecordBuilder
import com.nickanderssohn.declarativejooq.RecordGraph
import com.nickanderssohn.declarativejooq.RecordNode
import com.nickanderssohn.todolist.jooq.SharedWithTable
import com.nickanderssohn.todolist.jooq.TodoItemTable
import com.nickanderssohn.todolist.jooq.TodoListRecord
import com.nickanderssohn.todolist.jooq.TodoListTable
import kotlin.Boolean
import kotlin.Long
import kotlin.String
import kotlin.Unit
import kotlin.collections.List
import kotlin.collections.MutableList
import org.jooq.TableField

public class TodoListBuilder(
  recordGraph: RecordGraph,
  parentNode: RecordNode?,
  parentFkFields: List<TableField<*, *>> = emptyList(),
  isSelfReferential: Boolean = false,
) : RecordBuilder<TodoListRecord>(table = TodoListTable.TODO_LIST, parentNode = parentNode, parentFkFields = parentFkFields, recordGraph = recordGraph, isSelfReferential = isSelfReferential) {
  public var title: String? = null

  public var description: String? = null

  public var createdBy: AppUserResult? = null
    set(`value`) {
      field = value
      if (value != null) {
        pendingPlaceholderRefs.add(PendingPlaceholderRef(listOf(TodoListTable.TODO_LIST.CREATED_BY as TableField<*, *>), value.record))
      }
    }

  public var updatedBy: AppUserResult? = null
    set(`value`) {
      field = value
      if (value != null) {
        pendingPlaceholderRefs.add(PendingPlaceholderRef(listOf(TodoListTable.TODO_LIST.UPDATED_BY as TableField<*, *>), value.record))
      }
    }

  private val childBlocks: MutableList<(RecordNode) -> Unit> = mutableListOf()

  override fun buildRecord(): TodoListRecord {
    val record = TodoListRecord()
    record.set(TodoListTable.TODO_LIST.TITLE, title)
    record.set(TodoListTable.TODO_LIST.DESCRIPTION, description)
    return record
  }

  public fun sharedWith(block: SharedWithBuilder.() -> Unit): SharedWithResult {
    val builder = SharedWithBuilder(recordGraph = recordGraph, parentNode = null, parentFkFields = listOf(SharedWithTable.SHARED_WITH.TODO_LIST_ID as TableField<*, *>))
    builder.block()
    val placeholderRecord = builder.getOrBuildRecord()
    childBlocks.add { parentNode ->
      builder.parentNode = parentNode
      builder.buildWithChildren()
    }
    return SharedWithResult(placeholderRecord)
  }

  public fun todoItem(block: TodoItemBuilder.() -> Unit): TodoItemResult {
    val builder = TodoItemBuilder(recordGraph = recordGraph, parentNode = null, parentFkFields = listOf(TodoItemTable.TODO_ITEM.TODO_LIST_ID as TableField<*, *>))
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
    get() = record.get(TodoListTable.TODO_LIST.ID)

  public val title: String?
    get() = record.get(TodoListTable.TODO_LIST.TITLE)

  public val description: String?
    get() = record.get(TodoListTable.TODO_LIST.DESCRIPTION)

  public val createdBy: Long?
    get() = record.get(TodoListTable.TODO_LIST.CREATED_BY)

  public val updatedBy: Long?
    get() = record.get(TodoListTable.TODO_LIST.UPDATED_BY)
}
