package ru.kavader.arepos.controller

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.MockMvc
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import ru.kavader.arepos.model.Components
import ru.kavader.arepos.model.Diagrams
import ru.kavader.arepos.model.Models
import ru.kavader.arepos.model.NodeTypes
import ru.kavader.arepos.model.Nodes
import ru.kavader.arepos.model.Notations
import ru.kavader.arepos.model.Role
import ru.kavader.arepos.model.Users
import ru.kavader.arepos.repository.ComponentsRepository
import ru.kavader.arepos.repository.DiagramsRepository
import ru.kavader.arepos.repository.ModelsRepository
import ru.kavader.arepos.repository.NodeTypesRepository
import ru.kavader.arepos.repository.NodesRepository
import ru.kavader.arepos.repository.NotationsRepository
import ru.kavader.arepos.repository.UsersRepository
import ru.kavader.arepos.service.FileStorageService
import java.time.Instant
import java.util.UUID
import java.util.zip.ZipInputStream
import kotlin.test.assertTrue

@SpringBootTest
@AutoConfigureMockMvc
@Import(ModelPackageControllerTest.FileStorageTestConfiguration::class)
class ModelPackageControllerTest : ControllerIntegrationTest() {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var usersRepository: UsersRepository

    @Autowired
    lateinit var modelsRepository: ModelsRepository

    @Autowired
    lateinit var notationsRepository: NotationsRepository

    @Autowired
    lateinit var nodeTypesRepository: NodeTypesRepository

    @Autowired
    lateinit var componentsRepository: ComponentsRepository

    @Autowired
    lateinit var nodesRepository: NodesRepository

    @Autowired
    lateinit var diagramsRepository: DiagramsRepository

