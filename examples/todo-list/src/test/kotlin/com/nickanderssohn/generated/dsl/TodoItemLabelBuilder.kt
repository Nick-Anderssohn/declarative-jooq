package com.nickanderssohn.generated.dsl

import com.nickanderssohn.declarativejooq.DeclarativeJooqDsl
import com.nickanderssohn.declarativejooq.PendingPlaceholderRef
import com.nickanderssohn.declarativejooq.RecordBuilder
import com.nickanderssohn.declarativejooq.RecordGraph
import com.nickanderssohn.declarativejooq.RecordNode
import com.nickanderssohn.todolist.jooq.tables.Label
import com.nickanderssohn.todolist.jooq.tables.TodoItem
import com.nickanderssohn.todolist.jooq.tables.TodoItemLabel
import com.nickanderssohn.todolist.jooq.tables.records.TodoItemLabelRecord
import kotlin.Boolean
import kotlin.Long
import kotlin.String
import kotlin.Unit
import kotlin.collections.List
import kotlin.collections.MutableList
import org.jooq.TableField

@DeclarativeJooqDsl
public class TodoItemLabelBuilder(
  recordGraph: RecordGraph,
  parentNode: RecordNode?,
  parentFkFields: List<TableField<*, *>> = emptyList(),
  parentRefFields: List<TableField<*, *>> = emptyList(),
  isSelfReferential: Boolean = false,
) {
  public var todoItemId: Long? = null

  public var todoListId: Long? = null

  public var labelName: String? = null

  public var todoItem: TodoItemResult? = null
    set(`value`) {
      field = value
      if (value != null) {
        recordBuilder.pendingPlaceholderRefs.add(PendingPlaceholderRef(listOf(TodoItemLabel.TODO_ITEM_LABEL.TODO_ITEM_ID as TableField<*, *>), listOf(TodoItem.TODO_ITEM.ID as TableField<*, *>), value.record))
      }
    }

  public var label: LabelResult? = null
    set(`value`) {
      field = value
      if (value != null) {
        recordBuilder.pendingPlaceholderRefs.add(PendingPlaceholderRef(listOf(TodoItemLabel.TODO_ITEM_LABEL.TODO_LIST_ID as TableField<*, *>, TodoItemLabel.TODO_ITEM_LABEL.LABEL_NAME as TableField<*, *>), listOf(Label.LABEL.TODO_LIST_ID as TableField<*, *>, Label.LABEL.NAME as TableField<*, *>), value.record))
      }
    }

  internal val recordBuilder: RecordBuilder<TodoItemLabelRecord> = RecordBuilder(
    table = TodoItemLabel.TODO_ITEM_LABEL,
    parentNode = parentNode,
    parentFkFields = parentFkFields,
    parentRefFields = parentRefFields,
    recordGraph = recordGraph,
    isSelfReferential = isSelfReferential,
    buildRecord = {
      val record = TodoItemLabelRecord()
      todoItemId?.let { record.set(TodoItemLabel.TODO_ITEM_LABEL.TODO_ITEM_ID, it) }
      todoListId?.let { record.set(TodoItemLabel.TODO_ITEM_LABEL.TODO_LIST_ID, it) }
      labelName?.let { record.set(TodoItemLabel.TODO_ITEM_LABEL.LABEL_NAME, it) }
      record
    }
  )

  private val childBlocks: MutableList<(RecordNode) -> Unit> = mutableListOf()

  public fun buildWithChildren(): RecordNode {
    val node = recordBuilder.build()
    childBlocks.forEach { it(node) }
    return node
  }
}

public class TodoItemLabelResult(
  internal val record: TodoItemLabelRecord,
) {
  public val id: Long?
    get() = record.get(TodoItemLabel.TODO_ITEM_LABEL.ID)

  public val todoItemId: Long?
    get() = record.get(TodoItemLabel.TODO_ITEM_LABEL.TODO_ITEM_ID)

  public val todoListId: Long?
    get() = record.get(TodoItemLabel.TODO_ITEM_LABEL.TODO_LIST_ID)

  public val labelName: String?
    get() = record.get(TodoItemLabel.TODO_ITEM_LABEL.LABEL_NAME)
}
