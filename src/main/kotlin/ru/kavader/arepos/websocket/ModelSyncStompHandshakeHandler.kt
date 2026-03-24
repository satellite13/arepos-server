package ru.kavader.arepos.websocket

import org.springframework.http.server.ServerHttpRequest
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.stereotype.Component
import org.springframework.web.socket.WebSocketHandler
import org.springframework.web.socket.server.support.DefaultHandshakeHandler
import ru.kavader.arepos.security.JwtTokenProvider
import java.security.Principal
import java.util.UUID

@Component
class ModelSyncStompHandshakeHandler(
    private val jwtTokenProvider: JwtTokenProvider
) : DefaultHandshakeHandler() {

    override fun determineUser(
        request: ServerHttpRequest,
        wsHandler: WebSocketHandler,
        attributes: Map<String, Any>
    ): Principal {
        val token = attributes[JWT_ATTR] as String
        val userId = jwtTokenProvider.getUserId(token)
        val role = jwtTokenProvider.getRole(token)
        val authorities = listOf(SimpleGrantedAuthority("ROLE_$role"))
        return UsernamePasswordAuthenticationToken(userId, null, authorities)
    }

    companion object {
        const val JWT_ATTR: String = "arepos.ws.jwt"
    }
}
