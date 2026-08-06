package ru.kavader.arepos.service.diagramcopy

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import ru.kavader.arepos.model.Components
import ru.kavader.arepos.model.LinkTypes
import ru.kavader.arepos.model.NodeTypes
import ru.kavader.arepos.model.Notations
import ru.kavader.arepos.model.Relations
import ru.kavader.arepos.model.Users
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
        val sourceNotationId = UUID.randomUUID()
        val targetNotationId = UUID.randomUUID()
        val attrs = """
            {"instances":{"nodes":[{"attrs":{"notationComponentId":"$sourceComponentId"}}]}}
        """.trimIndent()

        val result = remapper.remapDiagramAttrs(
            attrs = attrs,
            sourceNotationId = sourceNotationId,
            targetNotationId = targetNotationId,
            componentIdMap = mapOf(sourceComponentId to targetComponentId),
            relationIdMap = emptyMap()
        )

        val nodeAttrs = objectMapper.readTree(result.attrs)["instances"]["nodes"][0]["attrs"]
        assertEquals(targetComponentId.toString(), nodeAttrs["notationComponentId"].asText())
        assertTrue(result.warnings.isEmpty())
    }

    @Test
    fun `unmapped component becomes warning and clears binding`() {
        val sourceNotationId = UUID.randomUUID()
        val targetNotationId = UUID.randomUUID()
        val attrs = """
            {"instances":{"nodes":[{"attrs":{"notationComponentId":"${UUID.randomUUID()}"}}]}}
        """.trimIndent()

        val result = remapper.remapDiagramAttrs(
            attrs,
            sourceNotationId,
            targetNotationId,
            emptyMap(),
            emptyMap()
        )

        val nodeAttrs = objectMapper.readTree(result.attrs)["instances"]["nodes"][0]["attrs"]
        assertNull(nodeAttrs["notationComponentId"])
        assertEquals("NOTATION_COMPONENT_NOT_MAPPED", result.warnings.single().code)
    }

    @Test
    fun `strips documentFileId and reports warning`() {
        val documentFileId = UUID.randomUUID()

        val result = remapper.remapDiagramAttrs(
            attrs = """{"documentFileId":"$documentFileId","name":"Diagram"}""",
            sourceNotationId = UUID.randomUUID(),
            targetNotationId = UUID.randomUUID(),
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
            attrs = diagramAttrs,
            sourceNotationId = sourceNotationId,
            targetNotationId = targetNotationId,
            componentIdMap = emptyMap(),
            relationIdMap = mapOf(sourceRelationId to targetRelationId)
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

    @Test
    fun `remaps componentProperties on diagram node instance`() {
        val sourceNotationId = UUID.randomUUID()
        val targetNotationId = UUID.randomUUID()
        val sourceComponentId = UUID.randomUUID()
        val targetComponentId = UUID.randomUUID()
        val attrs = """
            {
              "instances":{"nodes":[{"attrs":{
                "componentProperties":{"$sourceNotationId":{"$sourceComponentId":{"color":"blue"}}}
              }}]}
            }
        """.trimIndent()

        val result = remapper.remapDiagramAttrs(
            attrs = attrs,
            sourceNotationId = sourceNotationId,
            targetNotationId = targetNotationId,
            componentIdMap = mapOf(sourceComponentId to targetComponentId),
            relationIdMap = emptyMap()
        )

        val properties = objectMapper.readTree(result.attrs)["instances"]["nodes"][0]["attrs"]["componentProperties"]
        assertNull(properties[sourceNotationId.toString()])
        assertEquals(
            "blue",
            properties[targetNotationId.toString()][targetComponentId.toString()]["color"].asText()
        )
    }

    @Test
    fun `buildComponentIdMap matches unique names disambiguates types and reports unmapped`() {
        val owner = Users(email = "owner@example.com")
        val notation = Notations(owner = owner, name = "Notation", version = "1.0.0")
        val typeA = NodeTypes(id = UUID.randomUUID(), name = "Type A", owner = owner)
        val typeB = NodeTypes(id = UUID.randomUUID(), name = "Type B", owner = owner)
        val uniqueSource = component("Unique", typeA, notation, owner)
        val duplicateSource = component("Duplicate", typeB, notation, owner)
        val missingSource = component("Missing", typeA, notation, owner)
        val uniqueTarget = component("Unique", typeB, notation, owner)
        val duplicateTargetA = component("Duplicate", typeA, notation, owner)
        val duplicateTargetB = component("Duplicate", typeB, notation, owner)

        val (map, unmapped) = remapper.buildComponentIdMap(
            listOf(uniqueSource, duplicateSource, missingSource),
            listOf(uniqueTarget, duplicateTargetA, duplicateTargetB)
        )

        assertEquals(uniqueTarget.id, map[uniqueSource.id])
        assertEquals(duplicateTargetB.id, map[duplicateSource.id])
        assertEquals(listOf("Missing"), unmapped)
    }

    @Test
    fun `buildRelationIdMap matches unique names disambiguates types and reports unmapped`() {
        val owner = Users(email = "owner@example.com")
        val notation = Notations(owner = owner, name = "Notation", version = "1.0.0")
        val typeA = LinkTypes(id = UUID.randomUUID(), name = "Type A", owner = owner)
        val typeB = LinkTypes(id = UUID.randomUUID(), name = "Type B", owner = owner)
        val uniqueSource = relation("Unique", typeA, notation, owner)
        val duplicateSource = relation("Duplicate", typeB, notation, owner)
        val missingSource = relation("Missing", typeA, notation, owner)
        val uniqueTarget = relation("Unique", typeB, notation, owner)
        val duplicateTargetA = relation("Duplicate", typeA, notation, owner)
        val duplicateTargetB = relation("Duplicate", typeB, notation, owner)

        val (map, unmapped) = remapper.buildRelationIdMap(
            listOf(uniqueSource, duplicateSource, missingSource),
            listOf(uniqueTarget, duplicateTargetA, duplicateTargetB)
        )

        assertEquals(uniqueTarget.id, map[uniqueSource.id])
        assertEquals(duplicateTargetB.id, map[duplicateSource.id])
        assertEquals(listOf("Missing"), unmapped)
    }

    private fun component(name: String, nodeType: NodeTypes, notation: Notations, owner: Users): Components = Components(
        id = UUID.randomUUID(),
        name = name,
        version = "1.0.0",
        notation = notation,
        owner = owner,
        nodeType = nodeType
    )

    private fun relation(name: String, linkType: LinkTypes, notation: Notations, owner: Users): Relations = Relations(
        id = UUID.randomUUID(),
        version = "1.0.0",
        owner = owner,
        notation = notation,
        name = name,
        linkType = linkType
    )
}
