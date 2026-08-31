package ru.kavader.arepos.security

import com.nimbusds.jose.jwk.source.JWKSourceBuilder
import com.nimbusds.jose.proc.JWSAlgorithmFamilyJWSKeySelector
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier
import com.nimbusds.jwt.proc.DefaultJWTProcessor
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.net.URI
import java.util.Collections

/**
 * Verifies OIDC ID tokens against the IdP JWKS (signature + iss/aud/exp).
 */
@Component
class OidcIdTokenVerifier(
    private val oidcProperties: OidcProperties
) {
    companion object {
        private val log = LoggerFactory.getLogger(OidcIdTokenVerifier::class.java)
    }

    @Volatile
    private var processor: DefaultJWTProcessor<SecurityContext>? = null

    fun verify(idToken: String): JWTClaimsSet {
        try {
            return jwtProcessor().process(idToken, null)
        } catch (e: OidcException) {
            throw e
        } catch (e: Exception) {
            log.error("Failed to verify ID token", e)
            throw OidcException("Invalid ID token: ${e.message}")
        }
    }

    fun verifyNonce(claims: JWTClaimsSet, expectedNonce: String) {
        val actual = claims.getStringClaim("nonce")
        if (actual == null || actual != expectedNonce) {
            log.error("ID token nonce mismatch")
            throw OidcException("Invalid nonce")
        }
    }

    private fun jwtProcessor(): DefaultJWTProcessor<SecurityContext> {
        processor?.let { return it }
        synchronized(this) {
            processor?.let { return it }
            val issuerBase = oidcProperties.issuerUri.trim().let { raw ->
                when {
                    raw.isEmpty() -> throw OidcException("OIDC issuer is not configured")
                    raw.endsWith("/") -> raw
                    else -> "$raw/"
                }
            }
            val issuer = issuerBase.removeSuffix("/")
            val jwksUrl = URI.create("${issuerBase}protocol/openid-connect/certs").toURL()
            val jwkSource = JWKSourceBuilder.create<SecurityContext>(jwksUrl)
                .retrying(true)
                .build()
            val keySelector = try {
                JWSAlgorithmFamilyJWSKeySelector.fromJWKSource(jwkSource)
            } catch (e: Exception) {
                throw OidcException("Failed to initialize JWKS key selector: ${e.message}")
            }
            val created = DefaultJWTProcessor<SecurityContext>()
            created.jwsKeySelector = keySelector
            created.jwtClaimsSetVerifier = DefaultJWTClaimsVerifier(
                JWTClaimsSet.Builder()
                    .issuer(issuer)
                    .audience(oidcProperties.clientId)
                    .build(),
                Collections.singleton("sub")
            )
            processor = created
            return created
        }
    }
}
