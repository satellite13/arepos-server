package ru.kavader.arepos.controller

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.model.Users
import ru.kavader.arepos.repository.UsersRepository
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("/api/v1/users")
class UsersController(
    private val usersRepository: UsersRepository
) {

    @GetMapping
    fun listUsers(
        pageable: Pageable,
        @RequestParam(required = false) email: String?
    ): Page<UserResponse> {
        val users = if (email != null) {
            usersRepository.findByEmailContainingIgnoreCase(email, pageable)
        } else {
            usersRepository.findAll(pageable)
        }
        return users.map { it.toResponse() }
    }

    @GetMapping("/{id}")
    fun getUser(@PathVariable id: UUID): UserResponse =
        usersRepository.findById(id)
            .map { it.toResponse() }
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "User $id not found")
            }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createUser(@RequestBody request: UserRequest): UserResponse {
        if (usersRepository.existsByEmail(request.email)) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "User with email ${request.email} already exists")
        }
        val saved = usersRepository.save(
            Users(
                email = request.email,
                attrs = request.attrs,
                createdAt = Instant.now()
            )
        )
        return saved.toResponse()
    }

    @PutMapping("/{id}")
    fun updateUser(
        @PathVariable id: UUID,
        @RequestBody request: UserUpdateRequest
    ): UserResponse {
        val user = usersRepository.findById(id)
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "User $id not found")
            }
        
        request.email?.let { newEmail ->
            if (newEmail != user.email && usersRepository.existsByEmail(newEmail)) {
                throw ResponseStatusException(HttpStatus.CONFLICT, "User with email $newEmail already exists")
            }
        }

        val updated = usersRepository.save(
            user.copy(
                email = request.email ?: user.email,
                attrs = request.attrs ?: user.attrs
            )
        )
        return updated.toResponse()
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteUser(@PathVariable id: UUID) {
        if (!usersRepository.existsById(id)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "User $id not found")
        }
        usersRepository.deleteById(id)
    }

    private fun Users.toResponse() = UserResponse(
        id = requireNotNull(id),
        email = email,
        attrs = attrs,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

data class UserRequest(
    val email: String,
    val attrs: String? = null
)

data class UserUpdateRequest(
    val email: String? = null,
    val attrs: String? = null
)

data class UserResponse(
    val id: UUID,
    val email: String,
    val attrs: String?,
    val createdAt: Instant?,
    val updatedAt: Instant?
)

