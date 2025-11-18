package ru.kavader.arepos.controller

import org.junit.jupiter.api.Test
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import ru.kavader.arepos.metrics.CustomMetricsService
import kotlin.test.assertEquals

@WebMvcTest(HelloWorldController::class)
class HelloWorldControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockitoBean
    lateinit var customMetricsService: CustomMetricsService

    @Test
    fun `hello-world endpoint returns text and increments metric`() {
        val mvcResult = mockMvc.perform(get("/hello-world"))
            .andExpect(status().isOk)
            .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
            .andReturn()

        assertEquals("Hello World", mvcResult.response.contentAsString)
        verify(customMetricsService).incrementHelloWorldCounter()
    }
}


