package ru.kavader.arepos.controller

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import ru.kavader.arepos.model.Components
import ru.kavader.arepos.model.NodeShapes
import ru.kavader.arepos.model.NodeTypes
import ru.kavader.arepos.model.Notations
import ru.kavader.arepos.model.Role
import ru.kavader.arepos.model.Users
import ru.kavader.arepos.repository.ComponentsRepository
import ru.kavader.arepos.repository.NodeShapesRepository
import ru.kavader.arepos.repository.NodeTypesRepository
import ru.kavader.arepos.repository.NotationsRepository
import ru.kavader.arepos.repository.UsersRepository
import java.time.Instant
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@SpringBootTest
@AutoConfigureMockMvc
class CatalogSoftDeleteControllerTest : ControllerIntegrationTest() {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var usersRepository: UsersRepository

    @Autowired
    lateinit var nodeTypesRepository: NodeTypesRepository

    @Autowired
    lateinit var nodeShapesRepository: NodeShapesRepository

    @Autowired
    lateinit var notationsRepository: NotationsRepository

    @Autowired
    lateinit var componentsRepository: ComponentsRepository

    @Test
    fun `soft delete moves node type to admin deleted list and permanent removes it`() {
        val owner = usersRepository.save(
            Users(email = "catalog-soft-type@test.com", role = Role.ADMIN, createdAt = Instant.now())
        )
        val type = nodeTypesRepository.save(
            NodeTypes(
                name = "Soft Type",
                owner = owner,
                createdAt = Instant.now(),
                attrs = """{"color":"#111111"}"""
            )
        )

        mockMvc.perform(
            delete("/api/v1/node-types/${type.id}")
                .withAuth(owner.id!!, Role.ADMIN)
        ).andExpect(status().isNoContent)

        assertTrue(nodeTypesRepository.findById(type.id!!).isEmpty)
        assertTrue(nodeTypesRepository.findByIdIncludingDeleted(type.id!!).isPresent)
        assertTrue(nodeTypesRepository.findByIdIncludingDeleted(type.id!!).get().deleted)

        mockMvc.perform(
            get("/api/v1/node-types/deleted")
                .withAuth(owner.id!!, Role.ADMIN)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items[?(@.id == '${type.id}')]").exists())

        mockMvc.perform(
            delete("/api/v1/node-types/${type.id}/permanent")
                .withAuth(owner.id!!, Role.ADMIN)
        ).andExpect(status().isNoContent)

        assertTrue(nodeTypesRepository.findByIdIncludingDeleted(type.id!!).isEmpty)
    }

    @Test
    fun `soft delete moves shape to admin deleted list`() {
        val owner = usersRepository.save(
            Users(email = "catalog-soft-shape@test.com", role = Role.ADMIN, createdAt = Instant.now())
        )
        val shape = nodeShapesRepository.save(
            NodeShapes(
                name = "Soft Shape",
                owner = owner,
                outline = """[{"x":0,"y":0}]""",
                createdAt = Instant.now()
            )
        )

        mockMvc.perform(
            delete("/api/v1/node-shapes/${shape.id}")
                .withAuth(owner.id!!, Role.ADMIN)
        ).andExpect(status().isNoContent)

        assertTrue(nodeShapesRepository.findById(shape.id!!).isEmpty)
        assertTrue(nodeShapesRepository.findByIdIncludingDeleted(shape.id!!).get().deleted)

        mockMvc.perform(
            get("/api/v1/node-shapes/deleted")
                .withAuth(owner.id!!, Role.ADMIN)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items[?(@.id == '${shape.id}')]").exists())
    }

    @Test
    fun `permanent delete node type conflicts when still used by component`() {
        val owner = usersRepository.save(
            Users(email = "catalog-soft-conflict@test.com", role = Role.ADMIN, createdAt = Instant.now())
        )
        val type = nodeTypesRepository.save(
            NodeTypes(
                name = "Used Type",
                owner = owner,
                createdAt = Instant.now(),
                attrs = """{"color":"#222222"}"""
            )
        )
        val notation = notationsRepository.save(
            Notations(
                name = "Used Notation",
                version = "1.0.0",
                owner = owner,
                createdAt = Instant.now()
            )
        )
        componentsRepository.save(
            Components(
                name = "Used Component",
                version = "1.0.0",
                notation = notation,
                owner = owner,
                nodeType = type,
                createdAt = Instant.now()
            )
        )

        mockMvc.perform(
            delete("/api/v1/node-types/${type.id}")
                .withAuth(owner.id!!, Role.ADMIN)
        ).andExpect(status().isNoContent)

        mockMvc.perform(
            delete("/api/v1/node-types/${type.id}/permanent")
                .withAuth(owner.id!!, Role.ADMIN)
        ).andExpect(status().isConflict)

        assertFalse(nodeTypesRepository.findByIdIncludingDeleted(type.id!!).isEmpty)
    }
}
