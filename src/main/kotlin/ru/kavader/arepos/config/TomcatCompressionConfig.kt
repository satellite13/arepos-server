package ru.kavader.arepos.config

import org.apache.catalina.connector.Connector
import org.apache.coyote.http11.AbstractHttp11Protocol
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.web.embedded.tomcat.TomcatConnectorCustomizer
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory
import org.springframework.boot.web.server.WebServerFactoryCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@ConditionalOnProperty(prefix = "server.compression", name = ["enabled"], havingValue = "true")
class TomcatCompressionConfig {

    /**
     * Sendfile bypasses Tomcat gzip; large JSON responses (e.g. OpenAPI) stay uncompressed without this.
     */
    @Bean
    fun disableTomcatSendfileForCompression(): WebServerFactoryCustomizer<TomcatServletWebServerFactory> =
        WebServerFactoryCustomizer { factory ->
            factory.addConnectorCustomizers(TomcatConnectorCustomizer(::disableSendfile))
        }

    private fun disableSendfile(connector: Connector) {
        val protocol = connector.protocolHandler
        if (protocol is AbstractHttp11Protocol<*>) {
            protocol.useSendfile = false
        }
    }
}
