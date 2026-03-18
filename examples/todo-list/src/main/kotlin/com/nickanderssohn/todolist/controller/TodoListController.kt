package com.nickanderssohn.todolist.controller

import com.nickanderssohn.todolist.model.TodoListDto
import com.nickanderssohn.todolist.model.toDto
import com.nickanderssohn.todolist.repository.TodoListRepository
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

data class CreateTodoListRequest(val title: String, val description: String? = null, val createdBy: Long? = null)

@RestController
@RequestMapping("/api/todo-lists")
class TodoListController(private val repository: TodoListRepository) {

    @GetMapping
    fun getAll(): List<TodoListDto> = repository.findAll().map { it.toDto() }

    @GetMapping("/{id}")
    fun getById(@PathVariable id: Long): ResponseEntity<TodoListDto> {
        val record = repository.findById(id)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(record.toDto())
    }

    @PostMapping
    fun create(@RequestBody request: CreateTodoListRequest): TodoListDto =
        repository.create(request.title, request.description, request.createdBy).toDto()

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: Long): ResponseEntity<Void> {
        repository.delete(id)
        return ResponseEntity.noContent().build()
    }
}
