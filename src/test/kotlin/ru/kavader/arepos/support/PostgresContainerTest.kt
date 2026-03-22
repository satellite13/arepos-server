package ru.kavader.arepos.support

import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
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
        private val postgres: PostgreSQLContainer<*> = PostgreSQLContainer(DockerImageName.parse("postgres:16.4"))
            .withDatabaseName("arepos")
            .withUsername("arepos")
            .withPassword("arepos")
            .also { it.start() }

        private val policyDir: Path = Path.of("").toAbsolutePath().normalize().resolve("authz/cerbos/policies")

        private val cerbos: GenericContainer<*> =
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

        init {
            Runtime.getRuntime().addShutdownHook(
                Thread {
                    cerbos.stop()
                    postgres.stop()
                }
            )
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

        @JvmStatic
        @DynamicPropertySource
        fun registerCerbosProperties(registry: DynamicPropertyRegistry) {
            registry.add("arepos.authz.cerbos.endpoint") {
                "http://${cerbos.host}:${cerbos.getMappedPort(3592)}"
            }
            registry.add("arepos.authz.cerbos.request-timeout") { "5s" }
        }
    }
}

