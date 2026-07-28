package ru.kavader.arepos.service

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import ru.kavader.arepos.model.DocumentRefs
import ru.kavader.arepos.model.Files
import ru.kavader.arepos.repository.DocumentRefsRepository
import ru.kavader.arepos.repository.FilesRepository
import ru.kavader.arepos.repository.RepositoryTestBase
import java.time.Instant
import java.util.UUID
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@SpringBootTest
class ModelLifecycleServiceTest : RepositoryTestBase() {

    @Autowired
    lateinit var modelLifecycleService: ModelLifecycleService

    @Autowired
    lateinit var filesRepository: FilesRepository

    @Autowired
    lateinit var documentRefsRepository: DocumentRefsRepository

    @Test
    fun `permanent delete removes model that has wiki document_refs`() {
        val owner = persistUser()
        val model = persistModel(owner = owner, name = "wiki-model")
        model.deleted = true
        modelsRepository.save(model)

        val file = filesRepository.save(
            Files(
                id = UUID.randomUUID(),
                owner = owner,
                filename = "doc.md",
                contentType = "text/markdown",
                size = 12,
                objectKey = "documents/${owner.id}/doc.md",
                createdAt = Instant.now()
            )
        )
        documentRefsRepository.save(
            DocumentRefs(
                file = file,
                createdBy = owner,
                createdAt = Instant.now(),
                model = model
            )
        )
        val node = persistNode(model = model, owner = owner)
        documentRefsRepository.save(
            DocumentRefs(
                file = file,
                createdBy = owner,
                createdAt = Instant.now(),
                model = model,
                node = node
            )
        )

        val modelId = requireNotNull(model.id)
        assertTrue(documentRefsRepository.findAllByModelId(modelId).isNotEmpty())

        modelLifecycleService.permanentDeleteModel(model)

        assertFalse(modelsRepository.findById(modelId).isPresent)
        assertTrue(documentRefsRepository.findAllByModelId(modelId).isEmpty())
    }
}
