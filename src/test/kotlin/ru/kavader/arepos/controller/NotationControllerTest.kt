package ru.kavader.arepos.controller

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(NotationController::class)
class NotationControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun `save notation returns OK`() {
        mockMvc.perform(
            post("/api/v1/notation/{id}", "12345")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Hello World\"}")
        )
            .andExpect(status().isOk)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(content().string("OK"))
    }
}


