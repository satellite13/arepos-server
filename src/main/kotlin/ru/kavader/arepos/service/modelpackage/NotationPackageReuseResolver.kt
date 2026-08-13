package ru.kavader.arepos.service.modelpackage

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Component
import ru.kavader.arepos.dto.import.NotationImportRequest
import ru.kavader.arepos.dto.import.NotationImportResponse
import ru.kavader.arepos.model.Components
import ru.kavader.arepos.model.Relations
import ru.kavader.arepos.model.Users
import ru.kavader.arepos.repository.ComponentsRepository
import ru.kavader.arepos.repository.LinkTypesRepository
import ru.kavader.arepos.repository.NodeTypesRepository
import ru.kavader.arepos.repository.NotationsRepository
import ru.kavader.arepos.repository.RelationsRepository
import ru.kavader.arepos.security.ResourceAccessService
import java.util.UUID

data class NotationReuseResult(
    val response: NotationImportResponse,
    val warning: String
)

@Component
class NotationPackageReuseResolver(
    private val notationsRepository: NotationsRepository,
    private val componentsRepository: ComponentsRepository,
    private val relationsRepository: RelationsRepository,
    private val nodeTypesRepository: NodeTypesRepository,
    private val linkTypesRepository: LinkTypesRepository,
    private val accessService: ResourceAccessService,
    private val objectMapper: ObjectMapper
) {
    /**
     * @return null when no existing notation with the same name+version — caller should import.
     */
    fun tryReuse(request: NotationImportRequest, owner: Users): NotationReuseResult? {
        val name = request.notation.name.trim()
        val version = request.notation.version.trim().ifEmpty { "1.0.0" }
        val existing = notationsRepository.findByNameAndVersion(name, version) ?: return null

        if (!accessService.canViewNotation(existing)) {
            throw PackageImportConflictException.notationExistsForbidden(name, version)
        }

        val existingComponents = componentsRepository.findByNotation(existing, Pageable.unpaged()).content
        val existingRelations = relationsRepository.findByNotation(existing, Pageable.unpaged()).content

        val componentIndex = indexComponents(existingComponents)
        val relationIndex = indexRelations(existingRelations)

        val details = mutableListOf<String>()
        val componentIdMap = linkedMapOf<String, UUID>()
        val relationIdMap = linkedMapOf<String, UUID>()
        val nodeTypeIdMap = linkedMapOf<String, UUID>()
        val linkTypeIdMap = linkedMapOf<String, UUID>()
        val shapeIdMap = linkedMapOf<String, UUID>()

        val packagedNodeTypesById = request.nodeTypes.associateBy { it.id }
        val packagedLinkTypesById = request.linkTypes.associateBy { it.id }

        for (imported in request.components) {
            val typeName = packagedNodeTypesById[imported.nodeTypeId]?.name?.trim().orEmpty()
            if (typeName.isEmpty()) {
                details += "Component '${imported.name}' references unknown nodeTypeId '${imported.nodeTypeId}'"
                continue
            }
            val key = matchKey(imported.name, typeName)
            val matches = componentIndex[key].orEmpty()
            when (matches.size) {
                1 -> {
                    val existingComponent = matches.first()
                    componentIdMap[imported.id] = existingComponent.id!!
                    nodeTypeIdMap[imported.nodeTypeId] = existingComponent.nodeType.id!!
                    mapShapeIds(imported.attrs, existingComponent.attrs, shapeIdMap)
                }
                0 -> details += "Component '${imported.name}' (nodeType $typeName) not found"
                else -> details += "Component '${imported.name}' (nodeType $typeName) is ambiguous"
            }
        }

        for (imported in request.relations) {
            val typeName = packagedLinkTypesById[imported.linkTypeId]?.name?.trim().orEmpty()
            if (typeName.isEmpty()) {
                details += "Relation '${imported.name}' references unknown linkTypeId '${imported.linkTypeId}'"
                continue
            }
            val key = matchKey(imported.name, typeName)
            val matches = relationIndex[key].orEmpty()
            when (matches.size) {
                1 -> {
                    val existingRelation = matches.first()
                    relationIdMap[imported.id] = existingRelation.id!!
                    linkTypeIdMap[imported.linkTypeId] = existingRelation.linkType.id!!
                }
                0 -> details += "Relation '${imported.name}' (linkType $typeName) not found"
                else -> details += "Relation '${imported.name}' (linkType $typeName) is ambiguous"
            }
        }

        if (details.isNotEmpty()) {
            throw PackageImportConflictException.notationIncompatible(name, version, details)
        }

        // Map remaining packaged types by name from the existing notation catalog / importer ownership.
        val existingNodeTypesByName = existingComponents.associate {
            it.nodeType.name.trim().lowercase() to it.nodeType.id!!
        }
        for (imported in request.nodeTypes) {
            if (nodeTypeIdMap.containsKey(imported.id)) continue
            val byNotation = existingNodeTypesByName[imported.name.trim().lowercase()]
            if (byNotation != null) {
                nodeTypeIdMap[imported.id] = byNotation
                continue
            }
            val owned = nodeTypesRepository.findByOwnerAndNameIgnoreCase(owner, imported.name.trim())
            if (owned?.id != null) {
                nodeTypeIdMap[imported.id] = owned.id!!
            }
        }

        val existingLinkTypesByName = existingRelations.associate {
            it.linkType.name.trim().lowercase() to it.linkType.id!!
        }
        for (imported in request.linkTypes) {
            if (linkTypeIdMap.containsKey(imported.id)) continue
            val byNotation = existingLinkTypesByName[imported.name.trim().lowercase()]
            if (byNotation != null) {
                linkTypeIdMap[imported.id] = byNotation
                continue
            }
            val owned = linkTypesRepository.findByOwnerAndNameIgnoreCase(owner, imported.name.trim())
            if (owned?.id != null) {
                linkTypeIdMap[imported.id] = owned.id!!
            }
        }

        return NotationReuseResult(
            response = NotationImportResponse(
                notationId = existing.id!!,
                nodeTypeIdMap = nodeTypeIdMap,
                linkTypeIdMap = linkTypeIdMap,
                componentIdMap = componentIdMap,
                relationIdMap = relationIdMap,
                shapeIdMap = shapeIdMap
            ),
            warning = "Reused notation '$name' v$version"
        )
    }

    private fun indexComponents(components: List<Components>): Map<String, List<Components>> =
        components.groupBy { matchKey(it.name, it.nodeType.name) }

    private fun indexRelations(relations: List<Relations>): Map<String, List<Relations>> =
        relations.groupBy { matchKey(it.name, it.linkType.name) }

    private fun matchKey(entityName: String, typeName: String): String =
        "${entityName.trim().lowercase()}|${typeName.trim().lowercase()}"

    private fun mapShapeIds(importedAttrs: String?, existingAttrs: String?, shapeIdMap: MutableMap<String, UUID>) {
        val packagedShapeId = extractCustomShapeId(importedAttrs) ?: return
        val existingShapeId = extractCustomShapeId(existingAttrs) ?: return
        runCatching { UUID.fromString(existingShapeId) }.getOrNull()?.let { shapeIdMap[packagedShapeId] = it }
    }

    private fun extractCustomShapeId(attrs: String?): String? {
        if (attrs.isNullOrBlank()) return null
        return try {
            val root = objectMapper.readTree(attrs)
            if (!root.isObject) return null
            val diagramStyle = root.get("diagramStyle") ?: return null
            if (!diagramStyle.isObject) return null
            diagramStyle.get("customShapeId")?.asText()?.trim()?.takeIf { it.isNotEmpty() }
        } catch (_: Exception) {
            null
        }
    }
}
