package ru.kavader.arepos.service.modelbatch

import java.util.UUID

sealed interface BatchEntityOp {
    val entity: String
    val eventType: String
    val id: UUID
}

data class NodeCreateOp(override val id: UUID) : BatchEntityOp {
    override val entity: String = "node"
    override val eventType: String = "node_created"
}

data class NodeUpdateOp(override val id: UUID) : BatchEntityOp {
    override val entity: String = "node"
    override val eventType: String = "node_updated"
}

data class NodeDeleteOp(override val id: UUID) : BatchEntityOp {
    override val entity: String = "node"
    override val eventType: String = "node_deleted"
}

data class LinkCreateOp(override val id: UUID) : BatchEntityOp {
    override val entity: String = "link"
    override val eventType: String = "link_created"
}

data class LinkUpdateOp(override val id: UUID) : BatchEntityOp {
    override val entity: String = "link"
    override val eventType: String = "link_updated"
}

data class LinkDeleteOp(override val id: UUID) : BatchEntityOp {
    override val entity: String = "link"
    override val eventType: String = "link_deleted"
}

data class DiagramCreateOp(override val id: UUID) : BatchEntityOp {
    override val entity: String = "diagram"
    override val eventType: String = "diagram_created"
}

data class DiagramUpdateOp(override val id: UUID) : BatchEntityOp {
    override val entity: String = "diagram"
    override val eventType: String = "diagram_updated"
}

data class DiagramDeleteOp(override val id: UUID) : BatchEntityOp {
    override val entity: String = "diagram"
    override val eventType: String = "diagram_deleted"
}
