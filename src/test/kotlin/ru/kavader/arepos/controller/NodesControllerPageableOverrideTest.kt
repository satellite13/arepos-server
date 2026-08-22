package ru.kavader.arepos.controller

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import ru.kavader.arepos.model.Role
import ru.kavader.arepos.model.Users
import ru.kavader.arepos.repository.UsersRepository
import java.time.Instant
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(
    properties = [
        "spring.data.web.pageable.max-page-size=100",
        "spring.data.web.pageable.default-page-size=10"
    ]
)
class NodesControllerPageableOverrideTest : ControllerIntegrationTest() {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var usersRepository: UsersRepository

    private lateinit var owner: Users

    @BeforeEach
    fun setUp() {
        owner = usersRepository.save(
            Users(
                email = "node-pageable-owner-${UUID.randomUUID()}@test.com",
                role = Role.ADMIN,
                createdAt = Instant.now()
            )
        )
    }

    @Test
    fun `uses overridden default page size when size is omitted`() {
        mockMvc.perform(
            get("/api/v1/nodes")
                .withAuth(owner.id!!)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.page.size").value(10))
    }

    @Test
    fun `caps requested page size at overridden maximum`() {
        mockMvc.perform(
            get("/api/v1/nodes?size=500")
                .withAuth(owner.id!!)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.page.size").value(100))
    }
}
