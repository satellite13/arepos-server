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
import ru.kavader.arepos.model.Role
import ru.kavader.arepos.repository.*
import java.time.Instant
import java.util.*
import kotlin.test.assertEquals

@SpringBootTest
@AutoConfigureMockMvc
class RelationRulesControllerTest : ControllerIntegrationTest() {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var objectMapper: ObjectMapper
    @Autowired lateinit var usersRepository: UsersRepository
    @Autowired lateinit var notationsRepository: NotationsRepository
    @Autowired lateinit var nodeTypesRepository: NodeTypesRepository
    @Autowired lateinit var componentsRepository: ComponentsRepository
    @Autowired lateinit var linkTypesRepository: LinkTypesRepository
    @Autowired lateinit var relationsRepository: RelationsRepository
    @Autowired lateinit var relationRulesRepository: RelationRulesRepository

    private lateinit var owner: ru.kavader.arepos.model.Users
    private lateinit var notation: ru.kavader.arepos.model.Notations
    private lateinit var nodeType: ru.kavader.arepos.model.NodeTypes
    private lateinit var componentA: ru.kavader.arepos.model.Components
    private lateinit var componentB: ru.kavader.arepos.model.Components
    private lateinit var relation: ru.kavader.arepos.model.Relations

    @BeforeEach
    fun setUp() {
        owner = usersRepository.save(
            ru.kavader.arepos.model.Users(
                email = "rule-owner-${UUID.randomUUID()}@test.com",
                role = Role.ADMIN,
                createdAt = Instant.now()
            )
        )
        notation = notationsRepository.save(
            ru.kavader.arepos.model.Notations(
                name = "rule-notation-${UUID.randomUUID()}",
                version = "1.0.0",
                owner = owner,
                createdAt = Instant.now()
            )
        )
        nodeType = nodeTypesRepository.save(
            ru.kavader.arepos.model.NodeTypes(
                name = "rule-node-type-${UUID.randomUUID()}",
                createdAt = Instant.now(),
                owner = owner
            )
        )
        componentA = componentsRepository.save(
            ru.kavader.arepos.model.Components(
                name = "component-a",
                createdAt = Instant.now(),
                version = "1.0.0",
                notation = notation,
                owner = owner,
                nodeType = nodeType
            )
        )
        componentB = componentsRepository.save(
            ru.kavader.arepos.model.Components(
                name = "component-b",
                createdAt = Instant.now(),
                version = "1.0.0",
                notation = notation,
                owner = owner,
                nodeType = nodeType
            )
        )
        val linkType = linkTypesRepository.save(
            ru.kavader.arepos.model.LinkTypes(
                name = "rule-link-type-${UUID.randomUUID()}",
                createdAt = Instant.now(),
                owner = owner
            )
        )
        relation = relationsRepository.save(
            ru.kavader.arepos.model.Relations(
                name = "relation-${UUID.randomUUID()}",
                createdAt = Instant.now(),
                version = "1.0.0",
                notation = notation,
                owner = owner,
                linkType = linkType
            )
        )
    }

    @Test
    fun `creates relation rule via REST`() {
        val payload = RelationRuleRequest(
            relationId = relation.id!!,
            fromComponentId = componentA.id!!,
            toComponentId = componentB.id!!,
            ownerId = owner.id!!,
            attrs = """{"policy":"allow"}"""
        )

        mockMvc.perform(
            post("/api/v1/relation-rules")
                .withAuth(owner.id!!)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.relationId").value(relation.id.toString()))
            .andExpect(jsonPath("$.fromComponentId").value(componentA.id.toString()))

        assertEquals(1, relationRulesRepository.count())
    }
}
