package ru.kavader.arepos.websocket

import org.springframework.http.server.ServerHttpRequest
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.stereotype.Component
import org.springframework.web.socket.WebSocketHandler
import org.springframework.web.socket.server.support.DefaultHandshakeHandler
import java.security.Principal
import java.util.UUID

@Component
class ModelSyncStompHandshakeHandler : DefaultHandshakeHandler() {

    override fun determineUser(
        request: ServerHttpRequest,
        wsHandler: WebSocketHandler,
        attributes: Map<String, Any>
    ): Principal {
        val userId = attributes[USER_ID_ATTR] as UUID
        val role = attributes[ROLE_ATTR] as String
        val authorities = listOf(SimpleGrantedAuthority("ROLE_$role"))
        return UsernamePasswordAuthenticationToken(userId, null, authorities)
    }

    companion object {
        const val JWT_ATTR: String = "arepos.ws.jwt"
        const val USER_ID_ATTR: String = "arepos.ws.userId"
        const val ROLE_ATTR: String = "arepos.ws.role"
    }
}
