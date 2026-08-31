package ru.kavader.arepos.websocket

import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.http.server.ServerHttpRequest
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.http.server.ServletServerHttpRequest
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.web.socket.WebSocketHandler
import java.security.Principal
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class TestableModelSyncStompHandshakeHandler : ModelSyncStompHandshakeHandler() {
    fun userFromAttributes(
        request: ServerHttpRequest,
        wsHandler: WebSocketHandler,
        attributes: Map<String, Any>
    ): Principal = determineUser(request, wsHandler, attributes)
}

class ModelSyncStompHandshakeHandlerTest {

    @Test
    fun `uses DB role from attributes for authorities`() {
        val handler = TestableModelSyncStompHandshakeHandler()
        val userId = UUID.randomUUID()
        val attributes = mapOf<String, Any>(
            ModelSyncStompHandshakeHandler.USER_ID_ATTR to userId,
            ModelSyncStompHandshakeHandler.ROLE_ATTR to "ADMIN"
        )
        val request = ServletServerHttpRequest(MockHttpServletRequest())
        val principal = handler.userFromAttributes(request, mock(WebSocketHandler::class.java), attributes)
            as UsernamePasswordAuthenticationToken

        assertEquals(userId, principal.principal)
        assertTrue(principal.authorities.contains(SimpleGrantedAuthority("ROLE_ADMIN")))
    }
}
