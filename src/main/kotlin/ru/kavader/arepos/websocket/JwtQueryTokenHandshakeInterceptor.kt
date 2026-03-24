package ru.kavader.arepos.websocket

import org.springframework.http.server.ServerHttpRequest
import org.springframework.http.server.ServerHttpResponse
import org.springframework.http.server.ServletServerHttpRequest
import org.springframework.stereotype.Component
import org.springframework.web.socket.WebSocketHandler
import org.springframework.web.socket.server.HandshakeInterceptor
import ru.kavader.arepos.security.JwtTokenProvider

@Component
class JwtQueryTokenHandshakeInterceptor(
    private val jwtTokenProvider: JwtTokenProvider
) : HandshakeInterceptor {

    override fun beforeHandshake(
        request: ServerHttpRequest,
        response: ServerHttpResponse,
        wsHandler: WebSocketHandler,
        attributes: MutableMap<String, Any>
    ): Boolean {
        val servletRequest = (request as? ServletServerHttpRequest)?.servletRequest ?: return false
        val token = servletRequest.getParameter("token")?.trim()?.takeIf { it.isNotEmpty() } ?: return false
        if (!jwtTokenProvider.validateToken(token)) {
            return false
        }
        if (jwtTokenProvider.getTokenType(token) != "access") {
            return false
        }
        attributes[ModelSyncStompHandshakeHandler.JWT_ATTR] = token
        return true
    }

    override fun afterHandshake(
        request: ServerHttpRequest,
        response: ServerHttpResponse,
        wsHandler: WebSocketHandler,
        exception: Exception?
    ) {
        // no-op
    }
}
