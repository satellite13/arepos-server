package ru.kavader.arepos.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.dto.model.MergeLinksRequest
import ru.kavader.arepos.dto.model.MergeNodesRequest
import ru.kavader.arepos.model.Models
import ru.kavader.arepos.model.NodeTypes
import ru.kavader.arepos.model.Nodes
import ru.kavader.arepos.model.Users
import ru.kavader.arepos.repository.DiagramEditLocksRepository
import ru.kavader.arepos.repository.DiagramsRepository
import ru.kavader.arepos.repository.LinksRepository
import ru.kavader.arepos.repository.ModelsRepository
import ru.kavader.arepos.repository.NodesRepository
import ru.kavader.arepos.security.ResourceAccessService
import java.time.Instant
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class ModelValidationMergeServiceTest {

    private val modelsRepository = mock(ModelsRepository::class.java)
    private val accessService = mock(ResourceAccessService::class.java)
    private val nodesRepository = mock(NodesRepository::class.java)
    private val linksRepository = mock(LinksRepository::class.java)
    private val diagramsRepository = mock(DiagramsRepository::class.java)
    private val diagramEditLocksRepository = mock(DiagramEditLocksRepository::class.java)
    private val modelSyncBroadcaster = mock(ModelSyncBroadcaster::class.java)
    private val objectMapper = ObjectMapper()
    private val service = ModelValidationMergeService(
        modelsRepository = modelsRepository,
        accessService = accessService,
        nodesRepository = nodesRepository,
        linksRepository = linksRepository,
        diagramsRepository = diagramsRepository,
        diagramEditLocksRepository = diagramEditLocksRepository,
        modelSyncBroadcaster = modelSyncBroadcaster,
        objectMapper = objectMapper
    )

    private val owner = Users(id = UUID.randomUUID(), email = "merge@test.com")
    private val modelId = UUID.randomUUID()
    private val model = Models(
        id = modelId,
        name = "m",
        version = "1.0.0",
        owner = owner,
        createdAt = Instant.now()
    )
    private val nodeType = NodeTypes(
        id = UUID.randomUUID(),
        name = "ApplicationComponent",
        owner = owner,
        createdAt = Instant.now()
    )
    private val now = Instant.parse("2024-01-01T00:00:00Z")

    @Test
    fun `mergeNodes is a transactional write on the merge service`() {
        val method = ModelValidationMergeService::class.java.declaredMethods
            .singleOrNull { it.name == "mergeNodes" }
        assertNotNull(method, "mergeNodes must exist on ModelValidationMergeService")
        assertEquals(UUID::class.java, method.parameterTypes[0])
        assertEquals(MergeNodesRequest::class.java, method.parameterTypes[1])
    }

    @Test
    fun `mergeLinks is a transactional write on the merge service`() {
        val method = ModelValidationMergeService::class.java.declaredMethods
            .singleOrNull { it.name == "mergeLinks" }
        assertNotNull(method, "mergeLinks must exist on ModelValidationMergeService")
        assertEquals(UUID::class.java, method.parameterTypes[0])
        assertEquals(MergeLinksRequest::class.java, method.parameterTypes[1])
    }

    @Test
    fun `remapDropInstancesToKeep drops extras when keep already present`() {
        val keepId = UUID.randomUUID()
        val dropId = UUID.randomUUID()
        val attrs = """{"instances":{"nodes":[{"id":"i1","modelNodeId":"$dropId"},{"id":"i2","modelNodeId":"$keepId"}],"edges":[]}}"""

        val remapped = invokeRemapDropInstancesToKeep(attrs, dropId, keepId)!!
        val nodes = objectMapper.readTree(remapped).path("instances").path("nodes")
        assertEquals(1, nodes.size())
        assertEquals("i2", nodes[0].path("id").asText())
        assertEquals(keepId.toString(), nodes[0].path("modelNodeId").asText())
    }

    @Test
    fun `remapDropInstancesToKeep remaps when keep is absent`() {
        val keepId = UUID.randomUUID()
        val dropId = UUID.randomUUID()
        val attrs = """{"nodes":[{"id":"root","modelNodeId":"$dropId"}],"instances":{"nodes":[{"id":"i1","modelNodeId":"$dropId"}]}}"""

        val remapped = invokeRemapDropInstancesToKeep(attrs, dropId, keepId)!!
        val root = objectMapper.readTree(remapped)
        assertEquals(keepId.toString(), root.path("nodes")[0].path("modelNodeId").asText())
        assertEquals(keepId.toString(), root.path("instances").path("nodes")[0].path("modelNodeId").asText())
    }

    @Test
    fun `mergeNodes rejects drop that still has documents`() {
        val keep = node("CRM", attrs = """{"typeProperties":{"a":1}}""")
        val drop = node(
            "CRM",
            attrs = """{"documentFileId":"11111111-1111-1111-1111-111111111111"}"""
        )
        `when`(modelsRepository.findById(modelId)).thenReturn(Optional.of(model))
        `when`(nodesRepository.findByModel_IdAndIdIn(modelId, listOf(keep.id!!, drop.id!!)))
            .thenReturn(listOf(keep, drop))
        `when`(nodesRepository.existsByParentNode_Id(drop.id!!)).thenReturn(false)

        val exception = assertFailsWith<ResponseStatusException> {
            service.mergeNodes(
                modelId,
                MergeNodesRequest(
                    keepId = keep.id!!,
                    dropId = drop.id!!,
                    keepUpdatedAt = now,
                    dropUpdatedAt = now
                )
            )
        }

        assertEquals(HttpStatus.CONFLICT, exception.statusCode)
        assertEquals("Drop node has documents; transfer or clear them before merge", exception.reason)
    }

    private fun invokeRemapDropInstancesToKeep(attrs: String?, dropId: UUID, keepId: UUID): String? {
        val method = ModelValidationMergeService::class.java.getDeclaredMethod(
            "remapDropInstancesToKeep",
            String::class.java,
            UUID::class.java,
            UUID::class.java
        )
        method.isAccessible = true
        return method.invoke(service, attrs, dropId, keepId) as String?
    }

    private fun node(name: String, attrs: String? = null): Nodes =
        Nodes(
            id = UUID.randomUUID(),
            stableId = UUID.randomUUID(),
            name = name,
            model = model,
            owner = owner,
            nodeType = nodeType,
            attrs = attrs,
            createdAt = now,
            updatedAt = now
        )
}
