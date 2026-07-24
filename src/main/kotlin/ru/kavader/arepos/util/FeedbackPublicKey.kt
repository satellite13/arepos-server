package ru.kavader.arepos.util

object FeedbackPublicKey {
    private val KEY_REGEX = Regex("^FB-(\\d+)$", RegexOption.IGNORE_CASE)
    private val PLAIN_NUMBER_REGEX = Regex("^\\d+$")

    fun format(publicNumber: Int): String = "FB-$publicNumber"

    fun parseNumber(raw: String): Int? {
        val match = KEY_REGEX.matchEntire(raw.trim()) ?: return null
        return match.groupValues[1].toIntOrNull()
    }

    fun parsePlainNumber(raw: String): Int? {
        val trimmed = raw.trim()
        if (!PLAIN_NUMBER_REGEX.matches(trimmed)) return null
        return trimmed.toIntOrNull()
    }
}
