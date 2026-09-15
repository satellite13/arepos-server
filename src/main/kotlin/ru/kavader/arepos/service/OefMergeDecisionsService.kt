package ru.kavader.arepos.service

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.dto.oef.OefMergeDecisionDto
import ru.kavader.arepos.dto.oef.OefMergeDecisionSaveRequest
import ru.kavader.arepos.dto.oef.OefMergeDecisionSaveResponse
import ru.kavader.arepos.model.Models
import ru.kavader.arepos.model.Nodes
import ru.kavader.arepos.model.OefMergeDecisions
import ru.kavader.arepos.repository.ModelsRepository
import ru.kavader.arepos.repository.NodesRepository
import ru.kavader.arepos.repository.OefMergeDecisionsRepository
import ru.kavader.arepos.repository.UsersRepository
import ru.kavader.arepos.security.CurrentUser
import ru.kavader.arepos.security.ResourceAccessService
import java.time.Instant
import java.util.UUID

/**
 * Persisted duplicate-merge decisions for OEF imports. A decision maps an OEF entity
 * id (from the exchange XML) to the node it was merged into during a validation run;
 * subsequent imports reuse the target node instead of creating a duplicate.
 */
@Service
class OefMergeDecisionsService(
    private val modelsRepository: ModelsRepository,
    private val nodesRepository: NodesRepository,
    private val decisionsRepository: OefMergeDecisionsRepository,
    private val usersRepository: UsersRepository,
    private val accessService: ResourceAccessService,
) {

    @Transactional(readOnly = true)
    fun list(modelId: UUID): List<OefMergeDecisionDto> {
        val model = requireModel(modelId)
        accessService.requireCanEditModel(model)
        return decisionsRepository.findByModel(model).map { it.toDto() }
    }

    @Transactional
    fun save(modelId: UUID, request: OefMergeDecisionSaveRequest): OefMergeDecisionSaveResponse {
        val model = requireModel(modelId)
        accessService.requireCanEditModel(model)
        if (request.decisions.isEmpty()) {
            return OefMergeDecisionSaveResponse(saved = 0, skipped = 0)
        }

        val createdBy = CurrentUser.getId()?.let { id ->
            usersRepository.findById(id).orElse(null)
        }
        var saved = 0
        var skipped = 0
        for (item in request.decisions) {
            val entityId = item.oefEntityId.trim()
            if (entityId.isEmpty()) {
                skipped++
                continue
            }
            val target = nodesRepository.findByModel_IdAndIdIn(modelId, listOf(item.targetNodeId))
                .firstOrNull()
            if (target == null) {
                skipped++
                continue
            }
            val existing = decisionsRepository.findByModelAndOefEntityId(model, entityId)
            if (existing != null) {
                existing.targetNode = target
                existing.signatureType = item.signatureType?.trim()?.ifEmpty { null }
                existing.signatureName = item.signatureName?.trim()?.ifEmpty { null }
                existing.updatedAt = Instant.now()
                decisionsRepository.save(existing)
            } else {
                decisionsRepository.save(
                    OefMergeDecisions(
                        model = model,
                        oefEntityId = entityId,
                        targetNode = target,
                        signatureType = item.signatureType?.trim()?.ifEmpty { null },
                        signatureName = item.signatureName?.trim()?.ifEmpty { null },
                        createdBy = createdBy,
                        createdAt = Instant.now(),
                    )
                )
            }
            saved++
        }
        return OefMergeDecisionSaveResponse(saved = saved, skipped = skipped)
    }

    @Transactional
    fun delete(modelId: UUID, oefEntityId: String) {
        val model = requireModel(modelId)
        accessService.requireCanEditModel(model)
        decisionsRepository.deleteByModelAndOefEntityId(model, oefEntityId)
    }

    private fun requireModel(modelId: UUID): Models = modelsRepository.findById(modelId).orElseThrow {
        ResponseStatusException(HttpStatus.NOT_FOUND, "Model $modelId not found")
    }

    private fun OefMergeDecisions.toDto() = OefMergeDecisionDto(
        id = requireNotNull(id),
        oefEntityId = oefEntityId,
        targetNodeId = requireNotNull(targetNode.id),
        signatureType = signatureType,
        signatureName = signatureName,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}
