package ru.kavader.arepos.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.dto.common.ListResponse
import ru.kavader.arepos.dto.common.toListResponse
import ru.kavader.arepos.dto.model.*
import ru.kavader.arepos.dto.system.ModelSyncChangeType
import ru.kavader.arepos.dto.system.ModelSyncEntityEvent
import ru.kavader.arepos.dto.system.ModelSyncEventType
import ru.kavader.arepos.mapper.ModelMapper
import ru.kavader.arepos.model.Models
import ru.kavader.arepos.model.SharePermission
import ru.kavader.arepos.repository.ModelsRepository
import ru.kavader.arepos.security.ADMIN_ONLY
import ru.kavader.arepos.security.OwnerResolutionService
import ru.kavader.arepos.security.ResourceAccessService
import ru.kavader.arepos.service.*
import ru.kavader.arepos.util.VersionUtils
import java.util.*

@RestController
@RequestMapping("/api/v1/models")
@Tag(name = "Models", description = "Model management and versioning endpoints")
class ModelsController(
    private val modelsRepository: ModelsRepository,
    private val accessService: ResourceAccessService,
    private val ownerResolutionService: OwnerResolutionService,
    private val mdFileLinkValidator: MdFileLinkValidator,
    private val modelCopyService: ModelCopyService,
    private val modelLifecycleService: ModelLifecycleService,
    private val modelSyncBroadcaster: ModelSyncBroadcaster,
    private val modelMapper: ModelMapper
) {
    private val viewPermissions = listOf(SharePermission.VIEW, SharePermission.EDIT)

    @GetMapping
    @Operation(summary = "List models")
    fun listModels(
        pageable: Pageable,
        @RequestParam(required = false) ownerId: UUID?,
        @RequestParam(required = false) name: String?
    ): ListResponse<ModelResponse> {
        if (!accessService.canViewAdminPanel()) {
            val currentUserId = accessService.currentUserId()
            val page = modelsRepository.findAccessibleForUser(
                userId = currentUserId,
                ownerId = ownerId,
                name = name?.trim().orEmpty(),
                viewPermissions = viewPermissions,
                pageable = pageable
            ).applyMcpModelAllowlist(accessService, null) { it.id }
            return mapModelsPage(page)
        }

        val models = listPageByOwnerAndName(
            effectiveOwner = ownerResolutionService.resolveReadableOwner(ownerId) { oid, uid ->
                modelsRepository.existsAccessibleByOwnerForUser(oid, uid, viewPermissions)
            },
            name = name,
            pageable = pageable,
            queries = OwnerNamePageQueries(
                byOwnerAndName = modelsRepository::findByOwnerAndNameContainingIgnoreCase,
                byOwner = modelsRepository::findByOwner,
                byName = modelsRepository::findByNameContainingIgnoreCase,
                all = modelsRepository::findAll
            )
        )
        return mapModelsPage(models)
    }

    @GetMapping("/deleted")
    @Operation(summary = "List soft-deleted models (admin)")
    fun listDeletedModels(pageable: Pageable): ListResponse<ModelResponse> {
        if (!accessService.canViewAdminPanel()) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, ADMIN_ONLY)
        }
        return mapModelsPage(modelsRepository.findByDeletedTrue(pageable))
    }

    @DeleteMapping("/{id}/permanent")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Permanently delete model (admin)")
    fun permanentDeleteModel(@PathVariable id: UUID) {
        if (!accessService.canViewAdminPanel()) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, ADMIN_ONLY)
        }
        val model = modelsRepository.findByIdIncludingDeleted(id)
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Model $id not found")
            }
        modelLifecycleService.permanentDeleteModel(model)
    }

    @GetMapping("/grouped")
    @Operation(summary = "List models grouped by name")
    fun listModelsGrouped(): GroupedEntityResponse<ModelResponse> {
        val allModels = if (!accessService.canViewAdminPanel()) {
            modelsRepository.findAccessibleForUser(
                userId = accessService.currentUserId(),
                ownerId = null,
                name = "",
                viewPermissions = viewPermissions,
                pageable = Pageable.unpaged()
            ).content
        } else {
            modelsRepository.findAll(Pageable.unpaged()).content
        }

        val scopedModels = accessService.filterByMcpModelAllowlist(allModels) { it.id }
        val groups = scopedModels
            .groupBy { it.name.trim().lowercase() }
            .map { (_, models) ->
                val sorted = models.sortedWith(compareModelsByVersionDesc)
                val permissions = accessService.modelAccessPermissions(sorted)
                EntityGroupResponse(
                    name = sorted.first().name.trim(),
                    versions = sorted.map { model ->
                        val modelId = requireNotNull(model.id)
                        modelMapper.toResponse(model, permissions[modelId])
                    }
                )
            }
            .sortedBy { it.name.lowercase() }

        return GroupedEntityResponse(groups)
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get model by id")
    fun getModel(@PathVariable id: UUID): ModelResponse =
        modelsRepository.findById(id)
            .map {
                accessService.requireCanViewModel(it)
                modelMapper.toResponse(it)
            }
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Model $id not found")
            }

    @GetMapping("/{id}/related-versions")
    @Operation(summary = "Get related model versions")
    fun getRelatedVersions(@PathVariable id: UUID): ListResponse<ModelResponse> {
        val model = modelsRepository.findById(id)
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Model $id not found")
            }
        accessService.requireCanViewModel(model)
        val byName = modelsRepository.findByNameAndDeletedFalse(model.name)
        val withSource = model.source?.let { listOf(it) } ?: emptyList()
        val derived = modelsRepository.findBySourceIdAndDeletedFalse(id)
        val combined = (byName + withSource + derived).distinctBy { it.id }
        val filtered = if (accessService.canViewAdminPanel()) {
            combined
        } else {
            accessService.filterViewableModels(combined)
        }
        return filtered
            .sortedWith(compareModelsByVersionDesc)
            .let { models ->
                val permissions = accessService.modelAccessPermissions(models)
                models.map { model ->
                    val modelId = requireNotNull(model.id)
                    modelMapper.toResponse(model, permissions[modelId])
                }
            }
            .toListResponse()
    }

    private val compareModelsByVersionDesc = VersionUtils.semverDescComparator<Models> { it.version }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create model with system root node")
    fun createModel(@RequestBody @Valid request: ModelRequest): ModelResponse {
        if (modelsRepository.existsByNameAndVersion(request.name, request.version)) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "Model with name '${request.name}' and version '${request.version}' already exists"
            )
        }
        val owner = ownerResolutionService.resolveOwnerForCreate(request.ownerId)
        mdFileLinkValidator.validate(request.attrs)
        return modelMapper.toResponse(modelLifecycleService.createModel(request, owner))
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update model")
    fun updateModel(
        @PathVariable id: UUID,
        @RequestBody @Valid request: ModelUpdateRequest
    ): ModelResponse {
        val model = modelsRepository.findById(id)
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Model $id not found")
            }
        accessService.requireCanEditModel(model)
        mdFileLinkValidator.validate(request.attrs)

        val newName = request.name ?: model.name
        val newVersion = request.version ?: model.version
        if (modelsRepository.existsByNameAndVersionAndIdNot(newName, newVersion, id)) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "Model with name '$newName' and version '$newVersion' already exists"
            )
        }
        val owner = ownerResolutionService.resolveOwnerForUpdate(request.ownerId, model.owner)

        model.name = newName
        model.attrs = request.attrs ?: model.attrs
        model.version = newVersion
        model.owner = owner
        val updated = modelsRepository.save(model)
        modelSyncBroadcaster.broadcastModelChanged(
            id,
            ModelSyncChangeType.MODEL_UPDATE.wireValue,
            listOf(
                ModelSyncEntityEvent(
                    ModelSyncEventType.MODEL_UPDATED.wireValue,
                    ModelSyncEventType.MODEL_UPDATED.entity,
                    id
                )
            )
        )
        return modelMapper.toResponse(updated)
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Soft-delete model")
    fun deleteModel(@PathVariable id: UUID) {
        val model = modelsRepository.findById(id)
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Model $id not found")
            }
        accessService.requireCanEditModel(model)
        modelLifecycleService.softDeleteModel(id)
    }

    @PostMapping("/{sourceId}/copy")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create new model version by copying source model")
    fun copyModel(
        @PathVariable sourceId: UUID,
        @RequestBody @Valid request: ModelRequest
    ): ModelResponse {
        val source = modelsRepository.findById(sourceId)
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Source model $sourceId not found")
            }
        accessService.requireCanEditModel(source)
        // Конфликт только с неудалёнными: версия, занятая удалённой моделью, допустима
        if (modelsRepository.existsByNameAndVersion(request.name, request.version)) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "Model with name '${request.name}' and version '${request.version}' already exists"
            )
        }
        val owner = ownerResolutionService.resolveOwnerForCreate(request.ownerId)
        mdFileLinkValidator.validate(request.attrs)
        val copied = modelCopyService.copyModel(
            source = source,
            owner = owner,
            name = request.name,
            version = request.version
        )
        return modelMapper.toResponse(copied)
    }

    private fun mapModelsPage(page: org.springframework.data.domain.Page<Models>): ListResponse<ModelResponse> {
        return page.mapWithPermissions(
            loadPermissions = accessService::modelAccessPermissions,
            idOf = Models::id,
            transform = modelMapper::toResponse
        ).toListResponse()
    }
}

