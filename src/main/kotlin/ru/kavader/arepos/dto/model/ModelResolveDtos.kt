package ru.kavader.arepos.dto.model

import com.fasterxml.jackson.annotation.JsonIgnore
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Size
import java.util.UUID

const val MODEL_RESOLVE_MAX_IDS = 2_000

data class ModelNodesResolveRequest(
    @field:NotEmpty
    @field:Size(max = MODEL_RESOLVE_MAX_IDS)
    val nodeIds: List<UUID> = emptyList()
)

data class ModelNodesResolveResponse(
    val nodes: List<NodeResponse>,
    val missingIds: List<UUID>
)

data class ModelLinksResolveRequest(
    @field:Size(max = MODEL_RESOLVE_MAX_IDS)
    val linkIds: List<UUID> = emptyList(),
    @field:Size(max = MODEL_RESOLVE_MAX_IDS)
    val endpointNodeIds: List<UUID> = emptyList()
) {
    @get:JsonIgnore
    @get:AssertTrue(message = "At least one of linkIds or endpointNodeIds must be non-empty")
    val hasRequestedIds: Boolean
        get() = linkIds.isNotEmpty() || endpointNodeIds.isNotEmpty()
}

data class ModelLinksResolveResponse(
    val links: List<LinkResponse>,
    val missingLinkIds: List<UUID>
)