    @Test
    fun `export returns zip attachment with package entries`() {
        val owner = usersRepository.save(
            Users(email = "model-package-controller@test.com", role = Role.USER, createdAt = Instant.now())
        )
        val notation = notationsRepository.save(
            Notations(
                owner = owner,
                name = "Controller Package Notation",
                version = "1.0.0",
                createdAt = Instant.now(),
                deleted = false
            )
        )
        val nodeType = nodeTypesRepository.save(
            NodeTypes(name = "Controller Package Type", owner = owner, createdAt = Instant.now())
        )
        componentsRepository.save(
            Components(
                name = "Controller Package Component",
                version = "1.0.0",
                notation = notation,
                owner = owner,
                nodeType = nodeType,
                createdAt = Instant.now()
            )
        )
        val model = modelsRepository.save(
            Models(
                name = "Controller Package Model",
                version = "1.0.0",
                owner = owner,
                createdAt = Instant.now(),
                deleted = false
            )
        )
        val node = nodesRepository.save(
            Nodes(
                stableId = UUID.randomUUID(),
                name = "Node",
                createdAt = Instant.now(),
                model = model,
                owner = owner,
                nodeType = nodeType
            )
        )
        diagramsRepository.save(
            Diagrams(
                name = "Diagram",
                version = "1.0.0",
                createdAt = Instant.now(),
                owner = owner,
                model = model,
                notation = notation,
                node = node,
                deleted = false
            )
        )

        val result = mockMvc.perform(
            get("/api/v1/models/${model.id}/package")
                .withAuth(owner.id!!, Role.USER)
        )
            .andExpect(status().isOk)
            .andExpect(header().string("Content-Disposition", "attachment; filename=\"model-package.zip\""))
            .andExpect(header().string("Content-Type", "application/zip"))
            .andReturn()

        val names = linkedSetOf<String>()
        ZipInputStream(result.response.contentAsByteArray.inputStream()).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                names.add(entry.name)
                zis.closeEntry()
            }
        }
        assertTrue(names.contains("manifest.json"))
        assertTrue(names.contains("model.json"))
        assertTrue(names.contains("notations/${notation.id}.json"))
    }

    @Test
    fun `export returns 403 when notation is not readable`() {
        val modelOwner = usersRepository.save(
            Users(email = "model-package-reader@test.com", role = Role.USER, createdAt = Instant.now())
        )
        val notationOwner = usersRepository.save(
            Users(email = "model-package-foreign-notation@test.com", role = Role.USER, createdAt = Instant.now())
        )
        val notation = notationsRepository.save(
            Notations(
                owner = notationOwner,
                name = "Foreign Package Notation",
                version = "1.0.0",
                createdAt = Instant.now(),
                deleted = false
            )
        )
        val nodeType = nodeTypesRepository.save(
            NodeTypes(name = "Foreign Package Type", owner = notationOwner, createdAt = Instant.now())
        )
        componentsRepository.save(
            Components(
                name = "Foreign Package Component",
                version = "1.0.0",
                notation = notation,
                owner = notationOwner,
                nodeType = nodeType,
                createdAt = Instant.now()
            )
        )
        val model = modelsRepository.save(
            Models(
                name = "Readable Model Foreign Notation",
                version = "1.0.0",
                owner = modelOwner,
                createdAt = Instant.now(),
                deleted = false
            )
        )
        nodesRepository.save(
            Nodes(
                stableId = UUID.randomUUID(),
                name = "Node",
                createdAt = Instant.now(),
                model = model,
                owner = modelOwner,
                nodeType = nodeType
            )
        )
        diagramsRepository.save(
            Diagrams(
                name = "Diagram",
                version = "1.0.0",
                createdAt = Instant.now(),
                owner = modelOwner,
                model = model,
                notation = notation,
                deleted = false
            )
        )

        mockMvc.perform(
            get("/api/v1/models/${model.id}/package")
                .withAuth(modelOwner.id!!, Role.USER)
        )
            .andExpect(status().isForbidden)
    }

    @Test
    fun `import returns 201 for valid package zip`() {
        val owner = usersRepository.save(
            Users(email = "model-package-import-controller@test.com", role = Role.USER, createdAt = Instant.now())
        )
        val notation = notationsRepository.save(
            Notations(
                owner = owner,
                name = "Import Controller Notation",
                version = "1.0.0",
                createdAt = Instant.now(),
                deleted = false
            )
        )
        val nodeType = nodeTypesRepository.save(
            NodeTypes(name = "Import Controller Type", owner = owner, createdAt = Instant.now())
        )
        componentsRepository.save(
            Components(
                name = "Import Controller Component",
                version = "1.0.0",
                notation = notation,
                owner = owner,
                nodeType = nodeType,
                createdAt = Instant.now()
            )
        )
        val model = modelsRepository.save(
            Models(
                name = "Import Controller Model",
                version = "1.0.0",
                owner = owner,
                createdAt = Instant.now(),
                deleted = false
            )
        )
        val node = nodesRepository.save(
            Nodes(
                stableId = UUID.randomUUID(),
                name = "Node",
                createdAt = Instant.now(),
                model = model,
                owner = owner,
                nodeType = nodeType
            )
        )
        diagramsRepository.save(
            Diagrams(
                name = "Diagram",
                version = "1.0.0",
                createdAt = Instant.now(),
                owner = owner,
                model = model,
                notation = notation,
                node = node,
                deleted = false
            )
        )

        val exportResult = mockMvc.perform(
            get("/api/v1/models/${model.id}/package")
                .withAuth(owner.id!!, Role.USER)
        )
            .andExpect(status().isOk)
            .andReturn()

        model.name = "Import Controller Model Archived"
        modelsRepository.save(model)
        notation.name = "Import Controller Notation Archived"
        notationsRepository.save(notation)

        val upload = MockMultipartFile(
            "file",
            "model-package.zip",
            "application/zip",
            exportResult.response.contentAsByteArray
        )

        mockMvc.perform(
            multipart("/api/v1/models/package")
                .file(upload)
                .withAuth(owner.id!!, Role.USER)
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.modelName").value("Import Controller Model"))
            .andExpect(jsonPath("$.modelVersion").value("1.0.0"))
    }

    @Test
    fun `import returns 400 for invalid zip upload`() {
        val owner = usersRepository.save(
            Users(email = "model-package-import-bad-zip@test.com", role = Role.USER, createdAt = Instant.now())
        )
        val upload = MockMultipartFile(
            "file",
            "bad.zip",
            MediaType.APPLICATION_OCTET_STREAM_VALUE,
            "not-a-zip".toByteArray()
        )

        mockMvc.perform(
            multipart("/api/v1/models/package")
                .file(upload)
                .withAuth(owner.id!!, Role.USER)
        )
            .andExpect(status().isBadRequest)
    }

    @TestConfiguration(proxyBeanMethods = false)
    class FileStorageTestConfiguration {
        @Bean
        fun fileStorageService(): FileStorageService =
            org.mockito.Mockito.mock(FileStorageService::class.java)
    }
}
