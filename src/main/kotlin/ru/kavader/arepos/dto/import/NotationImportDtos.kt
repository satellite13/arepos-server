package ru.kavader.arepos.dto.import

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.util.*

data class NotationImportRequest(
    @field:Valid
    val notation: NotationImportMeta,
    @field:Size(max = 1000)
    @field:Valid
    val nodeTypes: List<ImportedNodeType> = emptyList(),
    @field:Size(max = 1000)
    @field:Valid
    val linkTypes: List<ImportedLinkType> = emptyList(),
    @field:Size(max = 1000)
    @field:Valid
    val components: List<ImportedComponent> = emptyList(),
    @field:Size(max = 1000)
    @field:Valid
    val relations: List<ImportedRelation> = emptyList(),
    @field:Size(max = 5000)
    @field:Valid
    val relationRules: List<ImportedRelationRule> = emptyList(),
    @field:Size(max = 1000)
    @field:Valid
    val shapes: List<ImportedNodeShape> = emptyList(),
    /** Library SVG snapshots for admin extract. Import does not upsert these. */
    @field:Size(max = 500)
    val icons: List<ImportedLibraryIcon> = emptyList()
)

data class ImportedLibraryIcon(
    @field:NotBlank
    @field:Size(max = 255)
    val name: String,
    @field:NotBlank
    @field:Size(max = 102400)
    val svg: String
)

data class NotationImportMeta(
    @field:NotBlank
    @field:Size(max = 255)
    val name: String,
    @field:NotBlank
    @field:Size(max = 64)
    val version: String = "1.0.0",
    @field:Size(max = 100000)
    val attrs: String? = null
)

data class ImportedNodeType(
    @field:NotBlank
    @field:Size(max = 255)
    val id: String,
    @field:NotBlank
    @field:Size(max = 255)
    val name: String,
    @field:Size(max = 100000)
    val attrs: String? = null
)

data class ImportedLinkType(
    @field:NotBlank
    @field:Size(max = 255)
    val id: String,
    @field:NotBlank
    @field:Size(max = 255)
    val name: String,
    @field:Size(max = 100000)
    val attrs: String? = null
)

data class ImportedComponent(
    @field:NotBlank
    @field:Size(max = 255)
    val id: String,
    @field:NotBlank
    @field:Size(max = 255)
    val name: String,
    @field:NotBlank
    @field:Size(max = 255)
    val nodeTypeId: String,
    @field:Size(max = 64)
    val version: String? = null,
    @field:Size(max = 100000)
    val attrs: String? = null
)

data class ImportedRelation(
    @field:NotBlank
    @field:Size(max = 255)
    val id: String,
    @field:NotBlank
    @field:Size(max = 255)
    val name: String,
    @field:NotBlank
    @field:Size(max = 255)
    val linkTypeId: String,
    @field:Size(max = 64)
    val version: String? = null,
    @field:Size(max = 100000)
    val attrs: String? = null
)

data class ImportedRelationRule(
    @field:NotBlank
    @field:Size(max = 255)
    val fromComponentId: String,
    @field:NotBlank
    @field:Size(max = 255)
    val toComponentId: String,
    @field:Size(max = 1000)
    val allowedRelationIds: List<@NotBlank @Size(max = 255) String> = emptyList()
)

data class ImportedNodeShape(
    @field:NotBlank
    @field:Size(max = 255)
    val id: String,
    @field:NotBlank
    @field:Size(max = 255)
    val name: String,
    @field:Size(max = 500000)
    val outline: String? = null,
    @field:Size(max = 100000)
    val contentArea: String? = null,
    @field:Size(max = 100000)
    val attrs: String? = null
)

data class NotationImportResponse(
    val notationId: UUID,
    val nodeTypeIdMap: Map<String, UUID>,
    val linkTypeIdMap: Map<String, UUID>,
    val componentIdMap: Map<String, UUID>,
    val relationIdMap: Map<String, UUID>,
    val shapeIdMap: Map<String, UUID> = emptyMap()
)
