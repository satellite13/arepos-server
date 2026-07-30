package ru.kavader.arepos.security

import org.springframework.stereotype.Component
import java.security.SecureRandom
import java.util.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Component
class OidcStateToken {
    companion object {
        private const val ALGORITHM = "HmacSHA256"
        private const val STATE_TTL_SECONDS = 600
        private val RANDOM = SecureRandom()
    }

    private var secretKey: ByteArray = generateSecretKey()

    private fun generateSecretKey(): ByteArray {
        val key = ByteArray(64)
        RANDOM.nextBytes(key)
        return key
    }

    fun generateStateToken(userId: UUID): String {
        val timestamp = System.currentTimeMillis() / 1000
        val payload = "${userId}|${timestamp}"
        val signature = hmac(payload, secretKey)
        return "${payload}.${signature}"
    }

    fun validateStateToken(token: String): UUID? {
        return try {
            val parts = token.split(".")
            if (parts.size != 2) return null
            val payload = parts[0]
            val signature = parts[1]

            if (!hmacVerify(payload, signature, secretKey)) return null

            val fields = payload.split("|")
            if (fields.size != 2) return null

            val userId = UUID.fromString(fields[0])
            val timestamp = fields[1].toLong()
            val now = System.currentTimeMillis() / 1000

            if (now - timestamp > STATE_TTL_SECONDS) return null

            userId
        } catch (_: Exception) {
            null
        }
    }

    private fun hmac(data: String, key: ByteArray): String {
        val mac = Mac.getInstance(ALGORITHM)
        mac.init(SecretKeySpec(key, ALGORITHM))
        val hmacBytes = mac.doFinal(data.toByteArray(Charsets.UTF_8))
        return hmacBytes.fold("") { str, b -> str + "%02x".format(b) }
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
