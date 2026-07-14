package ru.kavader.arepos.controller

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import ru.kavader.arepos.dto.document.RegisterDocumentRefRequest
import ru.kavader.arepos.model.Files
import ru.kavader.arepos.model.Models
import ru.kavader.arepos.model.Role
import ru.kavader.arepos.model.Users
import ru.kavader.arepos.repository.FilesRepository
import ru.kavader.arepos.repository.ModelsRepository
import ru.kavader.arepos.repository.UsersRepository
import java.time.Instant
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
class DocumentsControllerTest : ControllerIntegrationTest() {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Autowired
    lateinit var usersRepository: UsersRepository

    @Autowired
    lateinit var modelsRepository: ModelsRepository

    @Autowired
    lateinit var filesRepository: FilesRepository

    @Test
    fun `owner registers model document reference and receives file id and label`() {
        val owner = persistUser("document-owner@test.com")
        val model = persistModel(owner, "Architecture")
        val file = persistFile(owner, "architecture.md")
        val request = RegisterDocumentRefRequest(fileId = file.id, modelId = model.id)

        mockMvc.perform(
            post("/api/v1/documents")
                .withAuth(owner.id!!, Role.USER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.fileId").value(file.id.toString()))
            .andExpect(jsonPath("$.label").value(file.filename))
    }

    @Test
    fun `registering document reference without context ids returns bad request`() {
        val owner = persistUser("document-no-context@test.com")
        val file = persistFile(owner, "unlinked.md")
        val request = RegisterDocumentRefRequest(fileId = file.id)

        mockMvc.perform(
            post("/api/v1/documents")
                .withAuth(owner.id!!, Role.USER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `outsider cannot register document reference for foreign model`() {
        val owner = persistUser("document-model-owner@test.com")
        val outsider = persistUser("document-outsider@test.com")
        val model = persistModel(owner, "Private architecture")
        val outsiderFile = persistFile(outsider, "outsider.md")
        val request = RegisterDocumentRefRequest(fileId = outsiderFile.id, modelId = model.id)

        mockMvc.perform(
            post("/api/v1/documents")
                .withAuth(outsider.id!!, Role.USER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        ).andExpect(status().isForbidden)
    }

    @Test
    fun `model filter returns owners document references`() {
        val owner = persistUser("document-list-owner@test.com")
        val model = persistModel(owner, "Listed architecture")
        val file = persistFile(owner, "listed.md")
        registerModelRef(owner, file, model)

        mockMvc.perform(
            get("/api/v1/documents")
                .param("modelId", model.id.toString())
                .withAuth(owner.id!!, Role.USER)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].fileId").value(file.id.toString()))
            .andExpect(jsonPath("$[0].label").value(file.filename))
    }

    @Test
    fun `unfiltered document list includes entity metadata`() {
        val owner = persistUser("document-metadata-owner@test.com")
        val model = persistModel(owner, "Metadata architecture")
        val file = persistFile(owner, "metadata.md")
        registerModelRef(owner, file, model)

        mockMvc.perform(
            get("/api/v1/documents")
                .withAuth(owner.id!!, Role.USER)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].fileId").value(file.id.toString()))
            .andExpect(jsonPath("$[0].entityType").value("model"))
            .andExpect(jsonPath("$[0].entityId").value(model.id.toString()))
            .andExpect(jsonPath("$[0].entityName").value(model.name))
    }

    private fun registerModelRef(owner: Users, file: Files, model: Models) {
        val request = RegisterDocumentRefRequest(fileId = file.id, modelId = model.id)

        mockMvc.perform(
            post("/api/v1/documents")
                .withAuth(owner.id!!, Role.USER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        ).andExpect(status().isOk)
    }

    private fun persistUser(email: String): Users =
        usersRepository.save(
            Users(
                email = email,
                role = Role.USER,
                createdAt = Instant.now()
            )
        )

    private fun persistModel(owner: Users, name: String): Models =
        modelsRepository.save(
            Models(
                name = name,
                version = "1.0.0",
                owner = owner,
                createdAt = Instant.now()
            )
        )

    private fun persistFile(owner: Users, filename: String): Files =
        filesRepository.save(
            Files(
                id = UUID.randomUUID(),
                owner = owner,
                filename = filename,
                contentType = "text/markdown",
                size = 42,
                objectKey = "documents/${owner.id}/$filename",
                createdAt = Instant.now()
            )
        )
}
