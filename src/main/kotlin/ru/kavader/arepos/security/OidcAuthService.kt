package ru.kavader.arepos.security

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.ObjectMapper
import com.nimbusds.jwt.JWTClaimsSet
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.UriComponentsBuilder
import ru.kavader.arepos.model.Role
import ru.kavader.arepos.model.Users
import ru.kavader.arepos.repository.UsersRepository
import ru.kavader.arepos.service.UserProfileAttrsService
import java.net.URI
import java.time.Instant
import java.util.UUID

@Service
class OidcAuthService(
    private val oidcProperties: OidcProperties,
    private val usersRepository: UsersRepository,
    private val jwtTokenProvider: JwtTokenProvider,
    private val userProfileAttrsService: UserProfileAttrsService,
    private val idTokenVerifier: OidcIdTokenVerifier,
    private val objectMapper: ObjectMapper
) {
    companion object {
        private val log = LoggerFactory.getLogger(OidcAuthService::class.java)
    }

    fun buildAuthorizationUrl(state: String, codeChallenge: String, nonce: String): String {
        val authUrl = "${normalizeIssuerBase()}protocol/openid-connect/auth"
        val params = listOf(
            "client_id" to oidcProperties.clientId,
            "response_type" to "code",
            "redirect_uri" to oidcProperties.redirectUri,
            "scope" to oidcProperties.scope,
            "state" to state,
            "code_challenge" to codeChallenge,
            "code_challenge_method" to "S256",
            "nonce" to nonce
        )
        val qs = params.joinToString("&") { (k, v) ->
            "$k=${java.net.URLEncoder.encode(v, Charsets.UTF_8).replace("+", "%20")}"
        }
        return "$authUrl?$qs"
    }

    fun exchangeCodeForTokens(code: String, codeVerifier: String): OidcTokens {
        val tokenUrl = "${normalizeIssuerBase()}protocol/openid-connect/token"
        val restTemplate = RestTemplate()
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_FORM_URLENCODED
        headers.accept = listOf(MediaType.APPLICATION_JSON)
        headers.setBasicAuth(oidcProperties.clientId, oidcProperties.clientSecret)

        val body = UriComponentsBuilder.newInstance()
            .queryParam("grant_type", "authorization_code")
            .queryParam("code", code)
            .queryParam("redirect_uri", oidcProperties.redirectUri)
            .queryParam("code_verifier", codeVerifier)
            .build()
            .toUriString()

        val response: ResponseEntity<String> = restTemplate.exchange(
            URI.create(tokenUrl),
            HttpMethod.POST,
            org.springframework.http.HttpEntity(body.substringAfter('?'), headers),
            String::class.java
        )

        if (!response.statusCode.is2xxSuccessful) {
            log.error("OIDC token exchange failed: {} {}", response.statusCode, response.body)
            throw OidcException("Token exchange failed: ${response.body}")
        }

        return parseTokenResponse(response.body ?: throw OidcException("Empty token response"))
    }

    fun extractClaimsFromIdToken(idToken: String): JWTClaimsSet =
        idTokenVerifier.verify(idToken)

    fun verifyNonce(claims: JWTClaimsSet, expectedNonce: String) {
        idTokenVerifier.verifyNonce(claims, expectedNonce)
    }

    fun extractEmail(claims: JWTClaimsSet): String {
        return claims.getStringClaim("email") ?: throw OidcException("No email in ID token claims")
    }

    fun extractOidcSub(claims: JWTClaimsSet): String {
        return claims.subject ?: throw OidcException("No sub claim in ID token")
    }

    fun isEmailVerified(claims: JWTClaimsSet): Boolean =
        claims.getBooleanClaim("email_verified") == true

    @Transactional
    fun syncUser(claims: JWTClaimsSet): Users {
        val email = extractEmail(claims)
        val oidcSub = extractOidcSub(claims)
        val emailVerified = isEmailVerified(claims)

        var user = usersRepository.findByOidcSub(oidcSub)

        if (user == null) {
            user = usersRepository.findByEmailIgnoreCase(email)

            if (user != null) {
                if (!emailVerified) {
                    log.warn(
                        "Refusing OIDC auto-link for unverified email: userId={} email={}",
                        user.id, user.email
                    )
                    throw OidcException(
                        "Email is not verified; sign in and link SSO from your profile"
                    )
                }
                log.info(
                    "Auto-linking OIDC to existing user: userId={} email={} oidcSub={}",
                    user.id, user.email, oidcSub
                )
                user.oidcSub = oidcSub
                if (user.email != email) {
                    log.info("Updating email: {} -> {}", user.email, email)
                    user.email = email
                }
                user.updatedAt = Instant.now()
                usersRepository.save(user)
                if (!user.isActive) {
                    log.warn("OIDC login for deactivated user: userId={} email={}", user.id, user.email)
                    throw OidcException("Account is deactivated")
                }
                return user
            }

            if (!emailVerified) {
                throw OidcException("Email is not verified; cannot create account via SSO")
            }

            log.info(
                "Creating new user from OIDC: email={} oidcSub={} requestId={}",
                email, oidcSub, MDC.get("requestId")
            )

            val now = Instant.now()
            val firstName = claims.getStringClaim("given_name") ?: claims.getStringClaim("firstName")
            val lastName = claims.getStringClaim("family_name") ?: claims.getStringClaim("lastName")
            val middleName = claims.getStringClaim("middleName") ?: claims.getStringClaim("middle_name")
            val position = claims.getStringClaim("position")

            val profileAttrs = userProfileAttrsService.buildProfileAttrs(
                firstName = firstName,
                lastName = lastName,
                middleName = middleName,
                position = position
            )

            user = usersRepository.save(
                Users(
                    email = email,
                    role = Role.USER,
                    attrs = profileAttrs,
                    oidcSub = oidcSub,
                    createdAt = now,
                    updatedAt = now
                )
            )
        }

        if (!user.isActive) {
            log.warn("OIDC login for deactivated user: userId={} email={}", user.id, user.email)
            throw OidcException("Account is deactivated")
        }

        return user
    }

    fun generateLocalJwt(user: Users): String {
        return jwtTokenProvider.generateAccessToken(user.id!!, user.role.name)
    }

    fun generateLocalRefreshToken(user: Users): String {
        return jwtTokenProvider.generateRefreshToken(user.id!!)
    }

    private fun normalizeIssuerBase(): String {
        val raw = oidcProperties.issuerUri.trim()
        return when {
            raw.isEmpty() -> throw OidcException("OIDC issuer is not configured")
            raw.endsWith("/") -> raw
            else -> "$raw/"
        }
    }

    private fun parseTokenResponse(body: String): OidcTokens {
        val parsed = try {
            objectMapper.readValue(body, OidcTokenResponseBody::class.java)
        } catch (e: Exception) {
            log.error("Failed to parse OIDC token response", e)
            throw OidcException("Invalid token response")
        }
        return OidcTokens(
            accessToken = parsed.accessToken ?: throw OidcException("No access_token in response"),
            idToken = parsed.idToken ?: throw OidcException("No id_token in response"),
            tokenType = parsed.tokenType ?: "Bearer"
        )
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
private data class OidcTokenResponseBody(
    @com.fasterxml.jackson.annotation.JsonProperty("access_token")
    val accessToken: String? = null,
    @com.fasterxml.jackson.annotation.JsonProperty("id_token")
    val idToken: String? = null,
    @com.fasterxml.jackson.annotation.JsonProperty("token_type")
    val tokenType: String? = null
)

data class OidcTokens(
    val accessToken: String,
    val idToken: String,
    val tokenType: String
)

class OidcException(message: String) : RuntimeException(message)
