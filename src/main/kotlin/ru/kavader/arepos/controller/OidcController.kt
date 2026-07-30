package ru.kavader.arepos.controller

import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.dto.auth.UserInfoResponse
import ru.kavader.arepos.model.Users
import ru.kavader.arepos.repository.UsersRepository
import ru.kavader.arepos.security.AuthCookieService
import ru.kavader.arepos.security.OidcAuthService
import ru.kavader.arepos.security.OidcProperties
import ru.kavader.arepos.security.OidcStateToken
import ru.kavader.arepos.service.AuthTokenService
import java.time.Instant
import java.util.*

data class OidcCallbackRequest(
    val code: String,
    val state: String
)

data class OidcLinkResponse(
    val accessToken: String,
    val refreshToken: String,
    val user: UserInfoResponse
)

data class OidcStatusResponse(
    val linked: Boolean,
    val oidcSub: String?
)

data class OidcConfigResponse(
    val enabled: Boolean,
    val displayName: String
)

@RestController
@RequestMapping("/api/v1/auth/sso")
class OidcController(
    private val oidcService: OidcAuthService,
    private val oidcProperties: OidcProperties,
    private val oidcStateToken: OidcStateToken,
    private val userRepository: UsersRepository,
    private val authTokenService: AuthTokenService,
    private val authCookieService: AuthCookieService
) {

    @GetMapping("/config")
    fun config(): OidcConfigResponse =
        OidcConfigResponse(
            enabled = oidcProperties.isEffectivelyEnabled(),
            displayName = oidcProperties.displayName.ifBlank { "SSO" }
        )

    @GetMapping("/authorize")
    fun authorize(@RequestParam(required = false) linkUserId: String?): Map<String, String> {
        requireOidcEnabled()
        val state = if (linkUserId != null) {
            oidcStateToken.generateStateToken(UUID.fromString(linkUserId))
        } else {
            oidcStateToken.generateStateToken(UUID.randomUUID())
        }
        val authUrl = oidcService.buildAuthorizationUrl(state)
        return mapOf("url" to authUrl)
    }

    @PostMapping("/callback")
    fun callback(
        @RequestBody request: OidcCallbackRequest,
        response: HttpServletResponse
    ): OidcLinkResponse {
        requireOidcEnabled()
        val tokens = oidcService.exchangeCodeForTokens(request.code)
        val claims = oidcService.extractClaimsFromIdToken(tokens.idToken)
        val user = oidcService.syncUser(claims)
        return buildResponse(user, response)
    }

    @PostMapping("/link/callback")
    fun linkCallback(
        @RequestBody request: OidcCallbackRequest,
        response: HttpServletResponse
    ): OidcLinkResponse {
        requireOidcEnabled()
        val userId = oidcStateToken.validateStateToken(request.state)
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired state")

        val tokens = oidcService.exchangeCodeForTokens(request.code)
        val claims = oidcService.extractClaimsFromIdToken(tokens.idToken)
        val email = oidcService.extractEmail(claims)
        val oidcSub = oidcService.extractOidcSub(claims)

        val existingLinked = userRepository.findByOidcSub(oidcSub)
        if (existingLinked != null && existingLinked.id != userId) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "OIDC account is linked to another user")
        }

        val currentUser = userRepository.findById(userId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "User not found") }

        if (!currentUser.email.equals(email, ignoreCase = true)) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "OIDC email does not match your account email")
        }

        currentUser.oidcSub = oidcSub
        currentUser.updatedAt = Instant.now()
        userRepository.save(currentUser)

        return buildResponse(currentUser, response)
    }

    @DeleteMapping("/unlink")
    fun unlinkOidc(): OidcStatusResponse {
        requireOidcEnabled()
        val userId = (SecurityContextHolder.getContext().authentication?.principal as? UUID)
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated")

        val user = userRepository.findById(userId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "User not found") }

        if (user.oidcSub == null) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "OIDC is not linked")
        }

        user.oidcSub = null
        user.updatedAt = Instant.now()
        userRepository.save(user)

        return OidcStatusResponse(linked = false, oidcSub = null)
    }

    @GetMapping("/status")
    fun getLinkStatus(): OidcStatusResponse {
        requireOidcEnabled()
        val userId = (SecurityContextHolder.getContext().authentication?.principal as? UUID)
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated")

        val user = userRepository.findById(userId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "User not found") }

        return OidcStatusResponse(linked = user.oidcSub != null, oidcSub = user.oidcSub)
    }

    private fun requireOidcEnabled() {
        if (!oidcProperties.isEffectivelyEnabled()) {
            throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "OIDC SSO is not configured")
        }
    }

    private fun buildResponse(user: Users, response: HttpServletResponse): OidcLinkResponse {
        val result = authTokenService.issue(user)
        authCookieService.writeAuthCookies(
            response,
            result.response.accessToken,
            result.response.refreshToken,
            result.csrfToken
        )
        return OidcLinkResponse(
            accessToken = result.response.accessToken,
            refreshToken = result.response.refreshToken,
            user = result.response.user
        )
    }
}
