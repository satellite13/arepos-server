package ru.kavader.arepos.util

import java.util.UUID

/**
 * Упоминания пользователей в markdown-теле комментария:
 * `@[Имя](user:{uuid})` — по внутреннему id,
 * `@[Имя](oidc:{sub})` — по OIDC-идентификатору (users.oidc_sub).
 */
object CommentMentionParser {
    private const val MENTION_SCHEMES = "user|oidc"

    private val MENTION_REGEX = Regex("""@\[[^\]]*]\(($MENTION_SCHEMES):([^)]+)\)""")
    private val UUID_REGEX = Regex("""^[0-9a-fA-F-]{36}$""")

    data class MentionToken(val kind: String, val value: String)

    fun extractMentionTokens(bodyMd: String): List<MentionToken> =
        MENTION_REGEX.findAll(bodyMd)
            .map { MentionToken(it.groupValues[1], it.groupValues[2].trim()) }
            .distinct()
            .toList()

    fun extractMentionIds(bodyMd: String): List<UUID> =
        extractMentionTokens(bodyMd).mapNotNull { token ->
            if (token.kind == "user" && UUID_REGEX.matches(token.value)) {
                runCatching { UUID.fromString(token.value) }.getOrNull()
            } else {
                null
            }
        }.distinct()

    /** Первый внешний http(s)-URL в тексте (для превью-карточки). */
    private val URL_REGEX = Regex("""https?://[^\s<>()\[\]{}"']+""")

    fun extractFirstUrl(bodyMd: String): String? {
        val match = URL_REGEX.find(bodyMd) ?: return null
        return match.value.trimEnd('.', ',', ')', ']', '!', '?', ':', ';')
    }
}
