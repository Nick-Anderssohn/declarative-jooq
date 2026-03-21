package com.nickanderssohn.todolist

import com.nickanderssohn.declarativejooq.DecDsl
import com.nickanderssohn.generated.dsl.appUser
import com.nickanderssohn.todolist.controller.CreateTodoItemRequest
import com.nickanderssohn.todolist.controller.CreateTodoListRequest
import com.nickanderssohn.todolist.controller.CreateUserRequest
import com.nickanderssohn.todolist.controller.ShareTodoListRequest
import com.nickanderssohn.todolist.jooq.tables.AppUser.Companion.APP_USER
import com.nickanderssohn.todolist.jooq.tables.SharedWith.Companion.SHARED_WITH
import com.nickanderssohn.todolist.jooq.tables.Label
import com.nickanderssohn.todolist.jooq.tables.TodoItem
import com.nickanderssohn.todolist.jooq.tables.TodoItem.Companion.TODO_ITEM
import com.nickanderssohn.todolist.jooq.tables.TodoItemLabel
import com.nickanderssohn.todolist.jooq.tables.TodoList
import com.nickanderssohn.todolist.jooq.tables.TodoList.Companion.TODO_LIST
import org.jooq.DSLContext
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpStatus
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
class TodoListIntegrationTest {

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer<Nothing>("postgres:16-alpine")

