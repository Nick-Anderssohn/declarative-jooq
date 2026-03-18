package com.nickanderssohn.todolist.controller

import com.nickanderssohn.todolist.model.UserDto
import com.nickanderssohn.todolist.model.toDto
import com.nickanderssohn.todolist.repository.UserRepository
import org.springframework.web.bind.annotation.*

data class CreateUserRequest(val name: String, val email: String)

@RestController
@RequestMapping("/api/users")
class UserController(private val repository: UserRepository) {

    @GetMapping
    fun getAll(): List<UserDto> = repository.findAll().map { it.toDto() }

    @PostMapping
    fun create(@RequestBody request: CreateUserRequest): UserDto =
        repository.create(request.name, request.email).toDto()
}
