package ru.kavader.arepos.service

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.dto.document.DocumentItem
import ru.kavader.arepos.dto.document.RegisterDocumentRefRequest
import ru.kavader.arepos.model.DocumentRefs
import ru.kavader.arepos.repository.*
import ru.kavader.arepos.security.ResourceAccessService
import java.time.Instant
import java.util.*

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
    private val diagramsRepository: DiagramsRepository,
    private val relationsRepository: RelationsRepository,
    private val nodeShapesRepository: NodeShapesRepository,
    private val accessService: ResourceAccessService
) {

    /**
     * Если markdown-файл привязан к сущностям через document_refs, правка контента
     * требует тех же прав, что и регистрация ссылки (иначе обход через PUT /files/.../markdown).
     *
     * @return true, если для файла есть хотя бы одна ссылка. Все ссылки проверяются
     * до возврата, поэтому true означает, что пользователь может редактировать каждую
     * связанную сущность.
     */
    @Transactional(readOnly = true)
    fun requireCanModifyMarkdownForLinkedEntities(fileId: UUID): Boolean {
        val refs = documentRefsRepository.findAllByFileId(fileId)
        for (ref in refs) {
            ref.model?.let { accessService.requireCanEditModel(it) }
            ref.node?.let { accessService.requireCanEditNode(it) }
            ref.diagram?.let { accessService.requireCanEditDiagram(it) }
            ref.notation?.let { accessService.requireCanEditNotation(it) }
            ref.component?.let { accessService.requireCanEditComponent(it) }
            ref.relation?.let { accessService.requireCanEditRelation(it) }
            ref.nodeType?.let { accessService.requireCanEditNodeType(it) }
            ref.linkType?.let { accessService.requireCanEditLinkType(it) }
            ref.nodeShape?.let { accessService.requireCanEditNodeShape(it) }
        }
        return refs.isNotEmpty()
    }

    @Transactional
    fun registerRef(request: RegisterDocumentRefRequest): DocumentItem {
        val userId = accessService.currentUserId()
        val user = usersRepository.findById(userId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "User not found") }
        val file = filesRepository.findById(request.fileId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "File not found") }
        accessService.requireCanViewFile(file)

        val hasContext = request.modelId != null || request.notationId != null ||
                request.componentId != null || request.nodeId != null ||
                request.nodeTypeId != null || request.linkTypeId != null ||
                request.diagramId != null || request.relationId != null || request.nodeShapeId != null
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
        val diagram = request.diagramId?.let {
            diagramsRepository.findById(it).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Diagram not found")
            }.also { d -> accessService.requireCanEditDiagram(d) }
        }
        val relation = request.relationId?.let {
            relationsRepository.findById(it).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Relation not found")
            }.also { r -> accessService.requireCanEditRelation(r) }
        }
        val nodeShape = request.nodeShapeId?.let {
            nodeShapesRepository.findById(it).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Node shape not found")
            }.also { s -> accessService.requireCanEditNodeShape(s) }
        }

        request.nodeTypeId?.let { nodeTypeId ->
            documentRefsRepository.findFirstByFileIdAndNodeTypeId(request.fileId, nodeTypeId)
                .orElse(null)?.let { existing -> return refToDocumentItem(existing) }
        }
        request.linkTypeId?.let { linkTypeId ->
            documentRefsRepository.findFirstByFileIdAndLinkTypeId(request.fileId, linkTypeId)
                .orElse(null)?.let { existing -> return refToDocumentItem(existing) }
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
            node = node,
            diagram = diagram,
            relation = relation,
            nodeShape = nodeShape
        )
        documentRefsRepository.save(ref)
        return DocumentItem(fileId = file.id, label = file.filename)
    }

    private fun refToDocumentItem(ref: DocumentRefs): DocumentItem =
        DocumentItem(fileId = ref.file.id!!, label = ref.file.filename)

    private fun entityTypeAndName(ref: DocumentRefs): Pair<String, String?> {
        return when {
            ref.nodeType != null -> "nodeType" to ref.nodeType!!.name
            ref.linkType != null -> "linkType" to ref.linkType!!.name
            ref.nodeShape != null -> "nodeShape" to ref.nodeShape!!.name
            ref.notation != null && ref.component == null && ref.relation == null -> "notation" to ref.notation!!.name
            ref.component != null -> "component" to ref.component!!.name
            ref.relation != null -> "relation" to ref.relation!!.name
            ref.model != null && ref.node == null && ref.diagram == null -> "model" to ref.model!!.name
            ref.node != null -> "node" to ref.node!!.name
            ref.diagram != null -> "diagram" to ref.diagram!!.name
            else -> "unknown" to null
        }
    }

    private fun parentName(ref: DocumentRefs): String? {
        return when {
            ref.component != null -> ref.notation?.name
            ref.relation != null -> ref.notation?.name
            ref.node != null -> ref.model?.name
            ref.diagram != null -> ref.model?.name
            else -> null
        }
    }

    fun listForSelect(
        modelId: UUID? = null,
        notationId: UUID? = null,
        componentId: UUID? = null,
        nodeId: UUID? = null,
        nodeTypeId: UUID? = null,
        linkTypeId: UUID? = null,
        diagramId: UUID? = null,
        relationId: UUID? = null,
        nodeShapeId: UUID? = null
    ): List<DocumentItem> {
        val userId = accessService.currentUserId()
        val refs = documentRefsRepository.findByFilters(
            userId = userId,
            modelId = modelId,
            notationId = notationId,
            componentId = componentId,
            nodeId = nodeId,
            nodeTypeId = nodeTypeId,
            linkTypeId = linkTypeId,
            diagramId = diagramId,
            relationId = relationId,
            nodeShapeId = nodeShapeId
        )
        val withContext = modelId == null && notationId == null && componentId == null &&
                nodeId == null && nodeTypeId == null && linkTypeId == null &&
                diagramId == null && relationId == null && nodeShapeId == null
        if (!withContext) {
            val seen = mutableSetOf<UUID>()
            return refs.mapNotNull { ref ->
                val fid = ref.file.id!!
                if (!seen.add(fid)) return@mapNotNull null
                DocumentItem(fileId = fid, label = ref.file.filename)
            }
        }
        val byFile = refs.groupBy { it.file.id!! }
        return byFile.mapNotNull { (_, fileRefs) ->
            val ref = fileRefs.maxWithOrNull(
                compareBy(
                    { r -> val (t, _) = entityTypeAndName(r); if (t != "unknown") 1 else 0 },
                    { r -> r.createdAt?.toEpochMilli() ?: 0L }
                )
            ) ?: return@mapNotNull null
            val (entityType, entityName) = entityTypeAndName(ref)
            val entityId = when (entityType) {
                "nodeType" -> ref.nodeType?.id
                "linkType" -> ref.linkType?.id
                "nodeShape" -> ref.nodeShape?.id
                "notation" -> ref.notation?.id
                "component" -> ref.component?.id
                "relation" -> ref.relation?.id
                "model" -> ref.model?.id
                "node" -> ref.node?.id
                "diagram" -> ref.diagram?.id
                else -> null
            }
            DocumentItem(
                fileId = ref.file.id!!,
                label = ref.file.filename,
                entityType = entityType,
                entityId = entityId,
                entityName = entityName,
                parentName = parentName(ref)
            )
        }
    }
}
