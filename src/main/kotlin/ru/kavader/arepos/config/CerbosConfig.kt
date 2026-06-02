package ru.kavader.arepos.config

import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(CerbosProperties::class)
class CerbosConfig(
    private val cerbosProperties: CerbosProperties
) {
    companion object {
        private val log = LoggerFactory.getLogger(CerbosConfig::class.java)
    }

    init {
        log.info(
            "Cerbos authz config loaded: endpoint={}, requestTimeout={}, bundleVersion={}, circuitFailureThreshold={}, circuitOpenDuration={}",
            cerbosProperties.endpoint,
            cerbosProperties.requestTimeout,
            cerbosProperties.bundleVersion,
            cerbosProperties.circuitFailureThreshold,
            cerbosProperties.circuitOpenDuration
        )
    }
}
