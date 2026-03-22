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
import ru.kavader.arepos.model.ResourceShares
import ru.kavader.arepos.model.Role
import ru.kavader.arepos.model.SharePermission
import ru.kavader.arepos.model.ShareResourceType
import ru.kavader.arepos.repository.NotationsRepository
import ru.kavader.arepos.repository.ResourceSharesRepository
import ru.kavader.arepos.repository.UsersRepository
import java.time.Instant
import kotlin.test.assertEquals

@SpringBootTest
@AutoConfigureMockMvc
class NotationsControllerTest : ControllerIntegrationTest() {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Autowired
    lateinit var usersRepository: UsersRepository

    @Autowired
    lateinit var notationsRepository: NotationsRepository

    @Autowired
    lateinit var resourceSharesRepository: ResourceSharesRepository

    @Test
    fun `creates notation via REST`() {
        val owner = usersRepository.save(
            ru.kavader.arepos.model.Users(
                email = "owner@test.com",
                role = Role.ADMIN,
                createdAt = Instant.now()
            )
        )

        val payload = NotationRequest(
            name = "test-notation",
            version = "1.0.0",
            ownerId = owner.id!!,
            attrs = """{"type":"test"}"""
        )

        mockMvc.perform(
            post("/api/v1/notations")
                .withAuth(owner.id!!)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.name").value("test-notation"))
            .andExpect(jsonPath("$.version").value("1.0.0"))

        assertEquals(1, notationsRepository.count())
    }

    @Test
    fun `lists notations with filters`() {
        val owner = usersRepository.save(
            ru.kavader.arepos.model.Users(
                email = "owner@test.com",
                role = Role.ADMIN,
                createdAt = Instant.now()
            )
        )

        notationsRepository.saveAll(
            listOf(
                ru.kavader.arepos.model.Notations(
                    name = "notation-1",
                    version = "1.0.0",
                    owner = owner,
                    createdAt = Instant.now()
                ),
                ru.kavader.arepos.model.Notations(
                    name = "notation-2",
                    version = "1.0.1",
                    owner = owner,
                    createdAt = Instant.now()
                )
            )
        )

        mockMvc.perform(
            get("/api/v1/notations?ownerId=${owner.id}&page=0&size=10")
                .withAuth(owner.id!!)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content.length()").value(2))
            .andExpect(jsonPath("$.page.totalElements").value(2))
    }

    @Test
    fun `user sees only own notations`() {
        val userA = usersRepository.save(
            ru.kavader.arepos.model.Users(
                email = "notation-a@test.com",
                role = Role.USER,
                createdAt = Instant.now()
            )
        )
        val userB = usersRepository.save(
            ru.kavader.arepos.model.Users(
                email = "notation-b@test.com",
                role = Role.USER,
                createdAt = Instant.now()
            )
        )
        val ownNotation = notationsRepository.save(
            ru.kavader.arepos.model.Notations(
                name = "own-notation",
                version = "1.0.0",
                owner = userA,
                createdAt = Instant.now()
            )
        )
        notationsRepository.save(
            ru.kavader.arepos.model.Notations(
                name = "foreign-notation",
                version = "1.0.0",
                owner = userB,
                createdAt = Instant.now()
            )
        )

        mockMvc.perform(
            get("/api/v1/notations?page=0&size=10")
                .withAuth(userA.id!!, Role.USER)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.content[0].id").value(ownNotation.id.toString()))
    }

    @Test
    fun `user cannot read foreign notation by id`() {
        val userA = usersRepository.save(
            ru.kavader.arepos.model.Users(
                email = "reader-a@test.com",
                role = Role.USER,
                createdAt = Instant.now()
            )
        )
        val userB = usersRepository.save(
            ru.kavader.arepos.model.Users(
                email = "reader-b@test.com",
                role = Role.USER,
                createdAt = Instant.now()
            )
        )
        val foreignNotation = notationsRepository.save(
            ru.kavader.arepos.model.Notations(
                name = "foreign-notation",
                version = "1.0.0",
                owner = userB,
                createdAt = Instant.now()
            )
        )

        mockMvc.perform(
            get("/api/v1/notations/${foreignNotation.id}")
                .withAuth(userA.id!!, Role.USER)
        )
            .andExpect(status().isForbidden)
    }

    @Test
    fun `shared user can create notation version from source copy`() {
        val now = Instant.now()
        val owner = usersRepository.save(
            ru.kavader.arepos.model.Users(
                email = "notation-owner-copy@test.com",
                role = Role.USER,
                createdAt = now
            )
        )
        val viewer = usersRepository.save(
            ru.kavader.arepos.model.Users(
                email = "notation-viewer-copy@test.com",
                role = Role.USER,
                createdAt = now
            )
        )
        val source = notationsRepository.save(
            ru.kavader.arepos.model.Notations(
                name = "shared-notation-copy",
                version = "1.0.0",
                owner = owner,
                createdAt = now,
                updatedAt = now
            )
        )

        resourceSharesRepository.save(
            ResourceShares(
                resourceType = ShareResourceType.NOTATION,
                resourceId = source.id!!,
                granteeUser = viewer,
                grantedByUser = owner,
                permission = SharePermission.EDIT,
                createdAt = now,
                updatedAt = now
            )
        )

        val payload = NotationRequest(
            name = source.name,
            version = "1.1.0",
            ownerId = viewer.id
        )

        mockMvc.perform(
            post("/api/v1/notations/${source.id}/copy")
                .withAuth(viewer.id!!, Role.USER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.name").value("shared-notation-copy"))
            .andExpect(jsonPath("$.version").value("1.1.0"))
            .andExpect(jsonPath("$.ownerId").value(viewer.id.toString()))
    }
}
