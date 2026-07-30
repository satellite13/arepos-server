package ru.kavader.arepos.security

import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
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
import java.util.*

@Service
class OidcAuthService(
    private val oidcProperties: OidcProperties,
    private val usersRepository: UsersRepository,
    private val jwtTokenProvider: JwtTokenProvider,
    private val userProfileAttrsService: UserProfileAttrsService
) {
    companion object {
        private val log = LoggerFactory.getLogger(OidcAuthService::class.java)
    }

    fun buildAuthorizationUrl(state: String): String {
        val authUrl = "${oidcProperties.issuerUri}protocol/openid-connect/auth"
        val params = listOf(
            "client_id" to oidcProperties.clientId,
            "response_type" to "code",
            "redirect_uri" to oidcProperties.redirectUri,
            "scope" to oidcProperties.scope,
            "state" to state
        )
        val qs = params.joinToString("&") { (k, v) ->
            "$k=${java.net.URLEncoder.encode(v, Charsets.UTF_8).replace("+", "%20")}"
        }
        return "$authUrl?$qs"
    }

    fun exchangeCodeForTokens(code: String): OidcTokens {
        val tokenUrl = "${oidcProperties.issuerUri}protocol/openid-connect/token"
        val restTemplate = RestTemplate()
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_FORM_URLENCODED
        headers.accept = listOf(MediaType.APPLICATION_JSON)
        headers.setBasicAuth(oidcProperties.clientId, oidcProperties.clientSecret)

        val body = UriComponentsBuilder.newInstance()
            .queryParam("grant_type", "authorization_code")
            .queryParam("code", code)
            .queryParam("redirect_uri", oidcProperties.redirectUri)
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

    fun extractClaimsFromIdToken(idToken: String): JWTClaimsSet {
        try {
            val signedJwt = SignedJWT.parse(idToken)
            val claims = signedJwt.jwtClaimsSet

            val issuer = oidcProperties.issuerUri.removeSuffix("/")
            if (claims.issuer != issuer) {
                log.error("ID token issuer mismatch: {} != {}", claims.issuer, issuer)
                throw OidcException("Invalid issuer")
            }

            if (oidcProperties.clientId !in claims.audience) {
                log.error("ID token audience mismatch")
                throw OidcException("Invalid audience")
            }

            if (claims.expirationTime != null && claims.expirationTime.before(Date())) {
                log.error("ID token expired")
                throw OidcException("Expired ID token")
            }

            return claims
        } catch (e: OidcException) {
            throw e
        } catch (e: Exception) {
            log.error("Failed to decode ID token", e)
            throw OidcException("Invalid ID token: ${e.message}")
        }
    }

    fun extractEmail(claims: JWTClaimsSet): String {
        return claims.getStringClaim("email") ?: throw OidcException("No email in ID token claims")
    }

    fun extractOidcSub(claims: JWTClaimsSet): String {
        return claims.subject ?: throw OidcException("No sub claim in ID token")
    }

    @Transactional
    fun syncUser(claims: JWTClaimsSet): Users {
        val email = extractEmail(claims)
        val oidcSub = extractOidcSub(claims)

        var user = usersRepository.findByOidcSub(oidcSub)

        if (user == null) {
            user = usersRepository.findByEmailIgnoreCase(email)

            if (user != null) {
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

    private fun parseTokenResponse(body: String): OidcTokens {
        fun getStringValue(json: String, key: String): String? {
            val searchKey = "\"$key\""
            val idx = json.indexOf(searchKey)
            if (idx == -1) return null
            val afterKey = json.substring(idx + searchKey.length)
            val colonIdx = afterKey.indexOf(':')
            if (colonIdx == -1) return null
            val afterColon = afterKey.substring(colonIdx + 1).trimStart()
            if (!afterColon.startsWith("\"")) return null
            val rest = afterColon.substring(1)
            val endQuote = rest.indexOf("\"")
            if (endQuote == -1) return null
            return rest.substring(0, endQuote)
        }

        return OidcTokens(
            accessToken = getStringValue(body, "access_token") ?: throw OidcException("No access_token in response"),
            idToken = getStringValue(body, "id_token") ?: throw OidcException("No id_token in response"),
            tokenType = getStringValue(body, "token_type") ?: "Bearer"
        )
    }
}

data class OidcTokens(
    val accessToken: String,
    val idToken: String,
    val tokenType: String
)

class OidcException(message: String) : RuntimeException(message)