        @DynamicPropertySource
        @JvmStatic
        fun configureDataSource(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
        }
    }

    @Autowired
    lateinit var dslContext: DSLContext

    @Autowired
    lateinit var restTemplate: TestRestTemplate

    @BeforeEach
    fun truncateTables() {
        dslContext.execute("TRUNCATE todo_item_label, label, shared_with, todo_item, todo_list, app_user RESTART IDENTITY CASCADE")
    }

    // -----------------------------------------------------------------------
    // Test: declarative-jooq DSL seeds todo data correctly
    // -----------------------------------------------------------------------

    @Test
    fun `declarative-jooq seeds test data correctly`() {
        val result = DecDsl.execute(dslContext) {
            appUser {
                name = "Seed User"
                email = "seed@example.com"
                todoList(TodoList.TODO_LIST.CREATED_BY) {
                    title = "Groceries"
                    description = "Weekly shopping"
                    todoItem {
                        title = "Buy milk"
                    }
                    todoItem {
                        title = "Buy eggs"
                    }
                }
            }
        }

        // Verify 1 list exists
        val listCount = dslContext.selectCount().from(TODO_LIST).fetchOne(0, Int::class.java)!!
        assertEquals(1, listCount, "Expected 1 todo list row")

        // Verify 2 items exist
        val itemCount = dslContext.selectCount().from(TODO_ITEM).fetchOne(0, Int::class.java)!!
        assertEquals(2, itemCount, "Expected 2 todo item rows")

        // Verify FK relationship: both items reference the list
        val listId = dslContext.select(TODO_LIST.ID).from(TODO_LIST).fetchOne()?.get(TODO_LIST.ID)
        assertNotNull(listId, "Expected to find the inserted todo_list")

        val items = dslContext.selectFrom(TODO_ITEM).fetch()
        assertEquals(2, items.size, "Expected 2 todo_item rows")
        for (item in items) {
            assertEquals(listId, item.get(TODO_ITEM.TODO_LIST_ID),
                "todo_item.todo_list_id should reference the parent todo_list")
        }

        // Verify DslResult records
        val todoListRecords = result.records<com.nickanderssohn.todolist.jooq.tables.records.TodoListRecord>("todo_list")
        assertEquals(1, todoListRecords.size, "DslResult should show 1 root todo_list")
    }

    // -----------------------------------------------------------------------
    // Test: REST API creates and retrieves todo lists
    // -----------------------------------------------------------------------

    @Test
    fun `REST API creates and retrieves todo lists`() {
        val request = CreateTodoListRequest(title = "Shopping", description = "Weekly")
        val created = restTemplate.postForEntity("/api/todo-lists", request, Map::class.java)

        assertEquals(HttpStatus.OK, created.statusCode)
        val body = created.body!!
        assertNotNull(body["id"])
        assertEquals("Shopping", body["title"])

        val listId = (body["id"] as Number).toLong()

        // Create items via REST
        val item1 = restTemplate.postForEntity(
            "/api/todo-lists/$listId/items",
            CreateTodoItemRequest("Buy milk"),
            Map::class.java
        )
        assertEquals(HttpStatus.OK, item1.statusCode)

        val item2 = restTemplate.postForEntity(
            "/api/todo-lists/$listId/items",
            CreateTodoItemRequest("Buy eggs"),
            Map::class.java
        )
        assertEquals(HttpStatus.OK, item2.statusCode)

        // GET items
        val itemsResponse = restTemplate.getForEntity(
            "/api/todo-lists/$listId/items",
            List::class.java
        )
        assertEquals(HttpStatus.OK, itemsResponse.statusCode)
        assertEquals(2, itemsResponse.body?.size, "Expected 2 todo items")
    }

    // -----------------------------------------------------------------------
    // Test: Seeded data is accessible via REST API
    // -----------------------------------------------------------------------

    @Test
    fun `seeded data is accessible via REST API`() {
        // Seed data using declarative-jooq DSL
        DecDsl.execute(dslContext) {
            appUser {
                name = "Seed User"
                email = "seed@example.com"
                todoList(TodoList.TODO_LIST.CREATED_BY) {
                    title = "Work Tasks"
                    todoItem {
                        title = "Write tests"
                    }
                    todoItem {
                        title = "Review PR"
                    }
                }
            }
        }

        // Fetch via REST
        val response = restTemplate.getForEntity("/api/todo-lists", List::class.java)
        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(1, response.body?.size, "Expected 1 seeded todo list via REST")

        val lists = response.body!!
        val firstList = lists[0] as Map<*, *>
        assertEquals("Work Tasks", firstList["title"])

        val listId = (firstList["id"] as Number).toLong()
        val itemsResponse = restTemplate.getForEntity(
            "/api/todo-lists/$listId/items",
            List::class.java
        )
        assertEquals(HttpStatus.OK, itemsResponse.statusCode)
        assertEquals(2, itemsResponse.body?.size, "Expected 2 items seeded by declarative-jooq")
    }

    // -----------------------------------------------------------------------
    // Test: 404 for non-existent todo list
    // -----------------------------------------------------------------------

    @Test
    fun `returns 404 for non-existent todo list`() {
        val response = restTemplate.getForEntity("/api/todo-lists/99999", Map::class.java)
        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }

    // -----------------------------------------------------------------------
    // TEST-01: DSL seeds users with created_by and updated_by FK wiring
    // -----------------------------------------------------------------------

    @Test
    fun `DSL seeds users with created_by and updated_by FK wiring`() {
        DecDsl.execute(dslContext) {
            val bob = appUser { name = "Bob"; email = "bob@example.com" }
            appUser {
                name = "Alice"
                email = "alice@example.com"
                val myList = todoList(TodoList.TODO_LIST.CREATED_BY) {
                    title = "Alice's List"
                    description = "Test list"
                    updatedBy = bob
                }
                todoItem(TodoItem.TODO_ITEM.CREATED_BY) {
                    title = "Alice's Item"
                    updatedBy = bob
                    todoList = myList
                }
            }
        }

        // Verify 2 users, 1 list, 1 item inserted
        val userCount = dslContext.selectCount().from(APP_USER).fetchOne(0, Int::class.java)!!
        assertEquals(2, userCount, "Expected 2 app_user rows")

        val listCount = dslContext.selectCount().from(TODO_LIST).fetchOne(0, Int::class.java)!!
        assertEquals(1, listCount, "Expected 1 todo_list row")

        val itemCount = dslContext.selectCount().from(TODO_ITEM).fetchOne(0, Int::class.java)!!
        assertEquals(1, itemCount, "Expected 1 todo_item row")

        // Deep FK verification: list.created_by == alice.id, list.updated_by == bob.id
        val aliceId = dslContext.select(APP_USER.ID).from(APP_USER)
            .where(APP_USER.NAME.eq("Alice")).fetchOne(APP_USER.ID)
        val bobId = dslContext.select(APP_USER.ID).from(APP_USER)
            .where(APP_USER.NAME.eq("Bob")).fetchOne(APP_USER.ID)

        val listRow = dslContext.select(TODO_LIST.CREATED_BY, TODO_LIST.UPDATED_BY)
            .from(TODO_LIST).fetchOne()!!
        assertEquals(aliceId, listRow.get(TODO_LIST.CREATED_BY),
            "todo_list.created_by should be Alice's id")
        assertEquals(bobId, listRow.get(TODO_LIST.UPDATED_BY),
            "todo_list.updated_by should be Bob's id")

        // Deep FK verification: item.created_by == alice.id, item.updated_by == bob.id
        val itemRow = dslContext.select(TODO_ITEM.CREATED_BY, TODO_ITEM.UPDATED_BY)
            .from(TODO_ITEM).fetchOne()!!
        assertEquals(aliceId, itemRow.get(TODO_ITEM.CREATED_BY),
            "todo_item.created_by should be Alice's id")
        assertEquals(bobId, itemRow.get(TODO_ITEM.UPDATED_BY),
            "todo_item.updated_by should be Bob's id")
    }

    // -----------------------------------------------------------------------
    // TEST-02: DSL placeholder wires one user as creator of multiple records
    // -----------------------------------------------------------------------

    @Test
    fun `DSL placeholder wires one user as creator of multiple records`() {
        DecDsl.execute(dslContext) {
            appUser {
                name = "Alice"
                email = "alice@example.com"
                todoList(TodoList.TODO_LIST.CREATED_BY) {
                    title = "List One"
                }
                todoList(TodoList.TODO_LIST.CREATED_BY) {
                    title = "List Two"
                }
            }
        }

        // Alice inserted exactly once
        val userCount = dslContext.selectCount().from(APP_USER).fetchOne(0, Int::class.java)!!
        assertEquals(1, userCount, "Expected exactly 1 app_user row (Alice inserted once)")

        // Both lists have created_by = alice.id
        val aliceId = dslContext.select(APP_USER.ID).from(APP_USER).fetchOne(APP_USER.ID)
        val createdBys = dslContext.select(TODO_LIST.CREATED_BY).from(TODO_LIST)
            .fetch(TODO_LIST.CREATED_BY)
        assertEquals(2, createdBys.size, "Expected 2 todo_list rows")
        assertTrue(createdBys.all { it == aliceId },
            "Both todo_lists.created_by should be Alice's id")
    }

    // -----------------------------------------------------------------------
    // TEST-03: DSL seeds shared_with junction records
    // -----------------------------------------------------------------------

    @Test
    fun `DSL seeds shared_with junction records`() {
        DecDsl.execute(dslContext) {
            val alice = appUser { name = "Alice"; email = "alice@example.com" }
            val bob = appUser { name = "Bob"; email = "bob@example.com" }
            appUser {
                name = "Owner"
                email = "owner@example.com"
                todoList(TodoList.TODO_LIST.CREATED_BY) {
                    title = "Shared List"
                    sharedWith {
                        user = alice
                    }
                    sharedWith {
                        user = bob
                    }
                }
            }
        }

        // Verify 2 shared_with rows
        val shareCount = dslContext.selectCount().from(SHARED_WITH).fetchOne(0, Int::class.java)!!
        assertEquals(2, shareCount, "Expected 2 shared_with rows")

        // Verify FK values
        val aliceId = dslContext.select(APP_USER.ID).from(APP_USER)
            .where(APP_USER.NAME.eq("Alice")).fetchOne(APP_USER.ID)
        val bobId = dslContext.select(APP_USER.ID).from(APP_USER)
            .where(APP_USER.NAME.eq("Bob")).fetchOne(APP_USER.ID)
        val listId = dslContext.select(TODO_LIST.ID).from(TODO_LIST).fetchOne(TODO_LIST.ID)

        val shares = dslContext.select(SHARED_WITH.TODO_LIST_ID, SHARED_WITH.USER_ID)
            .from(SHARED_WITH).fetch()
        assertEquals(2, shares.size)
        assertTrue(shares.all { it.get(SHARED_WITH.TODO_LIST_ID) == listId },
            "All shared_with rows should reference the todo_list")
        val sharedUserIds = shares.map { it.get(SHARED_WITH.USER_ID) }.toSet()
        assertEquals(setOf(aliceId, bobId), sharedUserIds,
            "shared_with should reference both Alice and Bob")
    }

    // -----------------------------------------------------------------------
    // TEST-04: REST API creates and lists users
    // -----------------------------------------------------------------------

    @Test
    fun `REST API creates and lists users`() {
        // Create two users
        val alice = restTemplate.postForEntity(
            "/api/users",
            CreateUserRequest(name = "Alice", email = "alice@example.com"),
            Map::class.java
        )
        assertEquals(HttpStatus.OK, alice.statusCode)
        assertNotNull(alice.body!!["id"], "Created user should have an id")
        assertEquals("Alice", alice.body!!["name"])
        assertEquals("alice@example.com", alice.body!!["email"])

        val bob = restTemplate.postForEntity(
            "/api/users",
            CreateUserRequest(name = "Bob", email = "bob@example.com"),
            Map::class.java
        )
        assertEquals(HttpStatus.OK, bob.statusCode)

        // List all users
        val listResponse = restTemplate.getForEntity("/api/users", List::class.java)
        assertEquals(HttpStatus.OK, listResponse.statusCode)
        assertEquals(2, listResponse.body?.size, "Expected 2 users")

        val names = (listResponse.body!! as List<Map<String, Any>>).map { it["name"] }.toSet()
        assertEquals(setOf("Alice", "Bob"), names, "User names should match")
    }

    // -----------------------------------------------------------------------
    // TEST-05: REST API shares a todo list and retrieves shares
    // -----------------------------------------------------------------------

    @Test
    fun `REST API shares a todo list and retrieves shares`() {
        // Create a user via REST
        val userResponse = restTemplate.postForEntity(
            "/api/users",
            CreateUserRequest(name = "Alice", email = "alice@example.com"),
            Map::class.java
        )
        val userId = (userResponse.body!!["id"] as Number).toLong()

        // Create a second user to share with
        val user2Response = restTemplate.postForEntity(
            "/api/users",
            CreateUserRequest(name = "Bob", email = "bob@example.com"),
            Map::class.java
        )
        val user2Id = (user2Response.body!!["id"] as Number).toLong()

        // Create a todo list via REST (with createdBy = alice)
        val listResponse = restTemplate.postForEntity(
            "/api/todo-lists",
            CreateTodoListRequest(title = "Shared List", description = "Test", createdBy = userId),
            Map::class.java
        )
        assertEquals(HttpStatus.OK, listResponse.statusCode)
        val listId = (listResponse.body!!["id"] as Number).toLong()

        // Share with Bob
        val shareResponse = restTemplate.postForEntity(
            "/api/todo-lists/$listId/share",
            ShareTodoListRequest(userId = user2Id),
            Map::class.java
        )
        assertEquals(HttpStatus.OK, shareResponse.statusCode)
        assertEquals(listId, (shareResponse.body!!["todoListId"] as Number).toLong(),
            "Share response should reference the todo list")
        assertEquals(user2Id, (shareResponse.body!!["userId"] as Number).toLong(),
            "Share response should reference Bob")

        // Retrieve shares
        val sharesResponse = restTemplate.getForEntity(
            "/api/todo-lists/$listId/shares",
            List::class.java
        )
        assertEquals(HttpStatus.OK, sharesResponse.statusCode)
        assertEquals(1, sharesResponse.body?.size, "Expected 1 share")

        val share = (sharesResponse.body!![0] as Map<String, Any>)
        assertEquals(user2Id, (share["userId"] as Number).toLong(),
            "Shared user should be Bob")
    }

    // -----------------------------------------------------------------------
    // TEST-06: DSL seeds labels with composite FK
    // -----------------------------------------------------------------------

    @Test
    fun `DSL seeds labels with composite FK`() {
        DecDsl.execute(dslContext) {
            appUser {
                name = "Alice"; email = "alice@example.com"
                todoList(TodoList.TODO_LIST.CREATED_BY) {
                    title = "Sprint Tasks"
                    val fixBug = todoItem {
                        title = "Fix bug"
                    }
                    label {
                        name = "urgent"
                        color = "#ef4444"
                        todoItemLabel {
                            todoItem = fixBug
                        }
                    }
                    label {
                        name = "later"
                        color = "#22c55e"
                    }
                }
            }
        }

        val labelCount = dslContext.selectCount().from(Label.LABEL).fetchOne(0, Int::class.java)!!
        assertEquals(2, labelCount, "Expected 2 label rows")

        val tilCount = dslContext.selectCount().from(TodoItemLabel.TODO_ITEM_LABEL).fetchOne(0, Int::class.java)!!
        assertEquals(1, tilCount, "Expected 1 todo_item_label row")

        val til = dslContext.selectFrom(TodoItemLabel.TODO_ITEM_LABEL).fetchOne()!!
        val listId = dslContext.select(TODO_LIST.ID).from(TODO_LIST).fetchOne(TODO_LIST.ID)
        assertEquals(listId, til.get(TodoItemLabel.TODO_ITEM_LABEL.TODO_LIST_ID),
            "todo_item_label.todo_list_id should match the parent label's todo_list_id (composite FK)")

        assertEquals("urgent", til.get(TodoItemLabel.TODO_ITEM_LABEL.LABEL_NAME),
            "todo_item_label.label_name should match the parent label's name (composite FK)")

        val itemId = dslContext.select(TODO_ITEM.ID).from(TODO_ITEM).fetchOne(TODO_ITEM.ID)
        assertEquals(itemId, til.get(TodoItemLabel.TODO_ITEM_LABEL.TODO_ITEM_ID),
            "todo_item_label.todo_item_id should reference the todo_item via placeholder")
    }
}
