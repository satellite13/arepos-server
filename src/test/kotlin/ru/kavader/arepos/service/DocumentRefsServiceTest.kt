package ru.kavader.arepos.service

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.dto.document.DocumentItem
import ru.kavader.arepos.dto.document.RegisterDocumentRefRequest
import ru.kavader.arepos.model.DocumentRefs
import ru.kavader.arepos.model.Files
import ru.kavader.arepos.model.LinkTypes
import ru.kavader.arepos.model.Models
import ru.kavader.arepos.model.NodeTypes
import ru.kavader.arepos.model.Users
import ru.kavader.arepos.repository.ComponentsRepository
import ru.kavader.arepos.repository.DiagramsRepository
import ru.kavader.arepos.repository.DocumentRefsRepository
import ru.kavader.arepos.repository.FilesRepository
import ru.kavader.arepos.repository.LinkTypesRepository
import ru.kavader.arepos.repository.ModelsRepository
import ru.kavader.arepos.repository.NodeShapesRepository
import ru.kavader.arepos.repository.NodeTypesRepository
import ru.kavader.arepos.repository.NodesRepository
import ru.kavader.arepos.repository.NotationsRepository
import ru.kavader.arepos.repository.RelationsRepository
import ru.kavader.arepos.repository.UsersRepository
import ru.kavader.arepos.security.ResourceAccessService
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@ExtendWith(MockitoExtension::class)
class DocumentRefsServiceTest {

    @Mock
    lateinit var documentRefsRepository: DocumentRefsRepository

    @Mock
    lateinit var filesRepository: FilesRepository

    @Mock
    lateinit var usersRepository: UsersRepository

    @Mock
    lateinit var modelsRepository: ModelsRepository

    @Mock
    lateinit var nodesRepository: NodesRepository

    @Mock
    lateinit var notationsRepository: NotationsRepository

    @Mock
    lateinit var componentsRepository: ComponentsRepository

    @Mock
    lateinit var nodeTypesRepository: NodeTypesRepository

    @Mock
    lateinit var linkTypesRepository: LinkTypesRepository

    @Mock
    lateinit var diagramsRepository: DiagramsRepository

    @Mock
    lateinit var relationsRepository: RelationsRepository

    @Mock
    lateinit var nodeShapesRepository: NodeShapesRepository

    @Mock
    lateinit var accessService: ResourceAccessService

    private lateinit var service: DocumentRefsService

    @BeforeEach
    fun setUp() {
        service = DocumentRefsService(
            documentRefsRepository,
            filesRepository,
            usersRepository,
            modelsRepository,
            nodesRepository,
            notationsRepository,
            componentsRepository,
            nodeTypesRepository,
            linkTypesRepository,
            diagramsRepository,
            relationsRepository,
            nodeShapesRepository,
            accessService
        )
    }

    @Test
    fun `register ref rejects request without context`() {
        val user = user()
        val file = file(user)
        stubCurrentUserAndFile(user, file)

        val exception = assertFailsWith<ResponseStatusException> {
            service.registerRef(RegisterDocumentRefRequest(fileId = file.id))
        }

        assertEquals(HttpStatus.BAD_REQUEST, exception.statusCode)
        assertEquals("At least one context id must be set", exception.reason)
    }

    @Test
    fun `register ref saves model reference and returns document item`() {
        val user = user()
        val file = file(user)
        val model = Models(id = UUID.randomUUID(), name = "Architecture", version = "1.0.0", owner = user)
        stubCurrentUserAndFile(user, file)
        `when`(modelsRepository.findById(model.id!!)).thenReturn(Optional.of(model))

        val result = service.registerRef(RegisterDocumentRefRequest(fileId = file.id, modelId = model.id))

        assertEquals(DocumentItem(fileId = file.id, label = file.filename), result)
        verify(accessService).requireCanEditModel(model)
        val captor = ArgumentCaptor.forClass(DocumentRefs::class.java)
        verify(documentRefsRepository).save(captor.capture())
        assertEquals(file, captor.value.file)
        assertEquals(user, captor.value.createdBy)
        assertEquals(model, captor.value.model)
    }

