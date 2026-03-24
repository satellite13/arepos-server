package ru.kavader.arepos.websocket

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.messaging.simp.config.ChannelRegistration
import org.springframework.messaging.simp.config.MessageBrokerRegistry
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker
import org.springframework.web.socket.config.annotation.StompEndpointRegistry
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer
import ru.kavader.arepos.config.AreposWebSocketProperties

@Configuration
@EnableWebSocketMessageBroker
@EnableConfigurationProperties(AreposWebSocketProperties::class)
class ModelWebSocketBrokerConfig(
    private val areposWebSocketProperties: AreposWebSocketProperties,
    private val jwtQueryTokenHandshakeInterceptor: JwtQueryTokenHandshakeInterceptor,
    private val modelSyncStompHandshakeHandler: ModelSyncStompHandshakeHandler,
    private val stompModelSubscribeAuthorizationInterceptor: StompModelSubscribeAuthorizationInterceptor
) : WebSocketMessageBrokerConfigurer {

    override fun configureMessageBroker(registry: MessageBrokerRegistry) {
        registry.enableSimpleBroker("/topic")
        registry.setApplicationDestinationPrefixes("/app")
    }

    override fun registerStompEndpoints(registry: StompEndpointRegistry) {
        registry.addEndpoint("/ws")
            .setHandshakeHandler(modelSyncStompHandshakeHandler)
            .addInterceptors(jwtQueryTokenHandshakeInterceptor)
            .setAllowedOriginPatterns(*areposWebSocketProperties.allowedOriginPatternArray())
    }

    override fun configureClientInboundChannel(registration: ChannelRegistration) {
        registration.interceptors(stompModelSubscribeAuthorizationInterceptor)
    }
}
