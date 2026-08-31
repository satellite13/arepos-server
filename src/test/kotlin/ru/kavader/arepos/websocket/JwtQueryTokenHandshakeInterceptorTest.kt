package ru.kavader.arepos.websocket

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.http.server.ServletServerHttpRequest
import org.springframework.http.server.ServletServerHttpResponse
import org.springframework.web.socket.WebSocketHandler
import ru.kavader.arepos.model.Role
import ru.kavader.arepos.model.Users
import ru.kavader.arepos.repository.UsersRepository
import ru.kavader.arepos.security.JwtTokenProvider
import ru.kavader.arepos.security.TokenType
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@ExtendWith(MockitoExtension::class)
class JwtQueryTokenHandshakeInterceptorTest {

    @Mock
    lateinit var jwtTokenProvider: JwtTokenProvider

    @Mock
    lateinit var usersRepository: UsersRepository

    @Mock
    lateinit var wsHandler: WebSocketHandler

    @Test
    fun `rejects handshake when user is missing`() {
        val interceptor = JwtQueryTokenHandshakeInterceptor(jwtTokenProvider, usersRepository)
        val userId = UUID.randomUUID()
        val servletRequest = MockHttpServletRequest()
        servletRequest.setParameter("token", "jwt")
        val request = ServletServerHttpRequest(servletRequest)
        val response = ServletServerHttpResponse(MockHttpServletResponse())
        val attributes = mutableMapOf<String, Any>()

        `when`(jwtTokenProvider.validateToken("jwt")).thenReturn(true)
        `when`(jwtTokenProvider.getTokenType("jwt")).thenReturn(TokenType.ACCESS)
        `when`(jwtTokenProvider.getUserId("jwt")).thenReturn(userId)
        `when`(usersRepository.findById(userId)).thenReturn(Optional.empty())

        assertFalse(interceptor.beforeHandshake(request, response, wsHandler, attributes))
    }

    @Test
    fun `rejects handshake when user is inactive`() {
        val interceptor = JwtQueryTokenHandshakeInterceptor(jwtTokenProvider, usersRepository)
        val userId = UUID.randomUUID()
        val user = Users(id = userId, email = "inactive@test.com", role = Role.ADMIN, isActive = false)
        val servletRequest = MockHttpServletRequest()
        servletRequest.setParameter("token", "jwt")
        val request = ServletServerHttpRequest(servletRequest)
        val response = ServletServerHttpResponse(MockHttpServletResponse())
        val attributes = mutableMapOf<String, Any>()

        `when`(jwtTokenProvider.validateToken("jwt")).thenReturn(true)
        `when`(jwtTokenProvider.getTokenType("jwt")).thenReturn(TokenType.ACCESS)
        `when`(jwtTokenProvider.getUserId("jwt")).thenReturn(userId)
        `when`(usersRepository.findById(userId)).thenReturn(Optional.of(user))

        assertFalse(interceptor.beforeHandshake(request, response, wsHandler, attributes))
    }

    @Test
    fun `stores DB role in attributes for active user`() {
        val interceptor = JwtQueryTokenHandshakeInterceptor(jwtTokenProvider, usersRepository)
        val userId = UUID.randomUUID()
        val user = Users(id = userId, email = "active@test.com", role = Role.EDITOR, isActive = true)
        val servletRequest = MockHttpServletRequest()
        servletRequest.setParameter("token", "jwt")
        val request = ServletServerHttpRequest(servletRequest)
        val response = ServletServerHttpResponse(MockHttpServletResponse())
        val attributes = mutableMapOf<String, Any>()

        `when`(jwtTokenProvider.validateToken("jwt")).thenReturn(true)
        `when`(jwtTokenProvider.getTokenType("jwt")).thenReturn(TokenType.ACCESS)
        `when`(jwtTokenProvider.getUserId("jwt")).thenReturn(userId)
        `when`(usersRepository.findById(userId)).thenReturn(Optional.of(user))

        assertTrue(interceptor.beforeHandshake(request, response, wsHandler, attributes))
        assertEquals(userId, attributes[ModelSyncStompHandshakeHandler.USER_ID_ATTR])
        assertEquals("EDITOR", attributes[ModelSyncStompHandshakeHandler.ROLE_ATTR])
    }
}
