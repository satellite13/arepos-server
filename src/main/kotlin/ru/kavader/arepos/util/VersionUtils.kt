package ru.kavader.arepos.util

/**
 * Shared utilities for parsing and comparing semantic versions.
 */
object VersionUtils {

    /** Parses a semver-like string "MAJOR.MINOR.PATCH" into a triple, or null on failure. */
    fun parseSemver(version: String): Triple<Int, Int, Int>? {
        val parts = version.trim().split(".")
        if (parts.size != 3) return null
        val major = parts[0].toIntOrNull() ?: return null
        val minor = parts[1].toIntOrNull() ?: return null
        val patch = parts[2].toIntOrNull() ?: return null
        return Triple(major, minor, patch)
    }

    /**
     * Comparator that orders entities by semantic version in descending order,
     * falling back to the raw version string for tie-breaking.
     * Entities with unparseable versions are sorted last.
     */
    fun <T> semverDescComparator(versionExtractor: (T) -> String): Comparator<T> =
        compareBy<T> { versionExtractor(it).let { v -> parseSemver(v) == null } }
            .thenByDescending { versionExtractor(it).let { v -> parseSemver(v)?.first ?: 0 } }
            .thenByDescending { versionExtractor(it).let { v -> parseSemver(v)?.second ?: 0 } }
            .thenByDescending { versionExtractor(it).let { v -> parseSemver(v)?.third ?: 0 } }
            .thenByDescending { versionExtractor(it) }
}
