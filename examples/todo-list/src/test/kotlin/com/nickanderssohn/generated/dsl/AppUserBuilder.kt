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
import kotlin.collections.List
import kotlin.collections.MutableList
import org.jooq.TableField

public class AppUserBuilder(
  private val graph: RecordGraph,
) : RecordBuilder<AppUserRecord>(table = AppUser.APP_USER, parentNode = null, recordGraph = graph) {
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
    val builder = SharedWithBuilder(recordGraph = graph, parentNode = null, parentFkFields = listOf(SharedWith.SHARED_WITH.USER_ID as TableField<*, *>), parentRefFields = listOf(AppUser.APP_USER.ID as TableField<*, *>))
    builder.block()
    val placeholderRecord = builder.getOrBuildRecord()
    childBlocks.add { parentNode ->
      builder.parentNode = parentNode
      builder.buildWithChildren()
    }
    return SharedWithResult(placeholderRecord)
  }

  public fun todoItem(vararg fkFields: TableField<TodoItemRecord, *>, block: TodoItemBuilder.() -> Unit): TodoItemResult {
    val fkNames = fkFields.map { it.name }.toSet()
    val parentFkFields: List<TableField<*, *>>
    val parentRefFields: List<TableField<*, *>>
    if (fkNames == setOf("created_by")) {
      parentFkFields = listOf(TodoItem.TODO_ITEM.CREATED_BY as TableField<*, *>)
      parentRefFields = listOf(AppUser.APP_USER.ID as TableField<*, *>)
    } else if (fkNames == setOf("updated_by")) {
      parentFkFields = listOf(TodoItem.TODO_ITEM.UPDATED_BY as TableField<*, *>)
      parentRefFields = listOf(AppUser.APP_USER.ID as TableField<*, *>)
    } else {
      error("No FK matching field names: ${'$'}fkNames")
    }
    val builder = TodoItemBuilder(recordGraph = graph, parentNode = null, parentFkFields = parentFkFields, parentRefFields = parentRefFields)
    builder.block()
    val placeholderRecord = builder.getOrBuildRecord()
    childBlocks.add { parentNode ->
      builder.parentNode = parentNode
      builder.buildWithChildren()
    }
    return TodoItemResult(placeholderRecord)
  }

  public fun todoList(vararg fkFields: TableField<TodoListRecord, *>, block: TodoListBuilder.() -> Unit): TodoListResult {
    val fkNames = fkFields.map { it.name }.toSet()
    val parentFkFields: List<TableField<*, *>>
    val parentRefFields: List<TableField<*, *>>
    if (fkNames == setOf("created_by")) {
      parentFkFields = listOf(TodoList.TODO_LIST.CREATED_BY as TableField<*, *>)
      parentRefFields = listOf(AppUser.APP_USER.ID as TableField<*, *>)
    } else if (fkNames == setOf("updated_by")) {
      parentFkFields = listOf(TodoList.TODO_LIST.UPDATED_BY as TableField<*, *>)
      parentRefFields = listOf(AppUser.APP_USER.ID as TableField<*, *>)
    } else {
      error("No FK matching field names: ${'$'}fkNames")
    }
    val builder = TodoListBuilder(recordGraph = graph, parentNode = null, parentFkFields = parentFkFields, parentRefFields = parentRefFields)
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
