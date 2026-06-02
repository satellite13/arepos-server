package ru.kavader.arepos.dto.system

enum class ModelSyncEventType(val wireValue: String, val entity: String) {
    MODEL_UPDATED("model_updated", "model"),
    NODE_CREATED("node_created", "node"),
    NODE_UPDATED("node_updated", "node"),
    NODE_DELETED("node_deleted", "node"),
    LINK_CREATED("link_created", "link"),
    LINK_UPDATED("link_updated", "link"),
    LINK_DELETED("link_deleted", "link"),
    DIAGRAM_CREATED("diagram_created", "diagram"),
    DIAGRAM_UPDATED("diagram_updated", "diagram"),
    DIAGRAM_DELETED("diagram_deleted", "diagram")
}
