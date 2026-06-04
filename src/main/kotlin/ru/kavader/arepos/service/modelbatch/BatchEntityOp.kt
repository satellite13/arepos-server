package ru.kavader.arepos.service.modelbatch

import ru.kavader.arepos.dto.system.ModelSyncEventType
import java.util.*

sealed interface BatchEntityOp {
    val entity: String
    val eventType: String
    val id: UUID
}

data class NodeCreateOp(override val id: UUID) : BatchEntityOp {
    override val entity: String = ModelSyncEventType.NODE_CREATED.entity
    override val eventType: String = ModelSyncEventType.NODE_CREATED.wireValue
}

data class NodeUpdateOp(override val id: UUID) : BatchEntityOp {
    override val entity: String = ModelSyncEventType.NODE_UPDATED.entity
    override val eventType: String = ModelSyncEventType.NODE_UPDATED.wireValue
}

data class NodeDeleteOp(override val id: UUID) : BatchEntityOp {
    override val entity: String = ModelSyncEventType.NODE_DELETED.entity
    override val eventType: String = ModelSyncEventType.NODE_DELETED.wireValue
}

data class LinkCreateOp(override val id: UUID) : BatchEntityOp {
    override val entity: String = ModelSyncEventType.LINK_CREATED.entity
    override val eventType: String = ModelSyncEventType.LINK_CREATED.wireValue
}

data class LinkUpdateOp(override val id: UUID) : BatchEntityOp {
    override val entity: String = ModelSyncEventType.LINK_UPDATED.entity
    override val eventType: String = ModelSyncEventType.LINK_UPDATED.wireValue
}

data class LinkDeleteOp(override val id: UUID) : BatchEntityOp {
    override val entity: String = ModelSyncEventType.LINK_DELETED.entity
    override val eventType: String = ModelSyncEventType.LINK_DELETED.wireValue
}

data class DiagramCreateOp(override val id: UUID) : BatchEntityOp {
    override val entity: String = ModelSyncEventType.DIAGRAM_CREATED.entity
    override val eventType: String = ModelSyncEventType.DIAGRAM_CREATED.wireValue
}

data class DiagramUpdateOp(override val id: UUID) : BatchEntityOp {
    override val entity: String = ModelSyncEventType.DIAGRAM_UPDATED.entity
    override val eventType: String = ModelSyncEventType.DIAGRAM_UPDATED.wireValue
}

data class DiagramDeleteOp(override val id: UUID) : BatchEntityOp {
    override val entity: String = ModelSyncEventType.DIAGRAM_DELETED.entity
    override val eventType: String = ModelSyncEventType.DIAGRAM_DELETED.wireValue
}
