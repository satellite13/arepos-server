package ru.kavader.arepos.util

import java.math.BigInteger

/**
 * Shared utilities for parsing and comparing semantic versions.
 */
object VersionUtils {
    private val storageSemverPattern = Regex(
        """^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(?:-([0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*))?$"""
    )

    private val fullSemverPattern = Regex(
        """^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(?:-([0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*))?(?:\+[0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*)?$"""
    )

    private data class FullSemver(
        val major: BigInteger,
        val minor: BigInteger,
        val patch: BigInteger,
        val prerelease: List<String>?
    )

    /** Parses a semver-like string "MAJOR.MINOR.PATCH" into a triple, or null on failure. */
    fun parseSemver(version: String): Triple<Int, Int, Int>? {
        val parts = version.trim().split(".")
        if (parts.size != 3) return null
        val major = parts[0].toIntOrNull() ?: return null
        val minor = parts[1].toIntOrNull() ?: return null
        val patch = parts[2].toIntOrNull() ?: return null
        return Triple(major, minor, patch)
    }

    /** Validates the SemVer format accepted by the persisted version_type domain. */
    fun isValidStorageSemver(version: String): Boolean {
        val match = storageSemverPattern.matchEntire(version) ?: return false
        return hasNoLeadingZeroNumericPrereleaseIdentifier(match.groups[4]?.value?.split("."))
    }

    /**
     * Compares two full SemVer values in ascending precedence order.
     * Returns null when either value is not valid SemVer.
     */
    fun compareSemver(a: String, b: String): Int? {
        val aSemver = parseFullSemver(a) ?: return null
        val bSemver = parseFullSemver(b) ?: return null

        val coreComparison = compareValuesBy(aSemver, bSemver, FullSemver::major, FullSemver::minor, FullSemver::patch)
        if (coreComparison != 0) return coreComparison

        val aPrerelease = aSemver.prerelease
        val bPrerelease = bSemver.prerelease
        if (aPrerelease == null && bPrerelease == null) return 0
        if (aPrerelease == null) return 1
        if (bPrerelease == null) return -1

        aPrerelease.zip(bPrerelease).forEach { (aIdentifier, bIdentifier) ->
            val identifierComparison = comparePrereleaseIdentifier(aIdentifier, bIdentifier)
            if (identifierComparison != 0) return identifierComparison
        }
        return aPrerelease.size.compareTo(bPrerelease.size)
    }

    private fun parseFullSemver(version: String): FullSemver? {
        val match = fullSemverPattern.matchEntire(version.trim()) ?: return null
        val prerelease = match.groups[4]?.value?.split(".")
        if (!hasNoLeadingZeroNumericPrereleaseIdentifier(prerelease)) {
            return null
        }
        return FullSemver(
            major = match.groups[1]!!.value.toBigInteger(),
            minor = match.groups[2]!!.value.toBigInteger(),
            patch = match.groups[3]!!.value.toBigInteger(),
            prerelease = prerelease
        )
    }

    private fun hasNoLeadingZeroNumericPrereleaseIdentifier(prerelease: List<String>?): Boolean =
        prerelease?.none { identifier ->
            identifier.all(Char::isDigit) && identifier.length > 1 && identifier.startsWith('0')
        } != false

    private fun comparePrereleaseIdentifier(a: String, b: String): Int {
        if (a == b) return 0
        val aNumeric = a.all(Char::isDigit)
        val bNumeric = b.all(Char::isDigit)
        if (aNumeric && bNumeric) return a.toBigInteger().compareTo(b.toBigInteger())
        if (aNumeric) return -1
        if (bNumeric) return 1
        return a.compareTo(b)
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
