package com.nickanderssohn.generated.dsl

import com.nickanderssohn.declarativejooq.PendingPlaceholderRef
import com.nickanderssohn.declarativejooq.RecordBuilder
import com.nickanderssohn.declarativejooq.RecordGraph
import com.nickanderssohn.declarativejooq.RecordNode
import com.nickanderssohn.todolist.jooq.tables.Label
import com.nickanderssohn.todolist.jooq.tables.TodoItemLabel
import com.nickanderssohn.todolist.jooq.tables.TodoList
import com.nickanderssohn.todolist.jooq.tables.records.LabelRecord
import kotlin.Boolean
import kotlin.Long
import kotlin.String
import kotlin.Unit
import kotlin.collections.List
import kotlin.collections.MutableList
import org.jooq.TableField

public class LabelBuilder(
  recordGraph: RecordGraph,
  parentNode: RecordNode?,
  parentFkFields: List<TableField<*, *>> = emptyList(),
  parentRefFields: List<TableField<*, *>> = emptyList(),
  isSelfReferential: Boolean = false,
) : RecordBuilder<LabelRecord>(table = Label.LABEL, parentNode = parentNode, parentFkFields = parentFkFields, parentRefFields = parentRefFields, recordGraph = recordGraph, isSelfReferential = isSelfReferential) {
  public var todoListId: Long? = null

  public var name: String? = null

  public var color: String? = null

  public var todoList: TodoListResult? = null
    set(`value`) {
      field = value
      if (value != null) {
        pendingPlaceholderRefs.add(PendingPlaceholderRef(listOf(Label.LABEL.TODO_LIST_ID as TableField<*, *>), listOf(TodoList.TODO_LIST.ID as TableField<*, *>), value.record))
      }
    }

  private val childBlocks: MutableList<(RecordNode) -> Unit> = mutableListOf()

  override fun buildRecord(): LabelRecord {
    val record = LabelRecord()
    todoListId?.let { record.set(Label.LABEL.TODO_LIST_ID, it) }
    name?.let { record.set(Label.LABEL.NAME, it) }
    color?.let { record.set(Label.LABEL.COLOR, it) }
    return record
  }

  public fun todoItemLabel(block: TodoItemLabelBuilder.() -> Unit): TodoItemLabelResult {
    val builder = TodoItemLabelBuilder(recordGraph = recordGraph, parentNode = null, parentFkFields = listOf(TodoItemLabel.TODO_ITEM_LABEL.TODO_LIST_ID as TableField<*, *>, TodoItemLabel.TODO_ITEM_LABEL.LABEL_NAME as TableField<*, *>), parentRefFields = listOf(Label.LABEL.TODO_LIST_ID as TableField<*, *>, Label.LABEL.NAME as TableField<*, *>))
    builder.block()
    val placeholderRecord = builder.getOrBuildRecord()
    childBlocks.add { parentNode ->
      builder.parentNode = parentNode
      builder.buildWithChildren()
    }
    return TodoItemLabelResult(placeholderRecord)
  }

  public fun buildWithChildren(): RecordNode {
    val node = build()
    childBlocks.forEach { it(node) }
    return node
  }
}

public class LabelResult(
  internal val record: LabelRecord,
) {
  public val todoListId: Long?
    get() = record.get(Label.LABEL.TODO_LIST_ID)

  public val name: String?
    get() = record.get(Label.LABEL.NAME)

  public val color: String?
    get() = record.get(Label.LABEL.COLOR)
}
