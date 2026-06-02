package ru.kavader.arepos.controller

import org.springframework.beans.factory.annotation.Value
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import jakarta.validation.Valid
import ru.kavader.arepos.model.Role
import ru.kavader.arepos.model.RefreshTokens
import ru.kavader.arepos.model.Users
import ru.kavader.arepos.repository.RefreshTokensRepository
import ru.kavader.arepos.repository.UsersRepository
import ru.kavader.arepos.metrics.CustomMetricsService
import ru.kavader.arepos.dto.auth.*
import ru.kavader.arepos.security.JwtTokenProvider
import ru.kavader.arepos.security.TokenType
import ru.kavader.arepos.service.UserProfileAttrsService
import java.security.MessageDigest
import java.time.Instant
import java.util.*

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth", description = "Authentication and profile endpoints")
class AuthController(
    private val usersRepository: UsersRepository,
    private val refreshTokensRepository: RefreshTokensRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtTokenProvider: JwtTokenProvider,
    private val userProfileAttrsService: UserProfileAttrsService,
    private val metrics: CustomMetricsService,
    @param:Value($$"${arepos.admin-secret:}") private val adminSecret: String
) {
    private val dummyPasswordHash by lazy {
        passwordEncoder.encode("dummy-password-for-timing-equalization")
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register user account")
    fun register(@RequestBody @Valid request: RegisterRequest): AuthResponse {
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
    @Operation(summary = "Login with email and password")
    fun login(@RequestBody @Valid request: LoginRequest): AuthResponse {
        val user = usersRepository.findByEmail(request.email)
        val passwordHash = user?.passwordHash ?: dummyPasswordHash
        val passwordMatches = passwordEncoder.matches(request.password, passwordHash)
        if (user == null || user.passwordHash == null || !passwordMatches) {
            metrics.authLoginFailure.increment()
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password")
        }
        if (!user.isActive) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Account is deactivated")
        }

        metrics.authLoginSuccess.increment()
        return buildAuthResponse(user)
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh JWT token pair")
    @Transactional
    fun refresh(@RequestBody @Valid request: RefreshRequest): AuthResponse {
        if (!jwtTokenProvider.validateToken(request.refreshToken)) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token")
        }

        val tokenType = jwtTokenProvider.getTokenType(request.refreshToken)
        if (tokenType != TokenType.REFRESH) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token type")
        }

        val tokenHash = hashToken(request.refreshToken)
        val persistedToken = refreshTokensRepository.findByTokenHash(tokenHash)
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token")
        val userId = jwtTokenProvider.getUserId(request.refreshToken)
        if (persistedToken.user.id != userId) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token")
        }
        val now = Instant.now()
        val marked = refreshTokensRepository.markUsed(tokenHash, userId, now)
        if (marked == 0) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token already used or expired")
        }

        val user = usersRepository.findById(userId)
            .orElseThrow { ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found") }

        if (!user.isActive) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Account is deactivated")
        }

        return buildAuthResponse(user)
    }

    @PostMapping("/register-admin")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register admin account with shared secret")
    fun registerAdmin(@RequestBody @Valid request: AdminRegisterRequest): AuthResponse {
        if (adminSecret.isBlank()) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Admin registration is not configured")
        }
        val providedSecret = request.adminSecret.toByteArray(Charsets.UTF_8)
        val expectedSecret = adminSecret.toByteArray(Charsets.UTF_8)
        if (!MessageDigest.isEqual(providedSecret, expectedSecret)) {
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
    @Operation(summary = "Get current authenticated user")
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
        persistRefreshToken(user, refreshToken)
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

    private fun persistRefreshToken(user: Users, refreshToken: String) {
        val now = Instant.now()
        refreshTokensRepository.save(
            RefreshTokens(
                user = user,
                tokenHash = hashToken(refreshToken),
                expiresAt = jwtTokenProvider.getExpirationInstant(refreshToken),
                createdAt = now
            )
        )
    }

    private fun hashToken(token: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(token.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}

