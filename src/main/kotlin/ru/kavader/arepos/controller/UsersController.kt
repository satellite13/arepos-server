package ru.kavader.arepos.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.dto.common.ListResponse
import ru.kavader.arepos.dto.common.toListResponse
import ru.kavader.arepos.dto.user.*
import ru.kavader.arepos.mapper.UserMapper
import ru.kavader.arepos.model.Role
import ru.kavader.arepos.model.Users
import ru.kavader.arepos.repository.UsersRepository
import ru.kavader.arepos.security.PasswordPolicyValidator
import ru.kavader.arepos.security.ResourceAccessService
import ru.kavader.arepos.service.UserProfileAttrsService
import java.time.Instant
import java.util.*

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Users", description = "User management and profile endpoints")
class UsersController(
    private val usersRepository: UsersRepository,
    private val passwordEncoder: PasswordEncoder,
    private val userProfileAttrsService: UserProfileAttrsService,
    private val accessService: ResourceAccessService,
    private val userMapper: UserMapper,
    private val passwordPolicyValidator: PasswordPolicyValidator
) {

    @GetMapping
    @Operation(summary = "List users")
    fun listUsers(
        pageable: Pageable,
        @RequestParam(required = false) email: String?,
        @RequestParam(required = false) search: String?
    ): ListResponse<UserResponse> {
        accessService.requireCanManageUsers()
        val users = when {
            email != null -> usersRepository.findByEmailContainingIgnoreCase(email, pageable)
            search != null -> usersRepository.searchByEmailOrOidcSubContaining(search, pageable)
            else -> usersRepository.findAll(pageable)
        }
        return users.map { userMapper.toResponse(it) }.toListResponse()
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get user by id")
    fun getUser(@PathVariable id: UUID): UserResponse {
        accessService.requireCanManageUsers()
        return usersRepository.findById(id)
            .map { userMapper.toResponse(it) }
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "User $id not found")
            }
    }

    @GetMapping("/{id}/public")
    @Operation(summary = "Get public user profile by id")
    @PreAuthorize("isAuthenticated()")
    fun getUserPublic(@PathVariable id: UUID): UserPublicResponse =
        usersRepository.findById(id)
            .map {
                requirePublicUserVisible(it)
                userMapper.toPublicResponse(it)
            }
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "User $id not found")
            }

    @GetMapping("/public/by-email")
    @Operation(summary = "Get public user profile by email")
    @PreAuthorize("isAuthenticated()")
    fun getUserPublicByEmail(@RequestParam email: String): UserPublicResponse {
        val normalizedEmail = email.trim()
        if (normalizedEmail.isEmpty()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Email is required")
        }

        val user = usersRepository.findByEmailIgnoreCase(normalizedEmail)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "User with email $normalizedEmail not found")
        requirePublicUserVisible(user)
        return userMapper.toPublicResponse(user)
    }

    @GetMapping("/public/search")
    @Operation(summary = "Search public user profiles")
    @PreAuthorize("isAuthenticated()")
    fun searchUsersPublic(
        pageable: Pageable,
        @RequestParam email: String
    ): ListResponse<UserPublicResponse> {
        val normalizedEmail = email.trim()
        if (normalizedEmail.isEmpty()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Email is required")
        }

        return usersRepository.findByEmailContainingIgnoreCaseAndRoleNot(normalizedEmail, Role.ADMIN, pageable)
            .map { userMapper.toPublicResponse(it) }
            .toListResponse()
    }

    @PostMapping("/public/batch")
    @Operation(summary = "Get public user profiles in batch")
    @PreAuthorize("isAuthenticated()")
    fun getUsersBatch(@RequestBody @Valid request: BatchUserPublicRequest): ListResponse<UserPublicResponse> {
        if (request.ids.isEmpty()) return emptyList<UserPublicResponse>().toListResponse()
        val ids = request.ids.distinct().take(100)
        return usersRepository.findAllById(ids)
            .filter { it.role != Role.ADMIN }
            .map { userMapper.toPublicResponse(it) }
            .toListResponse()
    }

    @GetMapping("/me/profile")
    @Operation(summary = "Get current user profile")
    @PreAuthorize("isAuthenticated()")
    fun getCurrentUserProfile(): UserPublicResponse {
        val currentUserId = accessService.currentUserId()

        return usersRepository.findById(currentUserId)
            .map { userMapper.toPublicResponse(it) }
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "User $currentUserId not found")
            }
    }

    @PostMapping
    @Operation(summary = "Create user")
    @ResponseStatus(HttpStatus.CREATED)
    fun createUser(@RequestBody @Valid request: UserRequest): UserResponse {
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
        return userMapper.toResponse(saved)
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update user")
    fun updateUser(
        @PathVariable id: UUID,
        @RequestBody @Valid request: UserUpdateRequest
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
            passwordPolicyValidator.validateOrThrow(newPassword, user.email)
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

        user.email = request.email ?: user.email
        user.attrs = nextAttrs
        user.role = request.role ?: user.role
        user.isActive = request.isActive ?: user.isActive
        user.passwordHash = request.password?.let(passwordEncoder::encode) ?: user.passwordHash
        val updated = usersRepository.save(user)
        return userMapper.toResponse(updated)
    }

    @PutMapping("/me/profile")
    @Operation(summary = "Update current user profile")
    @PreAuthorize("isAuthenticated()")
    fun updateMyProfile(@RequestBody @Valid request: UserProfileUpdateRequest): UserPublicResponse {
        val currentUserId = accessService.currentUserId()

        val user = usersRepository.findById(currentUserId)
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "User $currentUserId not found")
            }

        user.attrs = userProfileAttrsService.mergeProfile(
            existingAttrs = user.attrs,
            patch = UserProfilePatch(
                firstName = request.firstName,
                lastName = request.lastName,
                middleName = request.middleName,
                position = request.position
            )
        )
        val updated = usersRepository.save(user)

        return userMapper.toPublicResponse(updated)
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete user")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteUser(@PathVariable id: UUID) {
        accessService.requireCanManageUsers()
        if (!usersRepository.existsById(id)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "User $id not found")
        }
        usersRepository.deleteById(id)
    }


    private fun requirePublicUserVisible(user: Users) {
        if (user.role == Role.ADMIN) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "User ${user.id} not found")
        }
    }
}

