package ru.kavader.arepos.security.access

import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull

class AccessDecompositionStructureTest {
    @Test
    fun `access responsibilities are provided by dedicated components`() {
        val componentNames = listOf(
            "ShareResolver",
            "CerbosDecisionCache",
            "BatchEvaluator",
            "TopLevelAccess",
            "NotationDiagramAccess"
        )

        componentNames.forEach { componentName ->
            assertNotNull(Class.forName("$PACKAGE_NAME.$componentName"))
        }
    }

    private companion object {
        const val PACKAGE_NAME = "ru.kavader.arepos.security.access"
    }
}
