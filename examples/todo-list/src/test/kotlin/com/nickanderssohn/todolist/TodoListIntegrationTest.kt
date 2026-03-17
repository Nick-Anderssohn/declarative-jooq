package com.nickanderssohn.todolist

import com.nickanderssohn.declarativejooq.execute
import com.nickanderssohn.todolist.generated.todoList
import com.nickanderssohn.todolist.controller.CreateTodoItemRequest
import com.nickanderssohn.todolist.controller.CreateTodoListRequest
import com.nickanderssohn.todolist.jooq.TodoItemTable.Companion.TODO_ITEM
import com.nickanderssohn.todolist.jooq.TodoListTable.Companion.TODO_LIST
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
        dslContext.execute("TRUNCATE todo_item, todo_list RESTART IDENTITY CASCADE")
    }

    // -----------------------------------------------------------------------
    // Test: declarative-jooq DSL seeds todo data correctly
    // -----------------------------------------------------------------------

    @Test
    fun `declarative-jooq seeds test data correctly`() {
        val result = execute(dslContext) {
            todoList {
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
        execute(dslContext) {
            todoList {
                title = "Work Tasks"
                todoItem {
                    title = "Write tests"
                }
                todoItem {
                    title = "Review PR"
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
}
