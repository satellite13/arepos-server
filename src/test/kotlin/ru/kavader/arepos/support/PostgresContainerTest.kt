package ru.kavader.arepos.support

import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.BeforeAll
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.BindMode
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName
import org.testcontainers.utility.MountableFile
import java.nio.file.Files
import java.nio.file.Path

abstract class PostgresContainerTest {

    companion object {
        // Containers start lazily: on CI runners without Docker (e.g. GitVerse)
        // container-backed tests are skipped instead of failing.
        private val dockerAvailable: Boolean by lazy {
            runCatching { DockerClientFactory.instance().isDockerAvailable }.getOrDefault(false)
        }

        private val postgres: PostgreSQLContainer<*>? by lazy {
            PostgreSQLContainer(DockerImageName.parse("postgres:16.4"))
                .withDatabaseName("arepos")
                .withUsername("arepos")
                .withPassword("arepos")
                // Many Spring test contexts each open a Hikari pool; default PG max_connections=100
                // is exhausted when pool size stays at production default (30).
                .withCommand("postgres", "-c", "max_connections=300")
                .also { it.start() }
        }

        private val policyDir: Path = Path.of("").toAbsolutePath().normalize().resolve("authz/cerbos/policies")

        private val cerbos: GenericContainer<*>? by lazy {
            GenericContainer(DockerImageName.parse("ghcr.io/cerbos/cerbos:latest")).apply {
                require(Files.isDirectory(policyDir)) {
                    "Cerbos policies directory not found: $policyDir (cwd=${Path.of("").toAbsolutePath()})"
                }
                withExposedPorts(3592)
                withCopyFileToContainer(
                    MountableFile.forClasspathResource("cerbos-test/config.yaml"),
                    "/config/config.yaml"
                )
                withFileSystemBind(policyDir.toString(), "/policies", BindMode.READ_ONLY)
                withCommand("server", "--config=/config/config.yaml")
                waitingFor(
                    Wait.forHttp("/_cerbos/health")
                        .forStatusCode(200)
                        .forPort(3592)
                )
                start()
            }
        }

        @JvmStatic
        @BeforeAll
        fun skipWhenDockerUnavailable() {
            Assumptions.assumeTrue(
                dockerAvailable,
                "Docker unavailable — skipping container-backed tests"
            )
        }

        init {
            Runtime.getRuntime().addShutdownHook(
                Thread {
                    if (dockerAvailable) {
                        runCatching { cerbos?.stop() }
                        runCatching { postgres?.stop() }
                    }
                }
            )
        }

        @JvmStatic
        @DynamicPropertySource
        fun registerDataSourceProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") {
                requireNotNull(postgres) { "Docker unavailable" }.jdbcUrl
            }
            registry.add("spring.datasource.username") {
                requireNotNull(postgres) { "Docker unavailable" }.username
            }
            registry.add("spring.datasource.password") {
                requireNotNull(postgres) { "Docker unavailable" }.password
            }
            registry.add("spring.datasource.driver-class-name") {
                requireNotNull(postgres) { "Docker unavailable" }.driverClassName
            }
            registry.add("spring.datasource.hikari.maximum-pool-size") { "5" }
            registry.add("arepos.files.storage") { "disabled" }
        }

        @JvmStatic
        @DynamicPropertySource
        fun registerCerbosProperties(registry: DynamicPropertyRegistry) {
            registry.add("arepos.authz.cerbos.endpoint") {
                val cerbosContainer = requireNotNull(cerbos) { "Docker unavailable" }
                "http://${cerbosContainer.host}:${cerbosContainer.getMappedPort(3592)}"
            }
            registry.add("arepos.authz.cerbos.request-timeout") { "5s" }
        }
    }
}
