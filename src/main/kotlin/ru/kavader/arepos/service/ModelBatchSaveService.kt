package ru.kavader.arepos.service

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.dto.model.BatchSaveConflictException
import ru.kavader.arepos.dto.model.BatchSaveRequest
import ru.kavader.arepos.dto.model.BatchSaveResponse
import ru.kavader.arepos.repository.ModelsRepository
import ru.kavader.arepos.repository.UsersRepository
import ru.kavader.arepos.security.ResourceAccessService
import ru.kavader.arepos.service.modelbatch.BatchConflictCollector
import ru.kavader.arepos.service.modelbatch.BatchEventBuilder
import ru.kavader.arepos.service.modelbatch.BatchGraphOpsExecutor
import java.time.Instant
import java.util.UUID

@Service
class ModelBatchSaveService(
    private val modelsRepository: ModelsRepository,
    private val usersRepository: UsersRepository,
    private val accessService: ResourceAccessService,
    private val batchConflictCollector: BatchConflictCollector,
    private val batchGraphOpsExecutor: BatchGraphOpsExecutor,
    private val batchEventBuilder: BatchEventBuilder,
    private val modelSyncBroadcaster: ModelSyncBroadcaster
) {
    @Transactional
    fun batchSave(modelId: UUID, request: BatchSaveRequest): BatchSaveResponse {
        val model = modelsRepository.findById(modelId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Model $modelId not found") }
        accessService.requireCanEditModel(model)

        val conflicts = batchConflictCollector.collect(request, model)
        if (conflicts.isNotEmpty()) {
            throw BatchSaveConflictException(conflicts)
        }

        val owner = usersRepository.findById(accessService.currentUserId())
            .orElseThrow { ResponseStatusException(HttpStatus.UNAUTHORIZED, "Current user not found") }

        val execution = batchGraphOpsExecutor.execute(
            request = request,
            model = model,
            owner = owner,
            now = Instant.now()
        )

        if (execution.mutated) {
            modelSyncBroadcaster.broadcastModelChanged(
                requireNotNull(model.id),
                "batch_save",
                batchEventBuilder.build(
                    request = request,
                    nodeIdMap = execution.nodeIdMap,
                    linkIdMap = execution.linkIdMap,
                    diagramIdMap = execution.diagramIdMap
                )
            )
        }

        return BatchSaveResponse(
            nodeIdMap = execution.nodeIdMap,
            linkIdMap = execution.linkIdMap,
            diagramIdMap = execution.diagramIdMap,
            nodesCreated = execution.nodesCreated,
            nodesUpdated = execution.nodesUpdated,
            nodesDeleted = execution.nodesDeleted,
            linksCreated = execution.linksCreated,
            linksUpdated = execution.linksUpdated,
            linksDeleted = execution.linksDeleted,
            diagramsCreated = execution.diagramsCreated,
            diagramsUpdated = execution.diagramsUpdated,
            diagramsDeleted = execution.diagramsDeleted
        )
    }
}
