package ru.kavader.arepos.service.diagramcopy

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DiagramCopyNotationRemapperTest {

    private val objectMapper = ObjectMapper()
    private val remapper = DiagramCopyNotationRemapper(objectMapper)

    @Test
    fun `remaps instance notationComponentId by component name`() {
        val sourceComponentId = UUID.randomUUID()
        val targetComponentId = UUID.randomUUID()
        val attrs = """
            {"instances":{"nodes":[{"attrs":{"notationComponentId":"$sourceComponentId"}}]}}
        """.trimIndent()

        val result = remapper.remapDiagramAttrs(
            attrs = attrs,
            componentIdMap = mapOf(sourceComponentId to targetComponentId),
            relationIdMap = emptyMap()
        )

        val nodeAttrs = objectMapper.readTree(result.attrs)["instances"]["nodes"][0]["attrs"]
        assertEquals(targetComponentId.toString(), nodeAttrs["notationComponentId"].asText())
        assertTrue(result.warnings.isEmpty())
    }

    @Test
    fun `unmapped component becomes warning and clears binding`() {
        val attrs = """
            {"instances":{"nodes":[{"attrs":{"notationComponentId":"${UUID.randomUUID()}"}}]}}
        """.trimIndent()

        val result = remapper.remapDiagramAttrs(attrs, emptyMap(), emptyMap())

        val nodeAttrs = objectMapper.readTree(result.attrs)["instances"]["nodes"][0]["attrs"]
        assertNull(nodeAttrs["notationComponentId"])
        assertEquals("NOTATION_COMPONENT_NOT_MAPPED", result.warnings.single().code)
    }

    @Test
    fun `strips documentFileId and reports warning`() {
        val documentFileId = UUID.randomUUID()

        val result = remapper.remapDiagramAttrs(
            attrs = """{"documentFileId":"$documentFileId","name":"Diagram"}""",
            componentIdMap = emptyMap(),
            relationIdMap = emptyMap()
        )

        val remappedAttrs = objectMapper.readTree(result.attrs)
        assertFalse(remappedAttrs.has("documentFileId"))
        assertEquals("DOCUMENT_NOT_COPIED", result.warnings.single().code)
    }

    @Test
    fun `remaps notationComponents keys on node attrs from sourceNotationId to targetNotationId`() {
        val sourceNotationId = UUID.randomUUID()
        val targetNotationId = UUID.randomUUID()
        val sourceComponentId = UUID.randomUUID()
        val targetComponentId = UUID.randomUUID()
        val attrs = """
            {
              "notationComponents":{"$sourceNotationId":{"componentId":"$sourceComponentId"}},
              "componentProperties":{"$sourceNotationId":{"$sourceComponentId":{"color":"blue"}}}
            }
        """.trimIndent()

        val result = remapper.remapNodeAttrs(
            attrs,
            sourceNotationId,
            targetNotationId,
            mapOf(sourceComponentId to targetComponentId)
        )

        val remappedAttrs = objectMapper.readTree(result.attrs)
        assertNull(remappedAttrs["notationComponents"][sourceNotationId.toString()])
        assertEquals(
            targetComponentId.toString(),
            remappedAttrs["notationComponents"][targetNotationId.toString()]["componentId"].asText()
        )
        assertEquals(
            "blue",
            remappedAttrs["componentProperties"][targetNotationId.toString()][targetComponentId.toString()]["color"].asText()
        )
    }

    @Test
    fun `remaps notation relation on diagram edge and link attrs`() {
        val sourceNotationId = UUID.randomUUID()
        val targetNotationId = UUID.randomUUID()
        val sourceRelationId = UUID.randomUUID()
        val targetRelationId = UUID.randomUUID()
        val diagramAttrs = """
            {"instances":{"edges":[{"attrs":{"notationRelationId":"$sourceRelationId"}}]}}
        """.trimIndent()
        val linkAttrs = """
            {
              "notationRelations":{"$sourceNotationId":{"relationId":"$sourceRelationId"}},
              "relationProperties":{"$sourceNotationId":{"$sourceRelationId":{"line":"dashed"}}}
            }
        """.trimIndent()

        val remappedDiagram = remapper.remapDiagramAttrs(
            diagramAttrs,
            emptyMap(),
            mapOf(sourceRelationId to targetRelationId)
        )
        val remappedLink = remapper.remapLinkAttrs(
            linkAttrs,
            sourceNotationId,
            targetNotationId,
            mapOf(sourceRelationId to targetRelationId)
        )

        assertEquals(
            targetRelationId.toString(),
            objectMapper.readTree(remappedDiagram.attrs)["instances"]["edges"][0]["attrs"]["notationRelationId"].asText()
        )
        val remappedLinkAttrs = objectMapper.readTree(remappedLink.attrs)
        assertEquals(
            targetRelationId.toString(),
            remappedLinkAttrs["notationRelations"][targetNotationId.toString()]["relationId"].asText()
        )
        assertEquals(
            "dashed",
            remappedLinkAttrs["relationProperties"][targetNotationId.toString()][targetRelationId.toString()]["line"].asText()
        )
    }
}
