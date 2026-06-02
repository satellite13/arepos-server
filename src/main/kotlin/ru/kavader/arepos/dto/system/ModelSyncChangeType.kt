package ru.kavader.arepos.dto.system

enum class ModelSyncChangeType(val wireValue: String) {
    MODEL_UPDATE("model_update"),
    NODE_CREATE("node_create"),
    NODE_UPDATE("node_update"),
    NODE_DELETE("node_delete"),
    LINK_CREATE("link_create"),
    LINK_UPDATE("link_update"),
    LINK_DELETE("link_delete"),
    DIAGRAM_CREATE("diagram_create"),
    DIAGRAM_UPDATE("diagram_update"),
    DIAGRAM_DELETE("diagram_delete"),
    DIAGRAM_BASELINE("diagram_baseline")
}
