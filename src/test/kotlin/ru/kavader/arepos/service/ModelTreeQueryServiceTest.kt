package ru.kavader.arepos.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import ru.kavader.arepos.dto.model.NodeResponse
import ru.kavader.arepos.mapper.ModelMapper
import ru.kavader.arepos.model.Models
import ru.kavader.arepos.model.NodeTypes
import ru.kavader.arepos.model.Nodes
import ru.kavader.arepos.model.Users
import ru.kavader.arepos.repository.ModelsRepository
import ru.kavader.arepos.repository.NodeTreePageProjection
import ru.kavader.arepos.repository.NodesRepository
import ru.kavader.arepos.security.ResourceAccessService
import java.time.Instant
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals

@ExtendWith(MockitoExtension::class)
class ModelTreeQueryServiceTest {
    @Mock
    lateinit var modelsRepository: ModelsRepository

    @Mock
    lateinit var nodesRepository: NodesRepository

    @Mock
    lateinit var accessService: ResourceAccessService

    @Mock
    lateinit var modelMapper: ModelMapper

    @Test
    fun `skips rows deleted between projection and entity lookup while preserving available order`() {
        val owner = Users(id = UUID.randomUUID(), email = "owner@test.com", createdAt = Instant.now())
        val model = Models(
            id = UUID.randomUUID(),
            name = "model",
            createdAt = Instant.now(),
            attrs = "{}",
            version = "1.0.0",
            owner = owner
        )
        val nodeType = NodeTypes(
            id = UUID.randomUUID(),
            name = "Application",
            createdAt = Instant.now(),
            owner = owner
        )
        val firstId = UUID.randomUUID()
        val deletedId = UUID.randomUUID()
        val secondId = UUID.randomUUID()
        val first = node(firstId, "First", model, owner, nodeType)
        val second = node(secondId, "Second", model, owner, nodeType)
        val pageable = PageRequest.of(0, 10)
        val projections: Page<NodeTreePageProjection> = PageImpl(
            listOf(projection(secondId), projection(deletedId), projection(firstId)),
            pageable,
            3
        )
        `when`(modelsRepository.findById(model.id!!)).thenReturn(Optional.of(model))
        `when`(
            nodesRepository.findDirectChildrenPage(
                model.id!!,
                null,
                excludeSystem = true,
                foldersOnly = false,
                pageable
            )
        ).thenReturn(projections)
        `when`(nodesRepository.findAllById(listOf(secondId, deletedId, firstId)))
            .thenReturn(listOf(first, second))
        `when`(modelMapper.toResponse(first, false)).thenReturn(response(first))
        `when`(modelMapper.toResponse(second, false)).thenReturn(response(second))

        val result = service().listChildren(
            modelId = model.id!!,
            parentRef = "root",
            excludeSystem = true,
            foldersOnly = false,
            pageable = pageable
        )

        assertEquals(listOf(secondId, firstId), result.content.map { it.id })
        assertEquals(2, result.totalElements)
    }

    private fun service() = ModelTreeQueryService(
        modelsRepository,
        nodesRepository,
        accessService,
        modelMapper,
        ObjectMapper()
    )

    private fun projection(id: UUID) = object : NodeTreePageProjection {
        override fun getId(): UUID = id
        override fun getHasChildren(): Boolean = false
    }

    private fun node(
        id: UUID,
        name: String,
        model: Models,
        owner: Users,
        nodeType: NodeTypes
    ) = Nodes(
        id = id,
        stableId = UUID.randomUUID(),
        name = name,
        createdAt = Instant.now(),
        model = model,
        owner = owner,
        nodeType = nodeType
    )

    private fun response(node: Nodes) = NodeResponse(
        id = node.id!!,
        stableId = node.stableId,
        name = node.name,
        modelId = node.model.id!!,
        ownerId = node.owner.id!!,
        nodeTypeId = node.nodeType.id!!,
        parentNodeId = null,
        attrs = null,
        createdAt = node.createdAt,
        updatedAt = null,
        hasChildren = false
    )
}
