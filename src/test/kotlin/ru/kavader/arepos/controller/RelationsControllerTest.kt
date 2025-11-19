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
class RelationsControllerTest : ControllerIntegrationTest() {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var objectMapper: ObjectMapper
    @Autowired lateinit var usersRepository: UsersRepository
    @Autowired lateinit var notationsRepository: NotationsRepository
    @Autowired lateinit var linkTypesRepository: LinkTypesRepository
    @Autowired lateinit var relationsRepository: RelationsRepository

    private lateinit var owner: ru.kavader.arepos.model.Users
    private lateinit var notation: ru.kavader.arepos.model.Notations
    private lateinit var linkType: ru.kavader.arepos.model.LinkTypes

    @BeforeEach
    fun setUp() {
        owner = usersRepository.save(
            ru.kavader.arepos.model.Users(
                email = "relation-owner-${UUID.randomUUID()}@test.com",
                createdAt = Instant.now()
            )
        )
        notation = notationsRepository.save(
            ru.kavader.arepos.model.Notations(
                name = "relation-notation-${UUID.randomUUID()}",
                version = "1.0.0",
                owner = owner,
                createdAt = Instant.now()
            )
        )
        linkType = linkTypesRepository.save(
            ru.kavader.arepos.model.LinkTypes(
                name = "relation-link-type-${UUID.randomUUID()}",
                createdAt = Instant.now(),
                owner = owner
            )
        )
    }

    @Test
    fun `creates relation via REST`() {
        val payload = RelationRequest(
            name = "Relation-${System.currentTimeMillis()}",
            version = "1.0.0",
            notationId = notation.id!!,
            ownerId = owner.id!!,
            linkTypeId = linkType.id!!,
            attrs = """{"rule":"allow"}"""
        )

        mockMvc.perform(
            post("/api/v1/relations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.name").value(payload.name))
            .andExpect(jsonPath("$.notationId").value(notation.id.toString()))

        assertEquals(1, relationsRepository.count())
    }
}


