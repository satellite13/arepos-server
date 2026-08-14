package ru.kavader.arepos.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class LibraryIconNameCollectorTest {
    private val collector = LibraryIconNameCollector(ObjectMapper())

    @Test
    fun `collects icon fields and composite source`() {
        val attrs = """
            {
              "icon": "folder",
              "paletteMaterialIcon": "hub",
              "diagramStyle": {
                "iconName": "acme-app",
                "compositeContent": {
                  "type": "icon",
                  "source": "/icons/bound.svg",
                  "bindsNotationIcon": true
                }
              },
              "customProperties": [{ "interactiveIcon": "link" }]
            }
        """.trimIndent()
        assertEquals(
            setOf("folder", "hub", "acme-app", "bound", "link"),
            collector.collectFromJson(attrs)
        )
    }
}
