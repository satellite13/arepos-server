package ru.kavader.arepos.service

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.dto.model.EnsureLinkResponse
import ru.kavader.arepos.dto.model.LinkRequest
import ru.kavader.arepos.dto.model.LinkResponse
import ru.kavader.arepos.dto.system.ModelSyncChangeType
import ru.kavader.arepos.dto.system.ModelSyncEntityEvent
import ru.kavader.arepos.dto.system.ModelSyncEventType
import ru.kavader.arepos.mapper.ModelMapper
import ru.kavader.arepos.model.Links
import ru.kavader.arepos.repository.LinksRepository
import ru.kavader.arepos.repository.ModelsRepository
import ru.kavader.arepos.repository.NodesRepository
import ru.kavader.arepos.security.OwnerResolutionService
import ru.kavader.arepos.security.ResourceAccessService
import ru.kavader.arepos.security.TypeUsageAuthorization
import java.time.Instant
import java.util.UUID

@Service
class LinkEnsureService(
    private val linksRepository: LinksRepository,
    private val modelsRepository: ModelsRepository,
    private val nodesRepository: NodesRepository,
    private val accessService: ResourceAccessService,
    private val ownerResolutionService: OwnerResolutionService,
    private val mdFileLinkValidator: MdFileLinkValidator,
    private val modelSyncBroadcaster: ModelSyncBroadcaster,
    private val typeUsageAuthorization: TypeUsageAuthorization,
    private val modelMapper: ModelMapper,
    private val notationBindingService: NotationBindingService
) {

    @Transactional
    fun createLink(request: LinkRequest): LinkResponse =
        createInternal(request).let { modelMapper.toResponse(it) }

    @Transactional
    fun ensureLink(request: LinkRequest): EnsureLinkResponse {
        val model = modelsRepository.findById(request.modelId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Model ${request.modelId} not found")
        }
        accessService.requireCanEditModel(model)
        val binding = notationBindingService.resolveLinkCreate(
            linkTypeId = request.linkTypeId,
            notationId = request.notationId,
            relationId = request.relationId,
            relationName = request.relationName,
            attrs = request.attrs
        )
        val existing = linksRepository.findByModel_IdAndSource_IdAndTarget_IdAndLinkType_Id(
            modelId = request.modelId,
            sourceId = request.sourceId,
            targetId = request.targetId,
            linkTypeId = binding.linkType.id!!
        ).firstOrNull()
        if (existing != null) {
            accessService.requireCanViewLink(existing)
            return EnsureLinkResponse(link = modelMapper.toResponse(existing), created = false)
        }
        val created = createInternal(request, preResolved = binding)
        return EnsureLinkResponse(link = modelMapper.toResponse(created), created = true)
    }

    private fun createInternal(
        request: LinkRequest,
        preResolved: ResolvedLinkBinding? = null
    ): Links {
        val owner = ownerResolutionService.resolveOwnerForCreate(request.ownerId)
        val model = modelsRepository.findById(request.modelId)
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Model ${request.modelId} not found")
            }
        accessService.requireCanEditModel(model)
        val source = nodesRepository.findById(request.sourceId)
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Source node ${request.sourceId} not found")
            }
        accessService.requireCanEditNode(source)
        val target = nodesRepository.findById(request.targetId)
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Target node ${request.targetId} not found")
            }
        accessService.requireCanEditNode(target)
        val binding = preResolved ?: notationBindingService.resolveLinkCreate(
            linkTypeId = request.linkTypeId,
            notationId = request.notationId,
            relationId = request.relationId,
            relationName = request.relationName,
            attrs = request.attrs
        )
        val linkType = binding.linkType
        typeUsageAuthorization.requireCanUseLinkTypeForModel(linkType, model)
        mdFileLinkValidator.validate(binding.attrs)
        val now = Instant.now()
        val saved = linksRepository.save(
            Links(
                stableId = request.stableId ?: UUID.randomUUID(),
                source = source,
                target = target,
                createdAt = now,
                updatedAt = now,
                attrs = binding.attrs,
                owner = owner,
                linkType = linkType,
                model = model
            )
        )
        modelSyncBroadcaster.broadcastModelChanged(
            requireNotNull(model.id),
            ModelSyncChangeType.LINK_CREATE.wireValue,
            listOf(
                ModelSyncEntityEvent(
                    ModelSyncEventType.LINK_CREATED.wireValue,
                    ModelSyncEventType.LINK_CREATED.entity,
                    requireNotNull(saved.id)
                )
            )
        )
        return saved
    }
}
