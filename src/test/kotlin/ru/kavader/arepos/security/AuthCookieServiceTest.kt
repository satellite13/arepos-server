package ru.kavader.arepos.security

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.mock.web.MockHttpServletResponse
import ru.kavader.arepos.config.AreposAuthProperties
import java.time.Duration

class AuthCookieServiceTest {

    @Test
    fun `sets cookie domain when configured`() {
        val jwt = mock(JwtTokenProvider::class.java)
        `when`(jwt.accessExpirationSeconds()).thenReturn(1800)
        `when`(jwt.refreshExpirationSeconds()).thenReturn(Duration.ofDays(7).seconds)
        val service = AuthCookieService(
            jwt,
            AreposAuthProperties(cookieDomain = ".example.com")
        )
        val response = MockHttpServletResponse()
        service.writeAuthCookies(response, "access", "refresh", "csrf")
        val headers = response.getHeaders("Set-Cookie")
        assertTrue(headers.any { it.contains("Domain=.example.com") })
    }
}
