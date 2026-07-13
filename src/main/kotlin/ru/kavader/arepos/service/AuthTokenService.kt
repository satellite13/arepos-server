package ru.kavader.arepos.service

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.dto.auth.AuthResponse
import ru.kavader.arepos.mapper.UserMapper
import ru.kavader.arepos.model.RefreshTokens
import ru.kavader.arepos.model.Users
import ru.kavader.arepos.repository.RefreshTokensRepository
import ru.kavader.arepos.repository.UsersRepository
import ru.kavader.arepos.security.AuthCookieService
import ru.kavader.arepos.security.JwtTokenProvider
import ru.kavader.arepos.security.TokenType
import java.security.MessageDigest
import java.time.Instant

data class AuthTokenResult(
    val response: AuthResponse,
    val csrfToken: String
)

@Service
class AuthTokenService(
    private val refreshTokensRepository: RefreshTokensRepository,
    private val usersRepository: UsersRepository,
    private val jwtTokenProvider: JwtTokenProvider,
    private val authCookieService: AuthCookieService,
    private val userMapper: UserMapper
) {
    @Transactional
    fun issue(user: Users): AuthTokenResult {
        val userId = requireNotNull(user.id)
        val accessToken = jwtTokenProvider.generateAccessToken(userId, user.role.name)
        val refreshToken = jwtTokenProvider.generateRefreshToken(userId)
        val csrfToken = authCookieService.generateCsrfToken()
        persistRefreshToken(user, refreshToken)
        return AuthTokenResult(
            response = AuthResponse(
                accessToken = accessToken,
                refreshToken = refreshToken,
                user = userMapper.toUserInfoResponse(user)
            ),
            csrfToken = csrfToken
        )
    }

    @Transactional
    fun refresh(refreshToken: String): AuthTokenResult {
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token")
        }
        if (jwtTokenProvider.getTokenType(refreshToken) != TokenType.REFRESH) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token type")
        }

        val tokenHash = hashToken(refreshToken)
        val persistedToken = refreshTokensRepository.findByTokenHash(tokenHash)
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token")
        val userId = jwtTokenProvider.getUserId(refreshToken)
        if (persistedToken.user.id != userId) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token")
        }
        val marked = refreshTokensRepository.markUsed(tokenHash, userId, Instant.now())
        if (marked == 0) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token already used or expired")
        }

        val user = usersRepository.findById(userId)
            .orElseThrow { ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found") }
        if (!user.isActive) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Account is deactivated")
        }
        return issueTokenPair(user)
    }

    @Transactional
    fun logout(refreshToken: String?) {
        if (refreshToken != null && jwtTokenProvider.validateToken(refreshToken)) {
            if (jwtTokenProvider.getTokenType(refreshToken) == TokenType.REFRESH) {
                refreshTokensRepository.markUsed(
                    hashToken(refreshToken),
                    jwtTokenProvider.getUserId(refreshToken),
                    Instant.now()
                )
            }
        }
    }

    private fun issueTokenPair(user: Users): AuthTokenResult {
        val userId = requireNotNull(user.id)
        val accessToken = jwtTokenProvider.generateAccessToken(userId, user.role.name)
        val refreshToken = jwtTokenProvider.generateRefreshToken(userId)
        val csrfToken = authCookieService.generateCsrfToken()
        persistRefreshToken(user, refreshToken)
        return AuthTokenResult(
            AuthResponse(accessToken, refreshToken, userMapper.toUserInfoResponse(user)),
            csrfToken
        )
    }

    private fun persistRefreshToken(user: Users, refreshToken: String) {
        refreshTokensRepository.save(
            RefreshTokens(
                user = user,
                tokenHash = hashToken(refreshToken),
                expiresAt = jwtTokenProvider.getExpirationInstant(refreshToken),
                createdAt = Instant.now()
            )
        )
    }

    private fun hashToken(token: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(token.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}
