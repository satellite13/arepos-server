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
import ru.kavader.arepos.model.Models
import ru.kavader.arepos.model.Users
import ru.kavader.arepos.repository.ModelsRepository
import ru.kavader.arepos.repository.NodeTreePageProjection
import ru.kavader.arepos.repository.NodesRepository
import ru.kavader.arepos.security.ResourceAccessService
import java.time.Instant
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@ExtendWith(MockitoExtension::class)
class ModelTreeQueryServiceTest {
    @Mock
    lateinit var modelsRepository: ModelsRepository

    @Mock
    lateinit var nodesRepository: NodesRepository

    @Mock
    lateinit var accessService: ResourceAccessService

    @Test
    fun `maps projected rows while preserving deterministic order`() {
        val owner = Users(id = UUID.randomUUID(), email = "owner@test.com", createdAt = Instant.now())
        val model = Models(
            id = UUID.randomUUID(),
            name = "model",
            createdAt = Instant.now(),
            attrs = "{}",
            version = "1.0.0",
            owner = owner
        )
        val firstId = UUID.randomUUID()
        val secondId = UUID.randomUUID()
        val pageable = PageRequest.of(0, 10)
        val projections: Page<NodeTreePageProjection> = PageImpl(
            listOf(
                projection(secondId, "Second", model.id!!, owner.id!!),
                projection(firstId, "First", model.id!!, owner.id!!)
            ),
            pageable,
            2
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

    @Test
    fun `single projection page keeps next page reachable`() {
        val owner = Users(id = UUID.randomUUID(), email = "owner@test.com", createdAt = Instant.now())
        val model = Models(
            id = UUID.randomUUID(),
            name = "model",
            createdAt = Instant.now(),
            attrs = "{}",
            version = "1.0.0",
            owner = owner
        )
        val firstId = UUID.randomUUID()
        val secondId = UUID.randomUUID()
        val pageable = PageRequest.of(0, 2)
        val projections: Page<NodeTreePageProjection> = PageImpl(
            listOf(
                projection(firstId, "First", model.id!!, owner.id!!),
                projection(secondId, "Second", model.id!!, owner.id!!)
            ),
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

        val result = service().listChildren(
            modelId = model.id!!,
            parentRef = "root",
            excludeSystem = true,
            foldersOnly = false,
            pageable = pageable
        )

        assertEquals(listOf(firstId, secondId), result.content.map { it.id })
        assertEquals(3, result.totalElements)
        assertTrue(result.hasNext())
    }

    private fun service() = ModelTreeQueryService(
        modelsRepository,
        nodesRepository,
        accessService,
        ObjectMapper()
    )

    private fun projection(
        id: UUID,
        name: String,
        modelId: UUID,
        ownerId: UUID
    ) = object : NodeTreePageProjection {
        override fun getId(): UUID = id
        override fun getStableId(): UUID = id
        override fun getName(): String = name
        override fun getModelId(): UUID = modelId
        override fun getOwnerId(): UUID = ownerId
        override fun getNodeTypeId(): UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
        override fun getParentNodeId(): UUID? = null
        override fun getAttrs(): String? = null
        override fun getCreatedAt(): Instant? = null
        override fun getUpdatedAt(): Instant? = null
        override fun getHasChildren(): Boolean = false
    }
}
