package com.nickanderssohn.generated.dsl

import com.nickanderssohn.declarativejooq.DeclarativeJooqDsl
import com.nickanderssohn.declarativejooq.PendingPlaceholderRef
import com.nickanderssohn.declarativejooq.RecordBuilder
import com.nickanderssohn.declarativejooq.RecordGraph
import com.nickanderssohn.declarativejooq.RecordNode
import com.nickanderssohn.todolist.jooq.tables.AppUser
import com.nickanderssohn.todolist.jooq.tables.SharedWith
import com.nickanderssohn.todolist.jooq.tables.TodoList
import com.nickanderssohn.todolist.jooq.tables.records.SharedWithRecord
import kotlin.Boolean
import kotlin.Long
import kotlin.Unit
import kotlin.collections.List
import kotlin.collections.MutableList
import org.jooq.TableField

@DeclarativeJooqDsl
public class SharedWithBuilder(
  recordGraph: RecordGraph,
  parentNode: RecordNode?,
  parentFkFields: List<TableField<*, *>> = emptyList(),
  parentRefFields: List<TableField<*, *>> = emptyList(),
  isSelfReferential: Boolean = false,
) {
  public var todoListId: Long? = null

  public var userId: Long? = null

  public var todoList: TodoListResult? = null
    set(`value`) {
      field = value
      if (value != null) {
        recordBuilder.pendingPlaceholderRefs.add(PendingPlaceholderRef(listOf(SharedWith.SHARED_WITH.TODO_LIST_ID as TableField<*, *>), listOf(TodoList.TODO_LIST.ID as TableField<*, *>), value.record))
      }
    }

  public var user: AppUserResult? = null
    set(`value`) {
      field = value
      if (value != null) {
        recordBuilder.pendingPlaceholderRefs.add(PendingPlaceholderRef(listOf(SharedWith.SHARED_WITH.USER_ID as TableField<*, *>), listOf(AppUser.APP_USER.ID as TableField<*, *>), value.record))
      }
    }

  internal val recordBuilder: RecordBuilder<SharedWithRecord> = RecordBuilder(
    table = SharedWith.SHARED_WITH,
    parentNode = parentNode,
    parentFkFields = parentFkFields,
    parentRefFields = parentRefFields,
    recordGraph = recordGraph,
    isSelfReferential = isSelfReferential,
    buildRecord = {
      val record = SharedWithRecord()
      todoListId?.let { record.set(SharedWith.SHARED_WITH.TODO_LIST_ID, it) }
      userId?.let { record.set(SharedWith.SHARED_WITH.USER_ID, it) }
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
