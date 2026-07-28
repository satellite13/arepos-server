package ru.kavader.arepos.service.modelpackage

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import ru.kavader.arepos.model.NodeShapes
import ru.kavader.arepos.model.Role
import ru.kavader.arepos.model.Users
import ru.kavader.arepos.repository.NodeShapesRepository
import ru.kavader.arepos.repository.RepositoryTestBase
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@SpringBootTest
class NotationPackageAssemblerTest : RepositoryTestBase() {

    @Autowired
    lateinit var assembler: NotationPackageAssembler

    @Autowired
    lateinit var nodeShapesRepository: NodeShapesRepository

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Test
    fun `toImportRequest includes referenced node type and shape from customShapeId`() {
        val owner = usersRepository.save(
            Users(
                email = "notation-assembler@test.com",
                role = Role.USER,
                createdAt = Instant.now()
            )
        )
        val notation = persistNotation(owner = owner, name = "Assembler Notation", version = "1.2.0")
        val nodeType = persistNodeType(owner = owner, name = "Assembler Node Type")
        val shape = nodeShapesRepository.save(
            NodeShapes(
                name = "Assembler Hex",
                owner = owner,
                outline = """[{"type":"M","x":0,"y":0}]""",
                contentArea = """{"x":1,"y":1,"w":10,"h":10}""",
                attrs = """{"kind":"hex"}""",
                createdAt = Instant.now()
            )
        )
        val shapeId = requireNotNull(shape.id)
        persistComponent(
            notation = notation,
            nodeType = nodeType,
            owner = owner,
            name = "Assembler Component",
            attrs = """{"diagramStyle":{"customShapeId":"$shapeId","customOutline":[{"type":"M","x":0,"y":0}]}}"""
        )

        val request = assembler.toImportRequest(notation)

        assertEquals("Assembler Notation", request.notation.name)
        assertEquals("1.2.0", request.notation.version)
        assertEquals(1, request.nodeTypes.size)
        assertEquals(nodeType.id.toString(), request.nodeTypes.single().id)
        assertEquals("Assembler Node Type", request.nodeTypes.single().name)
        assertEquals(1, request.components.size)
        assertEquals(nodeType.id.toString(), request.components.single().nodeTypeId)
        assertEquals(1, request.shapes.size)
        val exportedShape = request.shapes.single()
        assertEquals(shapeId.toString(), exportedShape.id)
        assertEquals("Assembler Hex", exportedShape.name)
        assertEquals(objectMapper.readTree(shape.outline), objectMapper.readTree(exportedShape.outline))
        assertEquals(objectMapper.readTree(shape.contentArea), objectMapper.readTree(exportedShape.contentArea))
    }

    @Test
    fun `toClientExportDocument emits warchi-notation-export v2 wrapper`() {
        val owner = usersRepository.save(
            Users(
                email = "notation-export-doc@test.com",
                role = Role.USER,
                createdAt = Instant.now()
            )
        )
        val notation = persistNotation(owner = owner, name = "Export Doc Notation", version = "3.0.0")
        val nodeType = persistNodeType(owner = owner, name = "Export Doc Type")
        persistComponent(notation = notation, nodeType = nodeType, owner = owner, name = "Export Doc Component")

        val doc = assembler.toClientExportDocument(notation)

        assertEquals("warchi-notation-export", doc["format"])
        assertEquals(2, doc["version"])
        assertNotNull(doc["exportedAt"])
        @Suppress("UNCHECKED_CAST")
        val notationMeta = doc["notation"] as Map<String, Any?>
        assertEquals(notation.id.toString(), notationMeta["id"])
        assertEquals("Export Doc Notation", notationMeta["name"])
        assertEquals("3.0.0", notationMeta["version"])
        @Suppress("UNCHECKED_CAST")
        val state = doc["state"] as Map<String, Any?>
        assertEquals(notation.id.toString(), state["notationId"])
        assertEquals(owner.id.toString(), state["ownerId"])
        @Suppress("UNCHECKED_CAST")
        val components = state["components"] as List<*>
        assertEquals(1, components.size)
        assertTrue(doc.containsKey("shapes"))
    }

    @Test
    fun `toImportRequest and toClientExportDocument aggregate relationRules`() {
        val owner = usersRepository.save(
            Users(
                email = "notation-assembler-rules@test.com",
                role = Role.USER,
                createdAt = Instant.now()
            )
        )
        val notation = persistNotation(owner = owner, name = "Rules Notation", version = "1.0.0")
        val nodeType = persistNodeType(owner = owner, name = "Rules Node Type")
        val linkType = persistLinkType(owner = owner, name = "Rules Link Type")
        val from = persistComponent(
            notation = notation,
            nodeType = nodeType,
            owner = owner,
            name = "From Component"
        )
        val to = persistComponent(
            notation = notation,
            nodeType = nodeType,
            owner = owner,
            name = "To Component"
        )
        val relation = persistRelation(
            notation = notation,
            linkType = linkType,
            owner = owner,
            name = "Allowed Relation"
        )
        persistRelationRule(relation = relation, fromComponent = from, toComponent = to)

        val fromId = requireNotNull(from.id).toString()
        val toId = requireNotNull(to.id).toString()
        val relationId = requireNotNull(relation.id).toString()

        val request = assembler.toImportRequest(notation)
        assertEquals(2, request.components.size)
        assertEquals(1, request.relations.size)
        assertEquals(1, request.linkTypes.size)
        assertEquals(1, request.relationRules.size)
        val importRule = request.relationRules.single()
        assertEquals(fromId, importRule.fromComponentId)
        assertEquals(toId, importRule.toComponentId)
        assertEquals(listOf(relationId), importRule.allowedRelationIds)

        val doc = assembler.toClientExportDocument(notation)
        @Suppress("UNCHECKED_CAST")
        val state = doc["state"] as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val stateRules = state["relationRules"] as List<Map<String, Any?>>
        assertEquals(1, stateRules.size)
        val exportRule = stateRules.single()
        assertEquals(fromId, exportRule["fromComponentId"])
        assertEquals(toId, exportRule["toComponentId"])
        assertEquals(listOf(relationId), exportRule["allowedRelationIds"])
    }
}
