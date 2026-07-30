package ru.kavader.arepos.security

import com.nimbusds.jwt.JWTClaimsSet
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import ru.kavader.arepos.service.UserProfileAttrsService
import ru.kavader.arepos.model.Role
import ru.kavader.arepos.model.Users
import ru.kavader.arepos.repository.UsersRepository
import java.time.Instant
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class OidcAuthServiceTest {

    private val usersRepository = mock(UsersRepository::class.java)
    private val jwtTokenProvider = mock(JwtTokenProvider::class.java)
    private val userProfileAttrsService = mock(UserProfileAttrsService::class.java)

    private lateinit var service: OidcAuthService
    private lateinit var oidcProperties: OidcProperties

    private val testIssuer = "https://idp.example.com/realms/app"
    private val testClientId = "test-client-id"

    @BeforeEach
    fun setup() {
        oidcProperties = OidcProperties(
            issuerUri = testIssuer,
            clientId = testClientId,
            clientSecret = "secret",
            redirectUri = "http://localhost:5173/auth/oidc/callback",
            postLogoutUri = "http://localhost:5173/",
            frontendUrl = "http://localhost:5173/",
            scope = "openid email profile",
            stateSecret = ""
        )
        service = OidcAuthService(
            oidcProperties,
            usersRepository,
            jwtTokenProvider,
            userProfileAttrsService
        )
    }

    @Test
    fun `buildAuthorizationUrl encodes redirect_uri properly`() {
        val url = service.buildAuthorizationUrl("state-value")
        assertTrue(url.contains("redirect_uri=http%3A%2F%2Flocalhost%3A5173%2Fauth%2Foidc%2Fcallback"))
        assertTrue(url.contains("client_id=$testClientId"))
        assertTrue(url.contains("response_type=code"))
    }

    @Test
    fun `buildAuthorizationUrl uses %20 for spaces instead of +`() {
        val url = service.buildAuthorizationUrl("state")
        assertFalse(url.contains("scope=openid+email"))
        assertTrue(url.contains("scope=openid%20email%20profile"))
    }

    @Test
    fun `buildAuthorizationUrl encodes pipe in state as %7C`() {
        val stateWithPipe = "uuid-123|1700000000.sig"
        val url = service.buildAuthorizationUrl(stateWithPipe)
        assertTrue(url.contains("%7C"))
        assertFalse(url.contains("state=uuid-123|"))
    }

    @Test
    fun `extractEmail returns email from claims`() {
        val claims = buildClaims(
            subject = "sub123",
            email = "user@example.com"
        )
        assertEquals("user@example.com", service.extractEmail(claims))
    }

    @Test
    fun `extractEmail throws when email missing`() {
        val claims = buildClaims(subject = "sub123", email = null)
        var thrown: OidcException? = null
        try {
            service.extractEmail(claims)
        } catch (e: OidcException) {
            thrown = e
        }
        requireNotNull(thrown)
        assertTrue(thrown.message!!.contains("email", ignoreCase = true))
    }

    @Test
    fun `extractOidcSub returns subject from claims`() {
        val claims = buildClaims(subject = "keycloak-sub-123", email = null)
        assertEquals("keycloak-sub-123", service.extractOidcSub(claims))
    }

    @Test
    fun `extractOidcSub throws when subject missing`() {
        val claims = JWTClaimsSet.Builder()
            .issuer(testIssuer)
            .audience(testClientId)
            .expirationTime(Date.from(Instant.now().plusSeconds(3600)))
            .issueTime(Date.from(Instant.now()))
            .claim("email", "x@y.com")
            .build()

        var thrown: OidcException? = null
        try {
            service.extractOidcSub(claims)
        } catch (e: OidcException) {
            thrown = e
        }
        requireNotNull(thrown)
        assertTrue(thrown.message!!.contains("sub", ignoreCase = true))
    }

    @Test
    fun `generateLocalJwt delegates to jwtTokenProvider`() {
        val user = testUser(id = UUID.randomUUID(), email = "test@example.com", role = Role.USER)
        lenient()
            .`when`(jwtTokenProvider.generateAccessToken(user.id!!, user.role.name))
            .thenReturn("local-jwt-token")

        val token = service.generateLocalJwt(user)
        assertEquals("local-jwt-token", token)
        verify(jwtTokenProvider).generateAccessToken(user.id!!, user.role.name)
    }

    @Test
    fun `generateLocalRefreshToken delegates to jwtTokenProvider`() {
        val user = testUser(id = UUID.randomUUID(), email = "test@example.com", role = Role.USER)
        lenient().`when`(jwtTokenProvider.generateRefreshToken(user.id!!))
            .thenReturn("local-refresh-token")

        val refreshToken = service.generateLocalRefreshToken(user)
        assertEquals("local-refresh-token", refreshToken)
        verify(jwtTokenProvider).generateRefreshToken(user.id!!)
    }

    @Test
    fun `syncUser auto-links existing user by email`() {
        val existingUser = testUser(
            id = UUID.randomUUID(),
            email = "user@example.com",
            role = Role.USER
        )
        lenient().`when`(usersRepository.findByOidcSub("kc-sub-123")).thenReturn(null)
        lenient().`when`(usersRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(existingUser)
        lenient().`when`(usersRepository.save(existingUser)).thenReturn(existingUser)

        val claims = buildClaims(
            subject = "kc-sub-123",
            email = "user@example.com"
        )

        val synced = service.syncUser(claims)

        assertEquals("kc-sub-123", synced.oidcSub)
        assertEquals("user@example.com", synced.email)
    }

    @Test
    fun `syncUser auto-links existing user by email ignoring case`() {
        val existingUser = testUser(
            id = UUID.randomUUID(),
            email = "User@Example.COM",
            role = Role.USER
        )
        lenient().`when`(usersRepository.findByOidcSub("kc-sub-456")).thenReturn(null)
        lenient().`when`(usersRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(existingUser)
        lenient().`when`(usersRepository.save(existingUser)).thenReturn(existingUser)

        val claims = buildClaims(
            subject = "kc-sub-456",
            email = "user@example.com"
        )

        val synced = service.syncUser(claims)

        assertEquals("kc-sub-456", synced.oidcSub)
        assertEquals("user@example.com", synced.email)
    }

    @Test
    fun `syncUser returns user when already linked by oidc_sub`() {
        val linkedUser = testUser(
            id = UUID.randomUUID(),
            email = "user@example.com",
            oidcSub = "existing-kc-sub",
            role = Role.USER
        )
        lenient().`when`(usersRepository.findByOidcSub("existing-kc-sub")).thenReturn(linkedUser)

        val claims = buildClaims(
            subject = "existing-kc-sub",
            email = "user@example.com"
        )

        val synced = service.syncUser(claims)

        assertEquals("existing-kc-sub", synced.oidcSub)
        assertEquals("user@example.com", synced.email)
    }

    @Test
    fun `syncUser throws OidcException for deactivated user`() {
        val inactiveUser = testUser(
            id = UUID.randomUUID(),
            email = "inactive@example.com",
            role = Role.USER,
            isActive = false
        )
        lenient().`when`(usersRepository.findByOidcSub("kc")).thenReturn(null)
        lenient().`when`(usersRepository.findByEmailIgnoreCase("inactive@example.com")).thenReturn(inactiveUser)
        lenient().`when`(usersRepository.save(inactiveUser)).thenReturn(inactiveUser)

        val claims = buildClaims(
            subject = "kc",
            email = "inactive@example.com"
        )

        var thrown: OidcException? = null
        try {
            service.syncUser(claims)
        } catch (e: OidcException) {
            thrown = e
        }
        requireNotNull(thrown)
        assertTrue(thrown.message!!.contains("deactivated", ignoreCase = true))
    }

    private fun buildClaims(subject: String?, email: String?): JWTClaimsSet {
        val builder = JWTClaimsSet.Builder()
            .issuer(testIssuer)
            .audience(testClientId)
            .expirationTime(Date.from(Instant.now().plusSeconds(3600)))
            .issueTime(Date.from(Instant.now()))

        if (subject != null) builder.subject(subject)
        if (email != null) builder.claim("email", email)
        return builder.build()
    }

    private fun testUser(
        id: UUID,
        email: String,
        role: Role = Role.USER,
        oidcSub: String? = null,
        isActive: Boolean = true
    ): Users {
        return Users(
            id = id,
            email = email,
            role = role,
            oidcSub = oidcSub,
            isActive = isActive,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
    }
}
