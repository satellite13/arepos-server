package ru.kavader.arepos.service.modelpackage

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.web.server.ResponseStatusException
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NotationExportDocumentMapperTest {
    private val objectMapper: ObjectMapper = jacksonObjectMapper()
    private val mapper = NotationExportDocumentMapper(objectMapper)

    @Test
    fun `maps v2 export document to NotationImportRequest`() {
        val json = """
            {
              "format": "warchi-notation-export",
              "version": 2,
              "exportedAt": "2026-07-29T00:00:00Z",
              "notation": { "id": "n1", "name": "Arch", "version": "1.2.0" },
              "state": {
                "notationId": "n1",
                "ownerId": "o1",
                "nodeTypes": [
                  { "id": "nt1", "name": "App", "parsedAttrs": { "icon": "hub" } }
                ],
                "linkTypes": [
                  { "id": "lt1", "name": "Flow", "parsedAttrs": {} }
                ],
                "components": [
                  {
                    "id": "c1",
                    "name": "App Comp",
                    "nodeTypeId": "nt1",
                    "version": "1.2.0",
                    "parsedAttrs": { "diagramStyle": { "customShapeId": "s1" } }
                  }
                ],
                "relations": [
                  {
                    "id": "r1",
                    "name": "Flows",
                    "linkTypeId": "lt1",
                    "version": "1.2.0",
                    "parsedAttrs": { "label": "x" }
                  }
                ],
                "relationRules": [
                  {
                    "fromComponentId": "c1",
                    "toComponentId": "c1",
                    "allowedRelationIds": ["r1"]
                  }
                ],
                "diagramLayer": { "version": 1, "nodes": [], "edges": [] }
              },
              "shapes": [
                {
                  "id": "s1",
                  "name": "Hex",
                  "outline": "[]",
                  "contentArea": null,
                  "attrs": null
                }
              ]
            }
        """.trimIndent()

        val request = mapper.toImportRequest(objectMapper.readTree(json))

        assertEquals("Arch", request.notation.name)
        assertEquals("1.2.0", request.notation.version)
        assertTrue(request.notation.attrs!!.contains("diagramLayer"))
        assertEquals(1, request.nodeTypes.size)
        assertEquals("nt1", request.nodeTypes[0].id)
        assertEquals("App", request.nodeTypes[0].name)
        assertTrue(request.nodeTypes[0].attrs!!.contains("hub"))
        assertEquals(1, request.linkTypes.size)
        assertEquals(1, request.components.size)
        assertEquals("nt1", request.components[0].nodeTypeId)
        assertTrue(request.components[0].attrs!!.contains("customShapeId"))
        assertEquals(1, request.relations.size)
        assertEquals(1, request.relationRules.size)
        assertEquals(listOf("r1"), request.relationRules[0].allowedRelationIds)
        assertEquals(1, request.shapes.size)
        assertEquals("s1", request.shapes[0].id)
    }

    @Test
    fun `rejects unknown format`() {
        val json = """{"format":"other","version":2,"notation":{"name":"A","version":"1.0.0"},"state":{}}"""
        val ex = assertThrows<ResponseStatusException> {
            mapper.toImportRequest(objectMapper.readTree(json))
        }
        assertEquals(400, ex.statusCode.value())
    }

    @Test
    fun `rejects unsupported version`() {
        val json =
            """{"format":"warchi-notation-export","version":1,"notation":{"name":"A","version":"1.0.0"},"state":{}}"""
        val ex = assertThrows<ResponseStatusException> {
            mapper.toImportRequest(objectMapper.readTree(json))
        }
        assertEquals(400, ex.statusCode.value())
    }

    @Test
    fun `isExportDocument detects v2 wrapper`() {
        val v2 = objectMapper.readTree("""{"format":"warchi-notation-export","version":2}""")
        val flat = objectMapper.readTree("""{"notation":{"name":"A","version":"1.0.0"}}""")
        assertTrue(NotationExportDocumentMapper.isExportDocument(v2))
        assertTrue(!NotationExportDocumentMapper.isExportDocument(flat))
    }
}
