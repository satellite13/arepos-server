package ru.kavader.arepos.controller

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import ru.kavader.arepos.model.Components
import ru.kavader.arepos.model.NodeTypes
import ru.kavader.arepos.model.Notations
import ru.kavader.arepos.model.Role
import ru.kavader.arepos.model.Users
import ru.kavader.arepos.repository.ComponentsRepository
import ru.kavader.arepos.repository.NodeTypesRepository
import ru.kavader.arepos.repository.NotationsRepository
import ru.kavader.arepos.repository.UsersRepository
import java.time.Instant

@SpringBootTest
@AutoConfigureMockMvc
class NotationExportControllerTest : ControllerIntegrationTest() {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Autowired
    lateinit var usersRepository: UsersRepository

    @Autowired
    lateinit var notationsRepository: NotationsRepository

    @Autowired
    lateinit var nodeTypesRepository: NodeTypesRepository

    @Autowired
    lateinit var componentsRepository: ComponentsRepository

    @Test
    fun `export returns warchi-notation-export json attachment`() {
        val owner = usersRepository.save(
            Users(
                email = "notation-export-owner@test.com",
                role = Role.USER,
                createdAt = Instant.now()
            )
        )
        val notation = notationsRepository.save(
            Notations(
                owner = owner,
                name = "Export Controller Notation",
                version = "1.0.0",
                attrs = """{"format":"json"}""",
                createdAt = Instant.now(),
                deleted = false
            )
        )
        val nodeType = nodeTypesRepository.save(
            NodeTypes(
                name = "Export Controller Type",
                owner = owner,
                createdAt = Instant.now()
            )
        )
        componentsRepository.save(
            Components(
                name = "Export Controller Component",
                version = "1.0.0",
                notation = notation,
                owner = owner,
                nodeType = nodeType,
                createdAt = Instant.now()
            )
        )

        mockMvc.perform(
            get("/api/v1/notations/${notation.id}/export")
                .withAuth(owner.id!!, Role.USER)
        )
            .andExpect(status().isOk)
            .andExpect(header().string("Content-Disposition", "attachment; filename=\"notation-export.json\""))
            .andExpect(header().string("Content-Type", MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.format").value("warchi-notation-export"))
            .andExpect(jsonPath("$.version").value(2))
            .andExpect(jsonPath("$.notation.id").value(notation.id.toString()))
            .andExpect(jsonPath("$.notation.name").value("Export Controller Notation"))
            .andExpect(jsonPath("$.state.components[0].name").value("Export Controller Component"))
            .andExpect(jsonPath("$.shapes").isArray)
    }

    @Test
    fun `export returns 404 for missing notation`() {
        val owner = usersRepository.save(
            Users(
                email = "notation-export-missing@test.com",
                role = Role.USER,
                createdAt = Instant.now()
            )
        )

        mockMvc.perform(
            get("/api/v1/notations/11111111-1111-1111-1111-111111111111/export")
                .withAuth(owner.id!!, Role.USER)
        )
            .andExpect(status().isNotFound)
    }
}
