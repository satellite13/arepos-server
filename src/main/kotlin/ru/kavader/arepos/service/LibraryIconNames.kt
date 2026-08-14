package ru.kavader.arepos.service

import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

object LibraryIconNames {
    private val SLUG = Regex("^[a-z0-9][a-z0-9_-]{0,254}$")
    private val FROM_FILE = Regex("[^a-z0-9_-]+")

    fun normalize(raw: String): String {
        val name = raw.trim().lowercase()
            .removeSuffix(".svg")
            .replace(FROM_FILE, "-")
            .trim('-', '_')
        if (name.isEmpty() || !SLUG.matches(name)) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Icon name must match [a-z0-9_-] (got '${raw.trim()}')"
            )
        }
        return name
    }

    fun fromFilename(filename: String): String {
        val base = filename.substringAfterLast('/').substringAfterLast('\\')
        return normalize(base)
    }
}
