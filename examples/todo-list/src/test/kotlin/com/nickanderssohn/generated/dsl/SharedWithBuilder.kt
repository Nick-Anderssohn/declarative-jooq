package com.nickanderssohn.generated.dsl

import com.nickanderssohn.declarativejooq.PendingPlaceholderRef
import com.nickanderssohn.declarativejooq.RecordBuilder
import com.nickanderssohn.declarativejooq.RecordGraph
import com.nickanderssohn.declarativejooq.RecordNode
import com.nickanderssohn.todolist.jooq.SharedWithRecord
import com.nickanderssohn.todolist.jooq.SharedWithTable
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
) : RecordBuilder<SharedWithRecord>(table = SharedWithTable.SHARED_WITH, parentNode = parentNode, parentFkField = parentFkField, recordGraph = recordGraph, isSelfReferential = isSelfReferential) {
  public var todoListId: Long? = null

  public var userId: Long? = null

  public var todoList: TodoListResult? = null
    set(`value`) {
      field = value
      if (value != null) {
        pendingPlaceholderRefs.add(PendingPlaceholderRef(SharedWithTable.SHARED_WITH.TODO_LIST_ID as TableField<*, *>, value.record))
      }
    }

  public var user: AppUserResult? = null
    set(`value`) {
      field = value
      if (value != null) {
        pendingPlaceholderRefs.add(PendingPlaceholderRef(SharedWithTable.SHARED_WITH.USER_ID as TableField<*, *>, value.record))
      }
    }

  private val childBlocks: MutableList<(RecordNode) -> Unit> = mutableListOf()

  override fun buildRecord(): SharedWithRecord {
    val record = SharedWithRecord()
    record.set(SharedWithTable.SHARED_WITH.TODO_LIST_ID, todoListId)
    record.set(SharedWithTable.SHARED_WITH.USER_ID, userId)
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
    get() = record.get(SharedWithTable.SHARED_WITH.ID)

  public val todoListId: Long?
    get() = record.get(SharedWithTable.SHARED_WITH.TODO_LIST_ID)

  public val userId: Long?
    get() = record.get(SharedWithTable.SHARED_WITH.USER_ID)
}
