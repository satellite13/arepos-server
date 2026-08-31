package ru.kavader.arepos.security

import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

data class OidcIssuedState(
    val state: String,
    val codeChallenge: String,
    val codeChallengeMethod: String = "S256",
    val nonce: String
)

enum class OidcStatePurpose {
    LOGIN,
    LINK
}

data class OidcValidatedState(
    val purpose: OidcStatePurpose,
    val subjectId: UUID,
    val codeVerifier: String,
    val nonce: String
)

@Component
class OidcStateToken(
    oidcProperties: OidcProperties,
    jwtProperties: JwtProperties
) {
    companion object {
        private const val ALGORITHM = "HmacSHA256"
        private const val STATE_TTL_SECONDS = 600L
        private val RANDOM = SecureRandom()
    }

    private val secretKey: ByteArray = resolveSecret(oidcProperties, jwtProperties)

    private fun resolveSecret(oidcProperties: OidcProperties, jwtProperties: JwtProperties): ByteArray {
        val configured = oidcProperties.stateSecret.trim()
        if (configured.isNotEmpty()) {
            return configured.toByteArray(Charsets.UTF_8)
        }
        // Share HMAC across instances without requiring a separate OIDC secret.
        return jwtProperties.secret.toByteArray(Charsets.UTF_8)
    }

    fun generateLoginState(): OidcIssuedState = issue(OidcStatePurpose.LOGIN, UUID.randomUUID())

    fun generateLinkState(userId: UUID): OidcIssuedState = issue(OidcStatePurpose.LINK, userId)

    fun validateLoginState(token: String): OidcValidatedState? =
        validate(token)?.takeIf { it.purpose == OidcStatePurpose.LOGIN }

    fun validateLinkState(token: String): OidcValidatedState? =
        validate(token)?.takeIf { it.purpose == OidcStatePurpose.LINK }

    /** @deprecated Prefer generateLoginState / generateLinkState. Kept for simple HMAC round-trip tests. */
    fun generateStateToken(userId: UUID): String = generateLinkState(userId).state

    /** @deprecated Prefer validateLoginState / validateLinkState. */
    fun validateStateToken(token: String): UUID? = validate(token)?.subjectId

    private fun issue(purpose: OidcStatePurpose, subjectId: UUID): OidcIssuedState {
        val timestamp = System.currentTimeMillis() / 1000
        val codeVerifier = randomUrlToken(64)
        val nonce = randomUrlToken(32)
        val payload = listOf(
            purpose.name.lowercase(),
            subjectId.toString(),
            timestamp.toString(),
            codeVerifier,
            nonce
        ).joinToString("|")
        val signature = hmac(payload, secretKey)
        return OidcIssuedState(
            state = "$payload.$signature",
            codeChallenge = codeChallengeS256(codeVerifier),
            nonce = nonce
        )
    }

    private fun validate(token: String): OidcValidatedState? {
        return try {
            val parts = token.split(".", limit = 2)
            if (parts.size != 2) return null
            val payload = parts[0]
            val signature = parts[1]
            if (!hmacVerify(payload, signature, secretKey)) return null

            val fields = payload.split("|")
            if (fields.size != 5) return null

            val purpose = when (fields[0]) {
                "login" -> OidcStatePurpose.LOGIN
                "link" -> OidcStatePurpose.LINK
                else -> return null
            }
            val subjectId = UUID.fromString(fields[1])
            val timestamp = fields[2].toLong()
            val now = System.currentTimeMillis() / 1000
            if (now - timestamp > STATE_TTL_SECONDS) return null

            OidcValidatedState(
                purpose = purpose,
                subjectId = subjectId,
                codeVerifier = fields[3],
                nonce = fields[4]
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun codeChallengeS256(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }

    private fun randomUrlToken(byteLength: Int): String {
        val bytes = ByteArray(byteLength)
        RANDOM.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun hmac(data: String, key: ByteArray): String {
        val mac = Mac.getInstance(ALGORITHM)
        mac.init(SecretKeySpec(key, ALGORITHM))
        val hmacBytes = mac.doFinal(data.toByteArray(Charsets.UTF_8))
        return hmacBytes.joinToString("") { "%02x".format(it) }
    }

    private fun hmacVerify(data: String, expected: String, key: ByteArray): Boolean {
        val actual = hmac(data, key)
        return constantTimeEquals(actual, expected)
    }

    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var result = 0
        for (i in a.indices) {
            result = result or (a[i].code xor b[i].code)
        }
        return result == 0
    }
}
