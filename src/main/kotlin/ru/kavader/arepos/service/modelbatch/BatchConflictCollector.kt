package ru.kavader.arepos.service.modelbatch

import org.springframework.stereotype.Component
import ru.kavader.arepos.dto.model.BatchConflictItem
import ru.kavader.arepos.dto.model.BatchSaveRequest
import ru.kavader.arepos.model.Models
import ru.kavader.arepos.repository.DiagramsRepository
import ru.kavader.arepos.repository.LinksRepository
import ru.kavader.arepos.repository.NodesRepository
import java.time.Instant

@Component
class BatchConflictCollector(
    private val nodesRepository: NodesRepository,
    private val linksRepository: LinksRepository,
    private val diagramsRepository: DiagramsRepository
) {
    fun collect(request: BatchSaveRequest, model: Models): List<BatchConflictItem> {
        if (request.force) return emptyList()
        val modelId = requireNotNull(model.id) { "Model id required" }
        val conflicts = mutableListOf<BatchConflictItem>()

        for (upd in request.nodes.update) {
            val base = upd.baseUpdatedAt ?: continue
            val node = nodesRepository.findById(upd.id).orElse(null) ?: continue
            if (node.model.id != modelId) continue
            if (isVersionConflict(node.updatedAt, base)) {
                conflicts.add(BatchConflictItem("node", upd.id, node.updatedAt, base))
            }
        }
        for (del in request.nodes.delete) {
            val base = del.baseUpdatedAt ?: continue
            val node = nodesRepository.findById(del.id).orElse(null) ?: continue
            if (node.model.id != modelId) continue
            if (isVersionConflict(node.updatedAt, base)) {
                conflicts.add(BatchConflictItem("node", del.id, node.updatedAt, base))
            }
        }
        for (upd in request.links.update) {
            val base = upd.baseUpdatedAt ?: continue
            val link = linksRepository.findById(upd.id).orElse(null) ?: continue
            if (link.model.id != modelId) continue
            if (isVersionConflict(link.updatedAt, base)) {
                conflicts.add(BatchConflictItem("link", upd.id, link.updatedAt, base))
            }
        }
        for (del in request.links.delete) {
            val base = del.baseUpdatedAt ?: continue
            val link = linksRepository.findById(del.id).orElse(null) ?: continue
            if (link.model.id != modelId) continue
            if (isVersionConflict(link.updatedAt, base)) {
                conflicts.add(BatchConflictItem("link", del.id, link.updatedAt, base))
            }
        }
        for (upd in request.diagrams.update) {
            val base = upd.baseUpdatedAt ?: continue
            val diagram = diagramsRepository.findById(upd.id).orElse(null) ?: continue
            if (diagram.model.id != modelId) continue
            if (isVersionConflict(diagram.updatedAt, base)) {
                conflicts.add(BatchConflictItem("diagram", upd.id, diagram.updatedAt, base))
            }
        }
        for (del in request.diagrams.delete) {
            val base = del.baseUpdatedAt ?: continue
            val diagram = diagramsRepository.findById(del.id).orElse(null) ?: continue
            if (diagram.model.id != modelId) continue
            if (isVersionConflict(diagram.updatedAt, base)) {
                conflicts.add(BatchConflictItem("diagram", del.id, diagram.updatedAt, base))
            }
        }

        return conflicts
    }

    private fun isVersionConflict(server: Instant?, clientBase: Instant): Boolean {
        if (server == null) return true
        return server.toEpochMilli() != clientBase.toEpochMilli()
    }
}
