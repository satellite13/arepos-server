package ru.kavader.arepos.service.modelbatch

import org.springframework.stereotype.Component
import ru.kavader.arepos.dto.model.BatchSaveRequest
import ru.kavader.arepos.dto.system.ModelSyncEntityEvent
import java.util.*

@Component
class BatchEventBuilder {
    fun build(
        request: BatchSaveRequest,
        nodeIdMap: Map<String, UUID>,
        linkIdMap: Map<String, UUID>,
        diagramIdMap: Map<String, UUID>
    ): List<ModelSyncEntityEvent> {
        val ops = ArrayList<BatchEntityOp>()
        request.nodes.create.forEach { ops.add(NodeCreateOp(nodeIdMap.getValue(it.tempId))) }
        request.nodes.update.forEach { ops.add(NodeUpdateOp(it.id)) }
        request.nodes.delete.forEach { ops.add(NodeDeleteOp(it.id)) }
        request.links.create.forEach { ops.add(LinkCreateOp(linkIdMap.getValue(it.tempId))) }
        request.links.update.forEach { ops.add(LinkUpdateOp(it.id)) }
        request.links.delete.forEach { ops.add(LinkDeleteOp(it.id)) }
        request.diagrams.create.forEach { ops.add(DiagramCreateOp(diagramIdMap.getValue(it.tempId))) }
        request.diagrams.update.forEach { ops.add(DiagramUpdateOp(it.id)) }
        request.diagrams.delete.forEach { ops.add(DiagramDeleteOp(it.id)) }

        return ops.map { op ->
            ModelSyncEntityEvent(op.eventType, op.entity, op.id)
        }
    }
}
