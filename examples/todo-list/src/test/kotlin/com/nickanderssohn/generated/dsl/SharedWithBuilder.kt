package com.nickanderssohn.generated.dsl

import com.nickanderssohn.declarativejooq.PendingPlaceholderRef
import com.nickanderssohn.declarativejooq.RecordBuilder
import com.nickanderssohn.declarativejooq.RecordGraph
import com.nickanderssohn.declarativejooq.RecordNode
import com.nickanderssohn.todolist.jooq.tables.SharedWith
import com.nickanderssohn.todolist.jooq.tables.records.SharedWithRecord
import kotlin.Boolean
import kotlin.Long
import kotlin.Unit
import kotlin.collections.MutableList
import org.jooq.TableField

public class SharedWithBuilder(
  recordGraph: RecordGraph,
  parentNode: RecordNode?,
  parentFkField: TableField<*, *>?,
  isSelfReferential: Boolean = false,
) : RecordBuilder<SharedWithRecord>(table = SharedWith.SHARED_WITH, parentNode = parentNode, parentFkField = parentFkField, recordGraph = recordGraph, isSelfReferential = isSelfReferential) {
  public var todoListId: Long? = null

  public var userId: Long? = null

  public var todoList: TodoListResult? = null
    set(`value`) {
      field = value
      if (value != null) {
        pendingPlaceholderRefs.add(PendingPlaceholderRef(SharedWith.SHARED_WITH.TODO_LIST_ID as TableField<*, *>, value.record))
      }
    }

  public var user: AppUserResult? = null
    set(`value`) {
      field = value
      if (value != null) {
        pendingPlaceholderRefs.add(PendingPlaceholderRef(SharedWith.SHARED_WITH.USER_ID as TableField<*, *>, value.record))
      }
    }

  private val childBlocks: MutableList<(RecordNode) -> Unit> = mutableListOf()

  override fun buildRecord(): SharedWithRecord {
    val record = SharedWithRecord()
    todoListId?.let { record.set(SharedWith.SHARED_WITH.TODO_LIST_ID, it) }
    userId?.let { record.set(SharedWith.SHARED_WITH.USER_ID, it) }
    return record
  }

  public fun buildWithChildren(): RecordNode {
    val node = build()
    childBlocks.forEach { it(node) }
    return node
  }
}

public class SharedWithResult(
  internal val record: SharedWithRecord,
) {
  public val id: Long?
    get() = record.get(SharedWith.SHARED_WITH.ID)

  public val todoListId: Long?
    get() = record.get(SharedWith.SHARED_WITH.TODO_LIST_ID)

  public val userId: Long?
    get() = record.get(SharedWith.SHARED_WITH.USER_ID)
}
