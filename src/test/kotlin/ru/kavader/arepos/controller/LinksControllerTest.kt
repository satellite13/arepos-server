package ru.kavader.arepos.controller

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import ru.kavader.arepos.repository.*
import java.time.Instant
import java.util.*
import kotlin.test.assertEquals

@SpringBootTest
@AutoConfigureMockMvc
class LinksControllerTest : ControllerIntegrationTest() {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var objectMapper: ObjectMapper
    @Autowired lateinit var usersRepository: UsersRepository
    @Autowired lateinit var modelsRepository: ModelsRepository
    @Autowired lateinit var nodeTypesRepository: NodeTypesRepository
    @Autowired lateinit var nodesRepository: NodesRepository
    @Autowired lateinit var linkTypesRepository: LinkTypesRepository
    @Autowired lateinit var linksRepository: LinksRepository

    private lateinit var owner: ru.kavader.arepos.model.Users
    private lateinit var model: ru.kavader.arepos.model.Models
    private lateinit var nodeType: ru.kavader.arepos.model.NodeTypes
    private lateinit var sourceNode: ru.kavader.arepos.model.Nodes
    private lateinit var targetNode: ru.kavader.arepos.model.Nodes
    private lateinit var linkType: ru.kavader.arepos.model.LinkTypes

    @BeforeEach
    fun setUp() {
        owner = usersRepository.save(
            ru.kavader.arepos.model.Users(
                email = "link-owner-${UUID.randomUUID()}@test.com",
                createdAt = Instant.now()
            )
        )
        model = modelsRepository.save(
            ru.kavader.arepos.model.Models(
                name = "link-model-${UUID.randomUUID()}",
                createdAt = Instant.now(),
                version = "1.0.0",
                owner = owner
            )
        )
        nodeType = nodeTypesRepository.save(
            ru.kavader.arepos.model.NodeTypes(
                name = "link-node-type-${UUID.randomUUID()}",
                createdAt = Instant.now(),
                owner = owner
            )
        )
        sourceNode = nodesRepository.save(
            ru.kavader.arepos.model.Nodes(
                name = "source-node",
                model = model,
                owner = owner,
                nodeType = nodeType,
                createdAt = Instant.now()
            )
        )
        targetNode = nodesRepository.save(
            ru.kavader.arepos.model.Nodes(
                name = "target-node",
                model = model,
                owner = owner,
                nodeType = nodeType,
                createdAt = Instant.now()
            )
        )
        linkType = linkTypesRepository.save(
            ru.kavader.arepos.model.LinkTypes(
                name = "link-type-${UUID.randomUUID()}",
                createdAt = Instant.now(),
                owner = owner
            )
        )
    }

    @Test
    fun `creates link via REST`() {
        val payload = LinkRequest(
            sourceId = sourceNode.id!!,
            targetId = targetNode.id!!,
            modelId = model.id!!,
            ownerId = owner.id!!,
            linkTypeId = linkType.id!!,
            attrs = """{"weight":1}"""
        )

        mockMvc.perform(
            post("/api/v1/links")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.sourceId").value(sourceNode.id.toString()))
            .andExpect(jsonPath("$.targetId").value(targetNode.id.toString()))

        assertEquals(1, linksRepository.count())
    }
}


