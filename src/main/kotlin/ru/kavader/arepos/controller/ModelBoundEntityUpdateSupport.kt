package ru.kavader.arepos.controller

import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.model.Models
import ru.kavader.arepos.model.Users
import ru.kavader.arepos.repository.ModelsRepository
import ru.kavader.arepos.security.OwnerResolutionService
import ru.kavader.arepos.security.ResourceAccessService
import java.util.*

data class ModelBoundOwnerAndModel(
    val owner: Users,
    val model: Models
)

object ModelBoundEntityUpdateSupport {
    fun resolveOwnerAndModel(
        requestOwnerId: UUID?,
        requestModelId: UUID?,
        currentOwner: Users,
        currentModel: Models,
        ownerResolutionService: OwnerResolutionService,
        modelsRepository: ModelsRepository,
        accessService: ResourceAccessService
    ): ModelBoundOwnerAndModel = ModelBoundOwnerAndModel(
        owner = ownerResolutionService.resolveOwnerForUpdate(requestOwnerId, currentOwner),
        model = resolveModelForUpdate(requestModelId, currentModel, modelsRepository, accessService)
    )

    fun resolveModelForUpdate(
        requestModelId: UUID?,
        currentModel: Models,
        modelsRepository: ModelsRepository,
        accessService: ResourceAccessService
    ): Models =
        requestModelId?.let { modelId ->
            modelsRepository.findById(modelId).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Model $modelId not found")
            }
        }?.also { newModel ->
            accessService.requireCanEditModel(newModel)
        } ?: currentModel
}
