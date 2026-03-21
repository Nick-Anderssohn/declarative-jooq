package com.nickanderssohn.generated.dsl

import com.nickanderssohn.declarativejooq.DeclarativeJooqDsl
import com.nickanderssohn.declarativejooq.PendingPlaceholderRef
import com.nickanderssohn.declarativejooq.RecordBuilder
import com.nickanderssohn.declarativejooq.RecordGraph
import com.nickanderssohn.declarativejooq.RecordNode
import com.nickanderssohn.todolist.jooq.tables.AppUser
import com.nickanderssohn.todolist.jooq.tables.Label
import com.nickanderssohn.todolist.jooq.tables.SharedWith
import com.nickanderssohn.todolist.jooq.tables.TodoItem
import com.nickanderssohn.todolist.jooq.tables.TodoList
import com.nickanderssohn.todolist.jooq.tables.records.TodoListRecord
import java.time.LocalDateTime
import kotlin.Boolean
import kotlin.Long
import kotlin.String
import kotlin.Unit
import kotlin.collections.List
import kotlin.collections.MutableList
import org.jooq.TableField

@DeclarativeJooqDsl
public class TodoListBuilder(
  recordGraph: RecordGraph,
  parentNode: RecordNode?,
  parentFkFields: List<TableField<*, *>> = emptyList(),
  parentRefFields: List<TableField<*, *>> = emptyList(),
  isSelfReferential: Boolean = false,
) {
  public var title: String? = null

  public var description: String? = null

  public var createdAt: LocalDateTime? = null

  public var createdBy: AppUserResult? = null
    set(`value`) {
      field = value
      if (value != null) {
        recordBuilder.pendingPlaceholderRefs.add(PendingPlaceholderRef(listOf(TodoList.TODO_LIST.CREATED_BY as TableField<*, *>), listOf(AppUser.APP_USER.ID as TableField<*, *>), value.record))
      }
    }

  public var updatedBy: AppUserResult? = null
    set(`value`) {
      field = value
      if (value != null) {
        recordBuilder.pendingPlaceholderRefs.add(PendingPlaceholderRef(listOf(TodoList.TODO_LIST.UPDATED_BY as TableField<*, *>), listOf(AppUser.APP_USER.ID as TableField<*, *>), value.record))
      }
    }

  internal val recordBuilder: RecordBuilder<TodoListRecord> = RecordBuilder(
    table = TodoList.TODO_LIST,
    parentNode = parentNode,
    parentFkFields = parentFkFields,
    parentRefFields = parentRefFields,
    recordGraph = recordGraph,
    isSelfReferential = isSelfReferential,
    buildRecord = {
      val record = TodoListRecord()
      title?.let { record.set(TodoList.TODO_LIST.TITLE, it) }
      description?.let { record.set(TodoList.TODO_LIST.DESCRIPTION, it) }
      createdAt?.let { record.set(TodoList.TODO_LIST.CREATED_AT, it) }
      record
    }
  )

  private val childBlocks: MutableList<(RecordNode) -> Unit> = mutableListOf()

  public fun label(block: LabelBuilder.() -> Unit): LabelResult {
    val builder = LabelBuilder(recordGraph = recordBuilder.recordGraph, parentNode = null, parentFkFields = listOf(Label.LABEL.TODO_LIST_ID as TableField<*, *>), parentRefFields = listOf(TodoList.TODO_LIST.ID as TableField<*, *>))
    builder.block()
    val placeholderRecord = builder.recordBuilder.getOrBuildRecord()
    childBlocks.add { parentNode ->
      builder.recordBuilder.parentNode = parentNode
      builder.buildWithChildren()
    }
    return LabelResult(placeholderRecord)
  }

  public fun sharedWith(block: SharedWithBuilder.() -> Unit): SharedWithResult {
    val builder = SharedWithBuilder(recordGraph = recordBuilder.recordGraph, parentNode = null, parentFkFields = listOf(SharedWith.SHARED_WITH.TODO_LIST_ID as TableField<*, *>), parentRefFields = listOf(TodoList.TODO_LIST.ID as TableField<*, *>))
    builder.block()
    val placeholderRecord = builder.recordBuilder.getOrBuildRecord()
    childBlocks.add { parentNode ->
      builder.recordBuilder.parentNode = parentNode
      builder.buildWithChildren()
    }
    return SharedWithResult(placeholderRecord)
  }

  public fun todoItem(block: TodoItemBuilder.() -> Unit): TodoItemResult {
    val builder = TodoItemBuilder(recordGraph = recordBuilder.recordGraph, parentNode = null, parentFkFields = listOf(TodoItem.TODO_ITEM.TODO_LIST_ID as TableField<*, *>), parentRefFields = listOf(TodoList.TODO_LIST.ID as TableField<*, *>))
    builder.block()
    val placeholderRecord = builder.recordBuilder.getOrBuildRecord()
    childBlocks.add { parentNode ->
      builder.recordBuilder.parentNode = parentNode
      builder.buildWithChildren()
    }
    return TodoItemResult(placeholderRecord)
  }

  public fun buildWithChildren(): RecordNode {
    val node = recordBuilder.build()
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
