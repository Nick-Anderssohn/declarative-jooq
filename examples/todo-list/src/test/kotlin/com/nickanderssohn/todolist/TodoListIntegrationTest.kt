package com.nickanderssohn.todolist

import com.nickanderssohn.declarativejooq.DecDsl
import com.nickanderssohn.todolist.generated.appUser
import com.nickanderssohn.todolist.controller.CreateTodoItemRequest
import com.nickanderssohn.todolist.controller.CreateTodoListRequest
import com.nickanderssohn.todolist.jooq.SharedWithTable.Companion.SHARED_WITH
import com.nickanderssohn.todolist.jooq.TodoItemTable.Companion.TODO_ITEM
import com.nickanderssohn.todolist.jooq.TodoItemTable
import com.nickanderssohn.todolist.jooq.TodoListTable.Companion.TODO_LIST
import com.nickanderssohn.todolist.jooq.TodoListTable
import com.nickanderssohn.todolist.jooq.UserTable.Companion.APP_USER
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
        dslContext.execute("TRUNCATE shared_with, todo_item, todo_list, app_user RESTART IDENTITY CASCADE")
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
                todoList(TodoListTable.TODO_LIST.CREATED_BY) {
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
        val todoListRecords = result.records<com.nickanderssohn.todolist.jooq.TodoListRecord>("todo_list")
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
                todoList(TodoListTable.TODO_LIST.CREATED_BY) {
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
                val myList = todoList(TodoListTable.TODO_LIST.CREATED_BY) {
                    title = "Alice's List"
                    description = "Test list"
                    updatedBy = bob
                }
                todoItem(TodoItemTable.TODO_ITEM.CREATED_BY) {
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
                todoList(TodoListTable.TODO_LIST.CREATED_BY) {
                    title = "List One"
                }
                todoList(TodoListTable.TODO_LIST.CREATED_BY) {
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
                todoList(TodoListTable.TODO_LIST.CREATED_BY) {
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
}
