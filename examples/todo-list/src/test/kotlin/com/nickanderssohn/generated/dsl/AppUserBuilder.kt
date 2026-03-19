package com.nickanderssohn.generated.dsl

import com.nickanderssohn.declarativejooq.DslScope
import com.nickanderssohn.declarativejooq.RecordBuilder
import com.nickanderssohn.declarativejooq.RecordGraph
import com.nickanderssohn.declarativejooq.RecordNode
import com.nickanderssohn.todolist.jooq.SharedWithTable
import com.nickanderssohn.todolist.jooq.TodoItemRecord
import com.nickanderssohn.todolist.jooq.TodoItemTable
import com.nickanderssohn.todolist.jooq.TodoListRecord
import com.nickanderssohn.todolist.jooq.TodoListTable
import com.nickanderssohn.todolist.jooq.UserRecord
import com.nickanderssohn.todolist.jooq.UserTable
import kotlin.Long
import kotlin.String
import kotlin.Unit
import kotlin.collections.MutableList
import org.jooq.TableField

public class AppUserBuilder(
  private val graph: RecordGraph,
) : RecordBuilder<UserRecord>(table = UserTable.APP_USER, parentNode = null, parentFkField = null, recordGraph = graph) {
  public var name: String? = null

  public var email: String? = null

  private val childBlocks: MutableList<(RecordNode) -> Unit> = mutableListOf()

  override fun buildRecord(): UserRecord {
    val record = UserRecord()
    record.set(UserTable.APP_USER.NAME, name)
    record.set(UserTable.APP_USER.EMAIL, email)
    return record
  }

  public fun user(block: SharedWithBuilder.() -> Unit): SharedWithResult {
    val builder = SharedWithBuilder(recordGraph = graph, parentNode = null, parentFkField = SharedWithTable.SHARED_WITH.USER_ID)
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
  internal val record: UserRecord,
) {
  public val id: Long?
    get() = record.get(UserTable.APP_USER.ID)

  public val name: String?
    get() = record.get(UserTable.APP_USER.NAME)

  public val email: String?
    get() = record.get(UserTable.APP_USER.EMAIL)
}

public fun DslScope.appUser(block: AppUserBuilder.() -> Unit): AppUserResult {
  val builder = AppUserBuilder(recordGraph)
  builder.block()
  val node = builder.buildWithChildren()
  recordGraph.addRootNode(node)
  val result = AppUserResult(node.record as UserRecord)
  return result
}
