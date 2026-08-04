package ru.kavader.arepos.service

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.dto.model.DiagramRequest
import ru.kavader.arepos.dto.model.DiagramResponse
import ru.kavader.arepos.dto.model.EnsureDiagramResponse
import ru.kavader.arepos.dto.system.ModelSyncChangeType
import ru.kavader.arepos.dto.system.ModelSyncEntityEvent
import ru.kavader.arepos.dto.system.ModelSyncEventType
import ru.kavader.arepos.mapper.ModelMapper
import ru.kavader.arepos.model.Diagrams
import ru.kavader.arepos.repository.DiagramsRepository
import ru.kavader.arepos.repository.ModelsRepository
import ru.kavader.arepos.repository.NodesRepository
import ru.kavader.arepos.repository.NotationsRepository
import ru.kavader.arepos.security.OwnerResolutionService
import ru.kavader.arepos.security.ResourceAccessService
import java.time.Instant

@Service
class DiagramEnsureService(
    private val diagramsRepository: DiagramsRepository,
    private val modelsRepository: ModelsRepository,
    private val nodesRepository: NodesRepository,
    private val notationsRepository: NotationsRepository,
    private val accessService: ResourceAccessService,
    private val ownerResolutionService: OwnerResolutionService,
    private val mdFileLinkValidator: MdFileLinkValidator,
    private val modelSyncBroadcaster: ModelSyncBroadcaster,
    private val modelMapper: ModelMapper,
    private val diagramLifecycleService: DiagramLifecycleService
) {

    @Transactional
    fun createDiagram(request: DiagramRequest): DiagramResponse =
        createInternal(request).let { modelMapper.toResponse(it) }

    @Transactional
    fun ensureDiagram(request: DiagramRequest): EnsureDiagramResponse {
        val model = modelsRepository.findById(request.modelId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Model ${request.modelId} not found")
        }
        accessService.requireCanEditModel(model)

        val existing = diagramsRepository
            .findByModelIdAndNameAndDeletedFalse(request.modelId, request.name)
            .maxWithOrNull(diagramLifecycleService::compareDiagramVersions)

        if (existing != null) {
            accessService.requireCanViewDiagram(existing)
            return EnsureDiagramResponse(diagram = modelMapper.toResponse(existing), created = false)
        }

        val createRequest = request.copy(
            attrs = request.attrs ?: DEFAULT_EMPTY_INSTANCES_ATTRS
        )
        val created = createInternal(createRequest)
        return EnsureDiagramResponse(diagram = modelMapper.toResponse(created), created = true)
    }

    private fun createInternal(request: DiagramRequest): Diagrams {
        val owner = ownerResolutionService.resolveOwnerForCreate(request.ownerId)
        val model = modelsRepository.findById(request.modelId)
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Model ${request.modelId} not found")
            }
        accessService.requireCanEditModel(model)
        val notation = notationsRepository.findById(request.notationId)
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Notation ${request.notationId} not found")
            }
        accessService.requireCanReferenceNotationForModelDiagram(notation, model)
        val node = request.nodeId?.let {
            nodesRepository.findById(it).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Node $it not found")
            }
        }?.also { newNode ->
            accessService.requireCanEditNode(newNode)
        }
        if (diagramsRepository.existsByModelAndNameAndVersion(model, request.name, request.version)) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "Diagram with model '${request.modelId}', name '${request.name}' and version '${request.version}' already exists"
            )
        }

        mdFileLinkValidator.validate(request.attrs)
        val now = Instant.now()
        val saved = diagramsRepository.save(
            Diagrams(
                name = request.name,
                createdAt = now,
                updatedAt = now,
                attrs = request.attrs,
                version = request.version,
                owner = owner,
                deleted = false,
                model = model,
                notation = notation,
                node = node
            )
        )
        modelSyncBroadcaster.broadcastModelChanged(
            requireNotNull(model.id),
            ModelSyncChangeType.DIAGRAM_CREATE.wireValue,
            listOf(
                ModelSyncEntityEvent(
                    ModelSyncEventType.DIAGRAM_CREATED.wireValue,
                    ModelSyncEventType.DIAGRAM_CREATED.entity,
                    requireNotNull(saved.id)
                )
            )
        )
        return saved
    }

    companion object {
        const val DEFAULT_EMPTY_INSTANCES_ATTRS: String =
            """{"instances":{"nodes":[],"edges":[]}}"""
    }
}
