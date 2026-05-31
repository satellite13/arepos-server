package ru.kavader.arepos.controller

import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.model.Role
import ru.kavader.arepos.model.Users
import ru.kavader.arepos.repository.UsersRepository
import ru.kavader.arepos.metrics.CustomMetricsService
import ru.kavader.arepos.security.JwtTokenProvider
import java.time.Instant
import java.util.*

@RestController
@RequestMapping("/api/v1/auth")
class AuthController(
    private val usersRepository: UsersRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtTokenProvider: JwtTokenProvider,
    private val userProfileAttrsService: UserProfileAttrsService,
    private val metrics: CustomMetricsService,
    @param:Value("\${arepos.admin-secret:}") private val adminSecret: String
) {

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    fun register(@RequestBody request: RegisterRequest): AuthResponse {
        if (usersRepository.existsByEmail(request.email)) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "User with email ${request.email} already exists")
        }
        val now = Instant.now()
        val user = usersRepository.save(
            Users(
                email = request.email,
                passwordHash = passwordEncoder.encode(request.password),
                attrs = userProfileAttrsService.buildProfileAttrs(
                    firstName = request.firstName,
                    lastName = request.lastName,
                    middleName = request.middleName,
                    position = request.position
                ),
                role = Role.USER,
                createdAt = now,
                updatedAt = now
            )
        )
        return buildAuthResponse(user)
    }

    @PostMapping("/login")
    fun login(@RequestBody request: LoginRequest): AuthResponse {
        val user = usersRepository.findByEmail(request.email)
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password")

        if (!user.isActive) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Account is deactivated")
        }

        if (user.passwordHash == null || !passwordEncoder.matches(request.password, user.passwordHash)) {
            metrics.authLoginFailure.increment()
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password")
        }

        metrics.authLoginSuccess.increment()
        return buildAuthResponse(user)
    }

    @PostMapping("/refresh")
    fun refresh(@RequestBody request: RefreshRequest): AuthResponse {
        if (!jwtTokenProvider.validateToken(request.refreshToken)) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token")
        }

        val tokenType = jwtTokenProvider.getTokenType(request.refreshToken)
        if (tokenType != "refresh") {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token type")
        }

        val userId = jwtTokenProvider.getUserId(request.refreshToken)
        val user = usersRepository.findById(userId)
            .orElseThrow { ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found") }

        if (!user.isActive) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Account is deactivated")
        }

        return buildAuthResponse(user)
    }

    @PostMapping("/register-admin")
    @ResponseStatus(HttpStatus.CREATED)
    fun registerAdmin(@RequestBody request: AdminRegisterRequest): AuthResponse {
        if (adminSecret.isBlank()) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Admin registration is not configured")
        }
        if (request.adminSecret != adminSecret) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid admin secret")
        }
        if (usersRepository.existsByEmail(request.email)) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "User with email ${request.email} already exists")
        }
        val now = Instant.now()
        val user = usersRepository.save(
            Users(
                email = request.email,
                passwordHash = passwordEncoder.encode(request.password),
                attrs = userProfileAttrsService.buildProfileAttrs(
                    firstName = request.firstName,
                    lastName = request.lastName,
                    middleName = request.middleName,
                    position = request.position
                ),
                role = Role.ADMIN,
                createdAt = now,
                updatedAt = now
            )
        )
        return buildAuthResponse(user)
    }

    @GetMapping("/me")
    fun me(): UserInfoResponse {
        val userId = SecurityContextHolder.getContext().authentication?.principal as? UUID
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated")

        val user = usersRepository.findById(userId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "User not found") }

        val profile = userProfileAttrsService.readProfile(user.attrs)
        return UserInfoResponse(
            id = user.id!!,
            email = user.email,
            role = user.role.name,
            firstName = profile.firstName,
            lastName = profile.lastName,
            middleName = profile.middleName,
            position = profile.position,
            attrs = user.attrs,
            createdAt = user.createdAt,
            updatedAt = user.updatedAt
        )
    }

    private fun buildAuthResponse(user: Users): AuthResponse {
        val userId = requireNotNull(user.id)
        val accessToken = jwtTokenProvider.generateAccessToken(userId, user.role.name)
        val refreshToken = jwtTokenProvider.generateRefreshToken(userId)
        val profile = userProfileAttrsService.readProfile(user.attrs)
        return AuthResponse(
            accessToken = accessToken,
            refreshToken = refreshToken,
            user = UserInfoResponse(
                id = userId,
                email = user.email,
                role = user.role.name,
                firstName = profile.firstName,
                lastName = profile.lastName,
                middleName = profile.middleName,
                position = profile.position,
                attrs = user.attrs,
                createdAt = user.createdAt,
                updatedAt = user.updatedAt
            )
        )
    }
}

data class RegisterRequest(
    val email: String,
    val password: String,
    val firstName: String,
    val lastName: String,
    val middleName: String? = null,
    val position: String? = null
)

data class LoginRequest(
    val email: String,
    val password: String
)

data class RefreshRequest(
    val refreshToken: String
)

data class AdminRegisterRequest(
    val email: String,
    val password: String,
    val firstName: String,
    val lastName: String,
    val middleName: String? = null,
    val position: String? = null,
    val adminSecret: String
)

data class AuthResponse(
    val accessToken: String,
    val refreshToken: String,
    val user: UserInfoResponse
)

data class UserInfoResponse(
    val id: UUID,
    val email: String,
    val role: String,
    val firstName: String?,
    val lastName: String?,
    val middleName: String?,
    val position: String?,
    val attrs: String?,
    val createdAt: Instant?,
    val updatedAt: Instant?
)
