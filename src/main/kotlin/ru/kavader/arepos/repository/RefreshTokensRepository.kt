package ru.kavader.arepos.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import ru.kavader.arepos.model.RefreshTokens
import java.time.Instant
import java.util.UUID

@Repository
interface RefreshTokensRepository : JpaRepository<RefreshTokens, UUID> {
    fun findByTokenHash(tokenHash: String): RefreshTokens?

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        UPDATE RefreshTokens rt
        SET rt.usedAt = :now,
            rt.revokedAt = :now
        WHERE rt.tokenHash = :tokenHash
          AND rt.user.id = :userId
          AND rt.usedAt IS NULL
          AND rt.revokedAt IS NULL
          AND rt.expiresAt > :now
        """
    )
    fun markUsed(tokenHash: String, userId: UUID, now: Instant): Int
}
