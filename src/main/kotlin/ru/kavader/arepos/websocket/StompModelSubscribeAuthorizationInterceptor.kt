package ru.kavader.arepos.websocket

import org.springframework.messaging.Message
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.simp.stomp.StompCommand
import org.springframework.messaging.simp.stomp.StompHeaderAccessor
import org.springframework.messaging.support.ChannelInterceptor
import org.springframework.messaging.support.MessageHeaderAccessor
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import ru.kavader.arepos.repository.ModelsRepository
import ru.kavader.arepos.security.ResourceAccessService
import java.util.*

/**
 * Разрешает только подписку на `/topic/models/{modelId}` при праве просмотра модели.
 * Клиентские SEND запрещены — публикация только с сервера.
 */
@Component
class StompModelSubscribeAuthorizationInterceptor(
    private val modelsRepository: ModelsRepository,
    private val accessService: ResourceAccessService
) : ChannelInterceptor {

    private val topicModelRegex = Regex("^/topic/models/([0-9a-fA-F-]{36})$")

    override fun preSend(message: Message<*>, channel: MessageChannel): Message<*> {
        val accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor::class.java) ?: return message
        val command = accessor.command ?: return message

        when (command) {
            StompCommand.CONNECT,
            StompCommand.STOMP,
            StompCommand.DISCONNECT,
            StompCommand.UNSUBSCRIBE,
            StompCommand.ACK,
            StompCommand.NACK -> return message

            StompCommand.SEND -> throw SecurityException("Client STOMP SEND is not allowed")
            StompCommand.SUBSCRIBE -> {
                val dest = accessor.destination
                    ?: throw SecurityException("SUBSCRIBE without destination")
                val match = topicModelRegex.matchEntire(dest)
                    ?: throw SecurityException("Subscription to this destination is not allowed")
                val modelId = UUID.fromString(match.groupValues[1])
                val user = accessor.user as? UsernamePasswordAuthenticationToken
                    ?: throw SecurityException("Unauthenticated WebSocket session")

                val previous = SecurityContextHolder.getContext().authentication
                SecurityContextHolder.getContext().authentication = user
                try {
                    val model = modelsRepository.findById(modelId).orElse(null)
                        ?: throw SecurityException("Model not found")
                    if (!accessService.canViewModel(model)) {
                        throw SecurityException("Forbidden: cannot view model")
                    }
                } finally {
                    SecurityContextHolder.getContext().authentication = previous
                }
            }

            else -> return message
        }
        return message
    }
}
