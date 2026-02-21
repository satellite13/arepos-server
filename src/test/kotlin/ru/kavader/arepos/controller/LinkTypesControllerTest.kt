package ru.kavader.arepos.controller

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import ru.kavader.arepos.model.Role
import ru.kavader.arepos.repository.LinkTypesRepository
import ru.kavader.arepos.repository.UsersRepository
import java.time.Instant
import kotlin.test.assertEquals

@SpringBootTest
@AutoConfigureMockMvc
class LinkTypesControllerTest : ControllerIntegrationTest() {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Autowired
    lateinit var usersRepository: UsersRepository

    @Autowired
    lateinit var linkTypesRepository: LinkTypesRepository

    @Test
    fun `creates link type via REST`() {
        val owner = usersRepository.save(
            ru.kavader.arepos.model.Users(
                email = "owner-link-type@test.com",
                role = Role.EDITOR,
                createdAt = Instant.now()
            )
        )

        val payload = LinkTypeRequest(
            name = "test-link-type-${System.currentTimeMillis()}",
            ownerId = owner.id!!,
            attrs = """{"key":"value"}"""
        )

        mockMvc.perform(
            post("/api/v1/link-types")
                .withAuth(owner.id!!, Role.EDITOR)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.name").value(payload.name))
            .andExpect(jsonPath("$.ownerId").value(owner.id.toString()))

        assertEquals(1, linkTypesRepository.count())
    }

    @Test
    fun `lists link types`() {
        val timestamp = System.currentTimeMillis()
        val owner = usersRepository.save(
            ru.kavader.arepos.model.Users(
                email = "owner-list-link-type-$timestamp@test.com",
                role = Role.ADMIN,
                createdAt = Instant.now()
            )
        )
        linkTypesRepository.saveAll(
            listOf(
                ru.kavader.arepos.model.LinkTypes(
                    name = "link-type-1-$timestamp",
                    createdAt = Instant.now(),
                    owner = owner
                ),
                ru.kavader.arepos.model.LinkTypes(
                    name = "link-type-2-$timestamp",
                    createdAt = Instant.now(),
                    owner = owner
                )
            )
        )

        mockMvc.perform(
            get("/api/v1/link-types?page=0&size=10")
                .withAuth(owner.id!!)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content.length()").value(2))
            .andExpect(jsonPath("$.totalElements").value(2))
    }
}
