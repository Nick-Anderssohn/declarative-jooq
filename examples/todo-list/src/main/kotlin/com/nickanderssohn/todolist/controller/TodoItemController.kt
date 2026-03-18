package com.nickanderssohn.todolist.controller

import com.nickanderssohn.todolist.model.TodoItemDto
import com.nickanderssohn.todolist.model.toDto
import com.nickanderssohn.todolist.repository.TodoItemRepository
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

data class CreateTodoItemRequest(val title: String, val createdBy: Long? = null)
data class UpdateTodoItemRequest(val completed: Boolean)

@RestController
@RequestMapping("/api/todo-lists/{listId}/items")
class TodoItemController(private val repository: TodoItemRepository) {

    @GetMapping
    fun getItems(@PathVariable listId: Long): List<TodoItemDto> =
        repository.findByListId(listId).map { it.toDto() }

    @PostMapping
    fun createItem(
        @PathVariable listId: Long,
        @RequestBody request: CreateTodoItemRequest
    ): TodoItemDto = repository.create(listId, request.title, request.createdBy).toDto()

    @PatchMapping("/{itemId}")
    fun updateCompleted(
        @PathVariable listId: Long,
        @PathVariable itemId: Long,
        @RequestBody request: UpdateTodoItemRequest
    ): ResponseEntity<TodoItemDto> {
        val updated = repository.updateCompleted(itemId, request.completed)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(updated.toDto())
    }

    @DeleteMapping("/{itemId}")
    fun deleteItem(
        @PathVariable listId: Long,
        @PathVariable itemId: Long
    ): ResponseEntity<Void> {
        repository.delete(itemId)
        return ResponseEntity.noContent().build()
    }
}
