package com.nickanderssohn.generated.dsl

import com.nickanderssohn.declarativejooq.DslScope
import com.nickanderssohn.declarativejooq.RecordBuilder
import com.nickanderssohn.declarativejooq.RecordGraph
import com.nickanderssohn.declarativejooq.RecordNode
import com.nickanderssohn.todolist.jooq.tables.AppUser
import com.nickanderssohn.todolist.jooq.tables.SharedWith
import com.nickanderssohn.todolist.jooq.tables.TodoItem
import com.nickanderssohn.todolist.jooq.tables.TodoList
import com.nickanderssohn.todolist.jooq.tables.records.AppUserRecord
import com.nickanderssohn.todolist.jooq.tables.records.TodoItemRecord
import com.nickanderssohn.todolist.jooq.tables.records.TodoListRecord
import kotlin.Long
import kotlin.String
import kotlin.Unit
import kotlin.collections.MutableList
import org.jooq.TableField

public class AppUserBuilder(
  private val graph: RecordGraph,
) : RecordBuilder<AppUserRecord>(table = AppUser.APP_USER, parentNode = null, parentFkField = null, recordGraph = graph) {
  public var name: String? = null

  public var email: String? = null

  private val childBlocks: MutableList<(RecordNode) -> Unit> = mutableListOf()

  override fun buildRecord(): AppUserRecord {
    val record = AppUserRecord()
    name?.let { record.set(AppUser.APP_USER.NAME, it) }
    email?.let { record.set(AppUser.APP_USER.EMAIL, it) }
    return record
  }

  public fun user(block: SharedWithBuilder.() -> Unit): SharedWithResult {
    val builder = SharedWithBuilder(recordGraph = graph, parentNode = null, parentFkField = SharedWith.SHARED_WITH.USER_ID)
    builder.block()
    val placeholderRecord = builder.getOrBuildRecord()
    childBlocks.add { parentNode ->
      builder.parentNode = parentNode
      builder.buildWithChildren()
    }
    return SharedWithResult(placeholderRecord)
  }

  public fun todoItem(fkField: TableField<TodoItemRecord, *>, block: TodoItemBuilder.() -> Unit): TodoItemResult {
    val builder = TodoItemBuilder(recordGraph = graph, parentNode = null, parentFkField = fkField)
    builder.block()
    val placeholderRecord = builder.getOrBuildRecord()
    childBlocks.add { parentNode ->
      builder.parentNode = parentNode
      builder.buildWithChildren()
    }
    return TodoItemResult(placeholderRecord)
  }

  public fun todoList(fkField: TableField<TodoListRecord, *>, block: TodoListBuilder.() -> Unit): TodoListResult {
    val builder = TodoListBuilder(recordGraph = graph, parentNode = null, parentFkField = fkField)
    builder.block()
    val placeholderRecord = builder.getOrBuildRecord()
    childBlocks.add { parentNode ->
      builder.parentNode = parentNode
      builder.buildWithChildren()
    }
    return TodoListResult(placeholderRecord)
  }

  public fun buildWithChildren(): RecordNode {
    val node = build()
    childBlocks.forEach { it(node) }
    return node
  }
}

public class AppUserResult(
  internal val record: AppUserRecord,
) {
  public val id: Long?
    get() = record.get(AppUser.APP_USER.ID)

  public val name: String?
    get() = record.get(AppUser.APP_USER.NAME)

  public val email: String?
    get() = record.get(AppUser.APP_USER.EMAIL)
}

public fun DslScope.appUser(block: AppUserBuilder.() -> Unit): AppUserResult {
  val builder = AppUserBuilder(recordGraph)
  builder.block()
  val node = builder.buildWithChildren()
  recordGraph.addRootNode(node)
  val result = AppUserResult(node.record as AppUserRecord)
  return result
}
