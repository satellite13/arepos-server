package ru.kavader.arepos.dto.featuregrant

data class RoleFeatureGrantsMatrixResponse(
    val roles: Map<String, List<String>>,
    val catalog: List<String>,
)

data class FeatureGrantsUpdateRequest(
    val grants: List<String> = emptyList(),
)

data class FeatureGrantAllowsResponse(
    val grants: List<String>,
)
