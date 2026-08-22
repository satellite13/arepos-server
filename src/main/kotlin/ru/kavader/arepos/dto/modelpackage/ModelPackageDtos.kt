package ru.kavader.arepos.dto.modelpackage

import ru.kavader.arepos.dto.validation.StorageSemver
import java.time.Instant
import java.util.UUID

data class ModelPackageManifest(
    val format: String,
    val version: Int,
    val exportedAt: Instant,
    val source: ModelPackageSource,
    val notationIds: List<UUID> = emptyList(),
    val fileIds: List<UUID> = emptyList()
)

data class ModelPackageSource(
    val modelId: UUID,
    val modelName: String,
    val modelVersion: String
)

data class PackagedModel(
    val name: String,
    val version: String,
    val attrs: String? = null,
    val nodes: List<PackagedNode> = emptyList(),
    val links: List<PackagedLink> = emptyList(),
    val diagrams: List<PackagedDiagram> = emptyList()
)

data class PackagedNode(
    val id: UUID,
    val stableId: UUID,
    val name: String,
    val nodeTypeId: UUID,
    val parentNodeId: UUID? = null,
    val attrs: String? = null
)

data class PackagedLink(
    val id: UUID,
    val stableId: UUID,
    val sourceId: UUID,
    val targetId: UUID,
    val linkTypeId: UUID,
    val attrs: String? = null
)

data class PackagedDiagram(
    val id: UUID,
    val name: String,
    val version: String,
    val notationId: UUID,
    val nodeId: UUID? = null,
    val attrs: String? = null
)

data class PackagedDocumentRef(
    val fileId: UUID,
    val modelId: UUID? = null,
    val nodeId: UUID? = null,
    val diagramId: UUID? = null,
    val notationId: UUID? = null,
    val componentId: UUID? = null,
    val relationId: UUID? = null,
    val nodeTypeId: UUID? = null,
    val linkTypeId: UUID? = null,
    val nodeShapeId: UUID? = null
)

data class PackagedFileMeta(
    val filename: String,
    val contentType: String,
    val attrs: String? = null
)

data class ModelPackageImportResponse(
    val modelId: UUID,
    val modelName: String,
    val modelVersion: String,
    val notationIdMap: Map<UUID, UUID> = emptyMap(),
    val nodeTypeIdMap: Map<String, UUID> = emptyMap(),
    val linkTypeIdMap: Map<String, UUID> = emptyMap(),
    val fileIdMap: Map<UUID, UUID> = emptyMap(),
    val warnings: List<String> = emptyList()
)

data class PackageImportJobAcceptedResponse(
    val jobId: UUID,
    val status: String
)

data class PackageImportJobResultDto(
    val modelId: UUID,
    val modelName: String,
    val modelVersion: String,
    val warnings: List<String> = emptyList()
)

data class PackageImportJobErrorDto(
    val status: Int,
    val message: String,
    val code: String? = null,
    val conflict: PackageImportConflictDto? = null
)

data class PackageImportConflictDto(
    val entity: String,
    val name: String,
    val version: String,
    val suggestedVersion: String? = null,
    val details: List<String> = emptyList()
)

data class ModelPackageImportOverrides(
    val targetModelName: String? = null,
    val targetModelVersion: String? = null
)

data class PackageImportJobRetryRequest(
    val targetModelName: String? = null,
    @field:StorageSemver val targetModelVersion: String? = null
)

data class PackageImportJobStatusResponse(
    val jobId: UUID,
    val status: String,
    val stage: String,
    val progress: Int,
    val message: String? = null,
    val result: PackageImportJobResultDto? = null,
    val error: PackageImportJobErrorDto? = null
)

object PackageImportErrorCodes {
    const val CONFLICT = "CONFLICT"
    const val MODEL_EXISTS = "MODEL_EXISTS"
    const val NOTATION_EXISTS_FORBIDDEN = "NOTATION_EXISTS_FORBIDDEN"
    const val NOTATION_INCOMPATIBLE = "NOTATION_INCOMPATIBLE"
    const val PAYLOAD_TOO_LARGE = "PAYLOAD_TOO_LARGE"
    const val BAD_REQUEST = "BAD_REQUEST"
    const val TIMEOUT = "TIMEOUT"
}
