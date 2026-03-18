package com.nickanderssohn.todolist.controller

import com.nickanderssohn.todolist.model.ShareDto
import com.nickanderssohn.todolist.model.toDto
import com.nickanderssohn.todolist.repository.SharedWithRepository
import org.springframework.web.bind.annotation.*

data class ShareTodoListRequest(val userId: Long)

@RestController
@RequestMapping("/api/todo-lists/{listId}")
class ShareController(private val repository: SharedWithRepository) {

    @PostMapping("/share")
    fun share(
        @PathVariable listId: Long,
        @RequestBody request: ShareTodoListRequest
    ): ShareDto = repository.create(listId, request.userId).toDto()

    @GetMapping("/shares")
    fun getShares(@PathVariable listId: Long): List<ShareDto> =
        repository.findByListId(listId).map { it.toDto() }
}
