package ru.kavader.arepos.util

import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VersionUtilsTest {
    @Test
    fun `storage SemVer rejects leading zero numeric identifiers`() {
        listOf("01.0.0", "1.01.0", "1.0.01", "1.0.0-alpha.01").forEach { version ->
            assertFalse(VersionUtils.isValidStorageSemver(version), version)
        }
    }

    @Test
    fun `storage SemVer accepts valid core and prerelease identifiers`() {
        listOf("0.0.0", "1.0.0-alpha.1", "1.0.0-alpha.beta").forEach { version ->
            assertTrue(VersionUtils.isValidStorageSemver(version), version)
        }
    }

    @Test
    fun `storage SemVer rejects build metadata`() {
        assertFalse(VersionUtils.isValidStorageSemver("1.0.0+build.1"))
    }
}
