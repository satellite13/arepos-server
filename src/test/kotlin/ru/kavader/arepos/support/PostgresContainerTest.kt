package ru.kavader.arepos.support

import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

abstract class PostgresContainerTest {

    companion object {
        private val postgres: PostgreSQLContainer<*> = PostgreSQLContainer(DockerImageName.parse("postgres:16.4"))
            .withDatabaseName("arepos")
            .withUsername("arepos")
            .withPassword("arepos")
            .also { it.start() }

        init {
            Runtime.getRuntime().addShutdownHook(Thread { postgres.stop() })
        }

        @JvmStatic
        @DynamicPropertySource
        fun registerDataSourceProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { postgres.username }
            registry.add("spring.datasource.password") { postgres.password }
            registry.add("spring.datasource.driver-class-name") { postgres.driverClassName }
            registry.add("arepos.files.storage") { "disabled" }
        }
    }
}

