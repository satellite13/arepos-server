package ru.kavader.arepos.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.config.AreposAuthProperties
import ru.kavader.arepos.dto.auth.*
import ru.kavader.arepos.dto.user.UserMapper
import ru.kavader.arepos.metrics.CustomMetricsService
import ru.kavader.arepos.model.RefreshTokens
import ru.kavader.arepos.model.Role
import ru.kavader.arepos.model.Users
import ru.kavader.arepos.repository.RefreshTokensRepository
import ru.kavader.arepos.repository.UsersRepository
import ru.kavader.arepos.security.AuthCookieService
import ru.kavader.arepos.security.AuthCookies
import ru.kavader.arepos.security.JwtTokenProvider
import ru.kavader.arepos.security.PasswordPolicyValidator
import ru.kavader.arepos.security.TokenType
import ru.kavader.arepos.service.UserProfileAttrsService
import java.security.MessageDigest
import java.time.Duration
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
    private val userMapper: UserMapper,
    private val metrics: CustomMetricsService,
    private val authCookieService: AuthCookieService,
    private val authProperties: AreposAuthProperties,
    private val passwordPolicyValidator: PasswordPolicyValidator,
    @param:Value($$"${arepos.admin-secret:}") private val adminSecret: String
) {
    private val dummyPasswordHash by lazy {
        passwordEncoder.encode("dummy-password-for-timing-equalization")
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register user account")
    fun register(
        @RequestBody @Valid request: RegisterRequest,
        response: HttpServletResponse
    ): AuthResponse {
        ensureRegistrationEnabled()
        passwordPolicyValidator.validateOrThrow(request.password, request.email)
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
        return buildAuthResponse(user, response)
    }

    @PostMapping("/login")
    @Operation(summary = "Login with email and password")
    fun login(
        @RequestBody @Valid request: LoginRequest,
        response: HttpServletResponse
    ): AuthResponse {
        val user = usersRepository.findByEmail(request.email)
        if (user != null) {
            ensureAccountNotLocked(user, response)
        }
        val passwordHash = user?.passwordHash ?: dummyPasswordHash
        val passwordMatches = passwordEncoder.matches(request.password, passwordHash)
        if (user == null || user.passwordHash == null || !passwordMatches) {
            if (user != null) {
                registerFailedLoginAttempt(user)
            } else {
                metrics.recordLoginFailure("bad_password")
            }
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password")
        }
        if (!user.isActive) {
            metrics.recordLoginFailure("inactive")
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Account is deactivated")
        }

        clearLoginFailures(user)
        metrics.authLoginSuccess.increment()
        return buildAuthResponse(user, response)
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh JWT token pair")
    @Transactional
    fun refresh(
        @RequestBody(required = false) request: RefreshRequest?,
        @CookieValue(name = AuthCookies.REFRESH, required = false) refreshCookie: String?,
        response: HttpServletResponse
    ): AuthResponse {
        val refreshToken = request?.refreshToken?.trim()?.takeIf { it.isNotEmpty() }
            ?: refreshCookie?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token is required")

        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token")
        }

        val tokenType = jwtTokenProvider.getTokenType(refreshToken)
        if (tokenType != TokenType.REFRESH) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token type")
        }

        val tokenHash = hashToken(refreshToken)
        val persistedToken = refreshTokensRepository.findByTokenHash(tokenHash)
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token")
        val userId = jwtTokenProvider.getUserId(refreshToken)
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

        return buildAuthResponse(user, response)
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Logout and revoke refresh token")
    @Transactional
    fun logout(
        @CookieValue(name = AuthCookies.REFRESH, required = false) refreshCookie: String?,
        response: HttpServletResponse
    ) {
        val refreshToken = refreshCookie?.trim()?.takeIf { it.isNotEmpty() }
        if (refreshToken != null && jwtTokenProvider.validateToken(refreshToken)) {
            val tokenType = jwtTokenProvider.getTokenType(refreshToken)
            if (tokenType == TokenType.REFRESH) {
                val tokenHash = hashToken(refreshToken)
                val userId = jwtTokenProvider.getUserId(refreshToken)
                refreshTokensRepository.markUsed(tokenHash, userId, Instant.now())
            }
        }
        authCookieService.clearAuthCookies(response)
    }

    @PostMapping("/register-admin")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register admin account with shared secret")
    fun registerAdmin(
        @RequestBody @Valid request: AdminRegisterRequest,
        response: HttpServletResponse
    ): AuthResponse {
        if (adminSecret.isBlank()) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Admin registration is not configured")
        }
        val providedSecret = request.adminSecret.toByteArray(Charsets.UTF_8)
        val expectedSecret = adminSecret.toByteArray(Charsets.UTF_8)
        if (!MessageDigest.isEqual(providedSecret, expectedSecret)) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid admin secret")
        }
        passwordPolicyValidator.validateOrThrow(request.password, request.email)
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
        return buildAuthResponse(user, response)
    }

    @GetMapping("/me")
    @Operation(summary = "Get current authenticated user")
    fun me(): UserInfoResponse {
        val userId = SecurityContextHolder.getContext().authentication?.principal as? UUID
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated")

        val user = usersRepository.findById(userId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "User not found") }

        return userMapper.toUserInfoResponse(user)
    }

    private fun ensureRegistrationEnabled() {
        if (!authProperties.registrationEnabled) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "User registration is disabled")
        }
    }

    private fun ensureAccountNotLocked(user: Users, response: HttpServletResponse) {
        val lockedUntil = user.lockedUntil ?: return
        val now = Instant.now()
        if (now.isBefore(lockedUntil)) {
            val retryAfterSeconds = Duration.between(now, lockedUntil).seconds.coerceAtLeast(1)
            response.setHeader("Retry-After", retryAfterSeconds.toString())
            metrics.recordLoginFailure("locked")
            throw ResponseStatusException(HttpStatus.LOCKED, "Account is temporarily locked")
        }
        if (user.failedLoginAttempts > 0) {
            clearLoginFailures(user)
        }
    }

    private fun registerFailedLoginAttempt(user: Users) {
        val now = Instant.now()
        user.failedLoginAttempts += 1
        if (user.failedLoginAttempts >= PasswordPolicyValidator.MAX_FAILED_ATTEMPTS) {
            user.lockedUntil = now.plusSeconds(PasswordPolicyValidator.LOCKOUT_MINUTES * 60)
            metrics.recordLoginFailure("locked")
        } else {
            metrics.recordLoginFailure("bad_password")
        }
        user.updatedAt = now
        usersRepository.save(user)
    }

    private fun clearLoginFailures(user: Users) {
        if (user.failedLoginAttempts == 0 && user.lockedUntil == null) {
            return
        }
        user.failedLoginAttempts = 0
        user.lockedUntil = null
        user.updatedAt = Instant.now()
        usersRepository.save(user)
    }

    private fun buildAuthResponse(user: Users, response: HttpServletResponse): AuthResponse {
        val userId = requireNotNull(user.id)
        val accessToken = jwtTokenProvider.generateAccessToken(userId, user.role.name)
        val refreshToken = jwtTokenProvider.generateRefreshToken(userId)
        val csrfToken = authCookieService.generateCsrfToken()
        persistRefreshToken(user, refreshToken)
        authCookieService.writeAuthCookies(response, accessToken, refreshToken, csrfToken)
        return AuthResponse(
            accessToken = accessToken,
            refreshToken = refreshToken,
            user = userMapper.toUserInfoResponse(user)
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
