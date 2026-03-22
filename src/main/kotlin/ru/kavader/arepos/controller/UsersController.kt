package ru.kavader.arepos.controller

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.model.Role
import ru.kavader.arepos.model.Users
import ru.kavader.arepos.repository.UsersRepository
import ru.kavader.arepos.security.ResourceAccessService
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("/api/v1/users")
class UsersController(
    private val usersRepository: UsersRepository,
    private val passwordEncoder: PasswordEncoder,
    private val userProfileAttrsService: UserProfileAttrsService,
    private val accessService: ResourceAccessService
) {

    @GetMapping
    fun listUsers(
        pageable: Pageable,
        @RequestParam(required = false) email: String?
    ): Page<UserResponse> {
        accessService.requireCanManageUsers()
        val users = if (email != null) {
            usersRepository.findByEmailContainingIgnoreCase(email, pageable)
        } else {
            usersRepository.findAll(pageable)
        }
        return users.map { it.toResponse() }
    }

    @GetMapping("/{id}")
    fun getUser(@PathVariable id: UUID): UserResponse {
        accessService.requireCanManageUsers()
        return usersRepository.findById(id)
            .map { it.toResponse() }
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "User $id not found")
            }
    }

    @GetMapping("/{id}/public")
    @PreAuthorize("isAuthenticated()")
    fun getUserPublic(@PathVariable id: UUID): UserPublicResponse =
        usersRepository.findById(id)
            .map { it.toPublicResponse() }
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "User $id not found")
            }

    @GetMapping("/public/by-email")
    @PreAuthorize("isAuthenticated()")
    fun getUserPublicByEmail(@RequestParam email: String): UserPublicResponse {
        val normalizedEmail = email.trim()
        if (normalizedEmail.isEmpty()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Email is required")
        }

        return usersRepository.findByEmailIgnoreCase(normalizedEmail)
            ?.toPublicResponse()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "User with email $normalizedEmail not found")
    }

    @GetMapping("/public/search")
    @PreAuthorize("isAuthenticated()")
    fun searchUsersPublic(
        pageable: Pageable,
        @RequestParam email: String
    ): Page<UserPublicResponse> {
        val normalizedEmail = email.trim()
        if (normalizedEmail.isEmpty()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Email is required")
        }

        return usersRepository.findByEmailContainingIgnoreCaseAndRoleNot(normalizedEmail, Role.ADMIN, pageable)
            .map { it.toPublicResponse() }
    }

    @PostMapping("/public/batch")
    @PreAuthorize("isAuthenticated()")
    fun getUsersBatch(@RequestBody request: BatchUserPublicRequest): Map<UUID, UserPublicResponse> {
        if (request.ids.isEmpty()) return emptyMap()
        val ids = request.ids.distinct().take(100)
        return usersRepository.findAllById(ids)
            .associate { requireNotNull(it.id) to it.toPublicResponse() }
    }

    @GetMapping("/me/profile")
    @PreAuthorize("isAuthenticated()")
    fun getCurrentUserProfile(): UserPublicResponse {
        val currentUserId = SecurityContextHolder.getContext().authentication?.principal as? UUID
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated")

        return usersRepository.findById(currentUserId)
            .map { it.toPublicResponse() }
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "User $currentUserId not found")
            }
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createUser(@RequestBody request: UserRequest): UserResponse {
        accessService.requireCanManageUsers()
        if (usersRepository.existsByEmail(request.email)) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "User with email ${request.email} already exists")
        }
        val now = Instant.now()
        val saved = usersRepository.save(
            Users(
                email = request.email,
                attrs = request.attrs,
                role = request.role ?: Role.USER,
                createdAt = now,
                updatedAt = now
            )
        )
        return saved.toResponse()
    }

    @PutMapping("/{id}")
    fun updateUser(
        @PathVariable id: UUID,
        @RequestBody request: UserUpdateRequest
    ): UserResponse {
        accessService.requireCanManageUsers()
        val user = usersRepository.findById(id)
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "User $id not found")
            }

        request.email?.let { newEmail ->
            if (newEmail != user.email && usersRepository.existsByEmail(newEmail)) {
                throw ResponseStatusException(HttpStatus.CONFLICT, "User with email $newEmail already exists")
            }
        }
        request.password?.let { newPassword ->
            if (newPassword.length < 6) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must be at least 6 characters")
            }
        }

        val nextAttrs = userProfileAttrsService.mergeProfile(
            existingAttrs = request.attrs ?: user.attrs,
            patch = UserProfilePatch(
                firstName = request.firstName,
                lastName = request.lastName,
                middleName = request.middleName,
                position = request.position
            )
        )

        val updated = usersRepository.save(
            user.copy(
                email = request.email ?: user.email,
                attrs = nextAttrs,
                role = request.role ?: user.role,
                isActive = request.isActive ?: user.isActive,
                passwordHash = request.password?.let(passwordEncoder::encode) ?: user.passwordHash
            )
        )
        return updated.toResponse()
    }

    @PutMapping("/me/profile")
    @PreAuthorize("isAuthenticated()")
    fun updateMyProfile(@RequestBody request: UserProfileUpdateRequest): UserPublicResponse {
        val currentUserId = SecurityContextHolder.getContext().authentication?.principal as? UUID
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated")

        val user = usersRepository.findById(currentUserId)
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "User $currentUserId not found")
            }

        val updated = usersRepository.save(
            user.copy(
                attrs = userProfileAttrsService.mergeProfile(
                    existingAttrs = user.attrs,
                    patch = UserProfilePatch(
                        firstName = request.firstName,
                        lastName = request.lastName,
                        middleName = request.middleName,
                        position = request.position
                    )
                )
            )
        )

        return updated.toPublicResponse()
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteUser(@PathVariable id: UUID) {
        accessService.requireCanManageUsers()
        if (!usersRepository.existsById(id)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "User $id not found")
        }
        usersRepository.deleteById(id)
    }

    private fun Users.toResponse(): UserResponse {
        val profile = userProfileAttrsService.readProfile(attrs)
        return UserResponse(
            id = requireNotNull(id),
            email = email,
            role = role.name,
            isActive = isActive,
            firstName = profile.firstName,
            lastName = profile.lastName,
            middleName = profile.middleName,
            position = profile.position,
            attrs = attrs,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    private fun Users.toPublicResponse(): UserPublicResponse {
        val profile = userProfileAttrsService.readProfile(attrs)
        return UserPublicResponse(
            id = requireNotNull(id),
            email = email,
            firstName = profile.firstName,
            lastName = profile.lastName,
            middleName = profile.middleName,
            position = profile.position
        )
    }
}

data class UserRequest(
    val email: String,
    val attrs: String? = null,
    val role: Role? = null
)

data class UserUpdateRequest(
    val email: String? = null,
    val attrs: String? = null,
    val role: Role? = null,
    val isActive: Boolean? = null,
    val password: String? = null,
    val firstName: String? = null,
    val lastName: String? = null,
    val middleName: String? = null,
    val position: String? = null
)

data class UserProfileUpdateRequest(
    val firstName: String? = null,
    val lastName: String? = null,
    val middleName: String? = null,
    val position: String? = null
)

data class UserResponse(
    val id: UUID,
    val email: String,
    val role: String,
    val isActive: Boolean,
    val firstName: String?,
    val lastName: String?,
    val middleName: String?,
    val position: String?,
    val attrs: String?,
    val createdAt: Instant?,
    val updatedAt: Instant?
)

data class UserPublicResponse(
    val id: UUID,
    val email: String,
    val firstName: String?,
    val lastName: String?,
    val middleName: String?,
    val position: String?
)

data class BatchUserPublicRequest(val ids: List<UUID>)
