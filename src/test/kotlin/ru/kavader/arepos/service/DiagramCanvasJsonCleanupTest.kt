package ru.kavader.arepos.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import java.util.*
import kotlin.test.assertEquals

class DiagramCanvasJsonCleanupTest {

    private val om = ObjectMapper()

    @Test
    fun `removes node instances and edges touching them`() {
        val na = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
        val json = """
            {
              "instances": {
                "nodes": [
                  { "id": "i1", "modelNodeId": "$na", "x": 0, "y": 0 },
                  { "id": "i2", "modelNodeId": "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb", "x": 1, "y": 1 }
                ],
                "edges": [
                  { "id": "e1", "modelLinkId": "cccccccc-cccc-cccc-cccc-cccccccccccc",
                    "sourceInstanceId": "i1", "targetInstanceId": "i2" }
                ]
              }
            }
        """.trimIndent()

        val out = DiagramCanvasJsonCleanup.cleanupDiagramAttrs(json, om, setOf(na), emptySet())!!
        val tree = om.readTree(out)
        assertEquals(1, tree.path("instances").path("nodes").size())
        assertEquals("i2", tree.path("instances").path("nodes")[0].path("id").asText())
        assertEquals(0, tree.path("instances").path("edges").size())
    }

    @Test
    fun `removes edges by deleted model link id`() {
        val linkId = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc")
        val json = """
            {
              "instances": {
                "nodes": [
                  { "id": "i1", "modelNodeId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", "x": 0, "y": 0 },
                  { "id": "i2", "modelNodeId": "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb", "x": 1, "y": 1 }
                ],
                "edges": [
                  { "id": "e1", "modelLinkId": "$linkId",
                    "sourceInstanceId": "i1", "targetInstanceId": "i2" }
                ]
              }
            }
        """.trimIndent()

        val out = DiagramCanvasJsonCleanup.cleanupDiagramAttrs(json, om, emptySet(), setOf(linkId))!!
        assertEquals(0, om.readTree(out).path("instances").path("edges").size())
    }

    @Test
    fun `keeps diagram-only and note stub edges when link id matches`() {
        val linkId = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc")
        val noteId = "${DiagramCanvasJsonCleanup.DIAGRAM_NOTE_EDGE_MODEL_LINK_PREFIX}n1"
        val json = """
            {
              "instances": {
                "nodes": [
                  { "id": "i1", "modelNodeId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", "x": 0, "y": 0 },
                  { "id": "i2", "modelNodeId": "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb", "x": 1, "y": 1 }
                ],
                "edges": [
                  { "id": "e1", "modelLinkId": "$linkId",
                    "sourceInstanceId": "i1", "targetInstanceId": "i2",
                    "attrs": { "isDiagramOnly": true } },
                  { "id": "e2", "modelLinkId": "$noteId",
                    "sourceInstanceId": "i1", "targetInstanceId": "i2" }
                ]
              }
            }
        """.trimIndent()

        val out = DiagramCanvasJsonCleanup.cleanupDiagramAttrs(json, om, emptySet(), setOf(linkId))!!
        val edges = om.readTree(out).path("instances").path("edges")
        assertEquals(2, edges.size())
    }

    @Test
    fun `legacy root nodes and edges`() {
        val na = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
        val json = """
            {
              "nodes": [
                { "id": "i1", "modelNodeId": "$na", "x": 0, "y": 0 }
              ],
              "edges": [
                { "id": "e1", "modelLinkId": "cccccccc-cccc-cccc-cccc-cccccccccccc",
                  "sourceInstanceId": "i1", "targetInstanceId": "i1" }
              ]
            }
        """.trimIndent()

        val out = DiagramCanvasJsonCleanup.cleanupDiagramAttrs(json, om, setOf(na), emptySet())!!
        val root = om.readTree(out)
        assertEquals(0, root.path("nodes").size())
        assertEquals(0, root.path("edges").size())
    }

    @Test
    fun `unchanged when no ids to delete`() {
        val json = """{"instances":{"nodes":[],"edges":[]}}"""
        val out = DiagramCanvasJsonCleanup.cleanupDiagramAttrs(json, om, emptySet(), emptySet())
        assertEquals(json, out)
    }

    @Test
    fun `invalid json left as-is`() {
        val bad = "{not json"
        val out = DiagramCanvasJsonCleanup.cleanupDiagramAttrs(bad, om, setOf(UUID.randomUUID()), emptySet())
        assertEquals(bad, out)
    }
}
