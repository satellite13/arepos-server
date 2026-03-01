package ru.kavader.arepos.service

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.model.DocumentRefs
import ru.kavader.arepos.model.Files
import ru.kavader.arepos.model.Users
import ru.kavader.arepos.repository.ComponentsRepository
import ru.kavader.arepos.repository.DocumentRefsRepository
import ru.kavader.arepos.repository.FilesRepository
import ru.kavader.arepos.repository.LinkTypesRepository
import ru.kavader.arepos.repository.ModelsRepository
import ru.kavader.arepos.repository.NodeTypesRepository
import ru.kavader.arepos.repository.NodesRepository
import ru.kavader.arepos.repository.NotationsRepository
import ru.kavader.arepos.repository.UsersRepository
import ru.kavader.arepos.security.ResourceAccessService
import java.time.Instant
import java.util.UUID

data class DocumentItem(
    val fileId: UUID,
    val label: String
)

data class RegisterDocumentRefRequest(
    val fileId: UUID,
    val modelId: UUID? = null,
    val notationId: UUID? = null,
    val componentId: UUID? = null,
    val nodeId: UUID? = null,
    val nodeTypeId: UUID? = null,
    val linkTypeId: UUID? = null
)

@Service
class DocumentRefsService(
    private val documentRefsRepository: DocumentRefsRepository,
    private val filesRepository: FilesRepository,
    private val usersRepository: UsersRepository,
    private val modelsRepository: ModelsRepository,
    private val nodesRepository: NodesRepository,
    private val notationsRepository: NotationsRepository,
    private val componentsRepository: ComponentsRepository,
    private val nodeTypesRepository: NodeTypesRepository,
    private val linkTypesRepository: LinkTypesRepository,
    private val accessService: ResourceAccessService
) {

    fun registerRef(request: RegisterDocumentRefRequest): DocumentItem {
        val userId = accessService.currentUserId()
        val user = usersRepository.findById(userId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "User not found") }
        val file = filesRepository.findById(request.fileId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "File not found") }
        accessService.requireCanViewFile(file)

        val hasContext = request.modelId != null || request.notationId != null ||
            request.componentId != null || request.nodeId != null ||
            request.nodeTypeId != null || request.linkTypeId != null
        if (!hasContext) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one context id must be set")
        }

        val model = request.modelId?.let {
            modelsRepository.findById(it).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Model not found")
            }.also { m -> accessService.requireCanEditModel(m) }
        }
        val node = request.nodeId?.let {
            nodesRepository.findById(it).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Node not found")
            }.also { n -> accessService.requireCanEditNode(n) }
        }
        val notation = request.notationId?.let {
            notationsRepository.findById(it).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Notation not found")
            }.also { n -> accessService.requireCanEditNotation(n) }
        }
        val component = request.componentId?.let {
            componentsRepository.findById(it).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Component not found")
            }.also { c -> accessService.requireCanEditComponent(c) }
        }
        val nodeType = request.nodeTypeId?.let {
            nodeTypesRepository.findById(it).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Node type not found")
            }.also { t -> accessService.requireCanEditNodeType(t) }
        }
        val linkType = request.linkTypeId?.let {
            linkTypesRepository.findById(it).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Link type not found")
            }.also { t -> accessService.requireCanEditLinkType(t) }
        }

        val ref = DocumentRefs(
            file = file,
            createdBy = user,
            createdAt = Instant.now(),
            nodeType = nodeType,
            linkType = linkType,
            notation = notation,
            component = component,
            model = model,
            node = node
        )
        documentRefsRepository.save(ref)
        return DocumentItem(fileId = file.id, label = file.filename)
    }

    fun listForSelect(
        modelId: UUID? = null,
        notationId: UUID? = null,
        componentId: UUID? = null,
        nodeId: UUID? = null,
        nodeTypeId: UUID? = null,
        linkTypeId: UUID? = null
    ): List<DocumentItem> {
        val userId = accessService.currentUserId()
        val refs = documentRefsRepository.findByFilters(
            userId = userId,
            modelId = modelId,
            notationId = notationId,
            componentId = componentId,
            nodeId = nodeId,
            nodeTypeId = nodeTypeId,
            linkTypeId = linkTypeId
        )
        val seen = mutableSetOf<UUID>()
        return refs.mapNotNull { ref ->
            val fid = ref.file.id!!
            if (seen.add(fid)) DocumentItem(fileId = fid, label = ref.file.filename) else null
        }
    }
}
