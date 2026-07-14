package ru.kavader.arepos.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.config.AreposAuthProperties
import ru.kavader.arepos.dto.auth.*
import ru.kavader.arepos.mapper.UserMapper
import ru.kavader.arepos.metrics.CustomMetricsService
import ru.kavader.arepos.model.Role
import ru.kavader.arepos.model.Users
import ru.kavader.arepos.repository.UsersRepository
import ru.kavader.arepos.security.AuthCookieService
import ru.kavader.arepos.security.AuthCookies
import ru.kavader.arepos.security.PasswordPolicyValidator
import ru.kavader.arepos.service.AuthTokenService
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
    private val passwordEncoder: PasswordEncoder,
    private val userProfileAttrsService: UserProfileAttrsService,
    private val userMapper: UserMapper,
    private val metrics: CustomMetricsService,
    private val authCookieService: AuthCookieService,
    private val authTokenService: AuthTokenService,
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
    fun refresh(
        @RequestBody(required = false) request: RefreshRequest?,
        @CookieValue(name = AuthCookies.REFRESH, required = false) refreshCookie: String?,
        response: HttpServletResponse
    ): AuthResponse {
        val refreshToken = request?.refreshToken?.trim()?.takeIf { it.isNotEmpty() }
            ?: refreshCookie?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token is required")

        val result = authTokenService.refresh(refreshToken)
        writeAuthCookies(result.response, result.csrfToken, response)
        return result.response
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Logout and revoke refresh token")
    fun logout(
        @CookieValue(name = AuthCookies.REFRESH, required = false) refreshCookie: String?,
        response: HttpServletResponse
    ) {
        authTokenService.logout(refreshCookie?.trim()?.takeIf { it.isNotEmpty() })
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
        val result = authTokenService.issue(user)
        writeAuthCookies(result.response, result.csrfToken, response)
        return result.response
    }

    private fun writeAuthCookies(
        authResponse: AuthResponse,
        csrfToken: String,
        response: HttpServletResponse
    ) {
        authCookieService.writeAuthCookies(
            response,
            authResponse.accessToken,
            authResponse.refreshToken,
            csrfToken
        )
    }
}