    @Test
    fun `register ref returns existing node type reference without saving duplicate`() {
        val user = user()
        val file = file(user)
        val nodeType = NodeTypes(id = UUID.randomUUID(), name = "Service", owner = user)
        val existing = DocumentRefs(file = file, createdBy = user, nodeType = nodeType)
        stubCurrentUserAndFile(user, file)
        `when`(nodeTypesRepository.findById(nodeType.id!!)).thenReturn(Optional.of(nodeType))
        `when`(documentRefsRepository.findFirstByFileIdAndNodeTypeId(file.id, nodeType.id!!))
            .thenReturn(Optional.of(existing))

        val result = service.registerRef(RegisterDocumentRefRequest(fileId = file.id, nodeTypeId = nodeType.id))

        assertEquals(DocumentItem(fileId = file.id, label = file.filename), result)
        verify(documentRefsRepository, never()).save(org.mockito.ArgumentMatchers.any(DocumentRefs::class.java))
    }

    @Test
    fun `register ref returns existing link type reference without saving duplicate`() {
        val user = user()
        val file = file(user)
        val linkType = LinkTypes(id = UUID.randomUUID(), name = "Depends on", owner = user)
        val existing = DocumentRefs(file = file, createdBy = user, linkType = linkType)
        stubCurrentUserAndFile(user, file)
        `when`(linkTypesRepository.findById(linkType.id!!)).thenReturn(Optional.of(linkType))
        `when`(documentRefsRepository.findFirstByFileIdAndLinkTypeId(file.id, linkType.id!!))
            .thenReturn(Optional.of(existing))

        val result = service.registerRef(RegisterDocumentRefRequest(fileId = file.id, linkTypeId = linkType.id))

        assertEquals(DocumentItem(fileId = file.id, label = file.filename), result)
        verify(documentRefsRepository, never()).save(org.mockito.ArgumentMatchers.any(DocumentRefs::class.java))
    }

    @Test
    fun `list for select removes duplicate file references for context`() {
        val user = user()
        val file = file(user)
        val modelId = UUID.randomUUID()
        val refs = listOf(
            DocumentRefs(file = file, createdBy = user),
            DocumentRefs(file = file, createdBy = user)
        )
        `when`(accessService.currentUserId()).thenReturn(user.id)
        `when`(
            documentRefsRepository.findByFilters(
                user.id!!,
                modelId,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
            )
        ).thenReturn(refs)

        val result = service.listForSelect(modelId = modelId)

        assertEquals(listOf(DocumentItem(fileId = file.id, label = file.filename)), result)
    }

    @Test
    fun `require can modify markdown checks every linked entity permission and reports references`() {
        val user = user()
        val file = file(user)
        val model = Models(id = UUID.randomUUID(), name = "Architecture", version = "1.0.0", owner = user)
        val nodeType = NodeTypes(id = UUID.randomUUID(), name = "Service", owner = user)
        val ref = DocumentRefs(file = file, createdBy = user, model = model, nodeType = nodeType)
        `when`(documentRefsRepository.findAllByFileId(file.id)).thenReturn(listOf(ref))

        assertEquals<Any>(true, service.requireCanModifyMarkdownForLinkedEntities(file.id))

        verify(accessService).requireCanEditModel(model)
        verify(accessService).requireCanEditNodeType(nodeType)
    }

    @Test
    fun `require can modify markdown reports no references`() {
        val user = user()
        val file = file(user)
        `when`(documentRefsRepository.findAllByFileId(file.id)).thenReturn(emptyList())

        assertEquals<Any>(false, service.requireCanModifyMarkdownForLinkedEntities(file.id))
    }

    private fun user(): Users = Users(id = UUID.randomUUID(), email = "document@test.com")

    private fun file(user: Users): Files = Files(
        id = UUID.randomUUID(),
        owner = user,
        filename = "architecture.md",
        contentType = "text/markdown",
        size = 42,
        objectKey = "documents/architecture.md"
    )

    private fun stubCurrentUserAndFile(user: Users, file: Files) {
        `when`(accessService.currentUserId()).thenReturn(user.id)
        `when`(usersRepository.findById(user.id!!)).thenReturn(Optional.of(user))
        `when`(filesRepository.findById(file.id)).thenReturn(Optional.of(file))
    }
}
