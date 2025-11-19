package ru.kavader.arepos.controller

import com.fasterxml.jackson.databind.ObjectMapper
import org.hamcrest.Matchers.greaterThan
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
import ru.kavader.arepos.repository.UsersRepository

@SpringBootTest
@AutoConfigureMockMvc
class AuditLogControllerTest : ControllerIntegrationTest() {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Autowired
    lateinit var usersRepository: UsersRepository

    @Test
    fun `lists audit log entries`() {
        mockMvc.perform(
            post("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(UserRequest("audit@test.com", """{"role":"auditor"}""")))
        ).andExpect(status().isCreated)

        mockMvc.perform(get("/api/v1/audit-log?page=0&size=5"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalElements").value(greaterThan(0)))
    }
}


