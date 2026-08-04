package ru.kavader.arepos.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.dto.model.AmbiguousNotationCandidate
import ru.kavader.arepos.dto.model.AmbiguousNotationElementException
import ru.kavader.arepos.model.Components
import ru.kavader.arepos.model.LinkTypes
import ru.kavader.arepos.model.NodeTypes
import ru.kavader.arepos.model.Relations
import ru.kavader.arepos.repository.ComponentsRepository
import ru.kavader.arepos.repository.LinkTypesRepository
import ru.kavader.arepos.repository.NodeTypesRepository
import ru.kavader.arepos.repository.NotationsRepository
import ru.kavader.arepos.repository.RelationsRepository
import java.util.UUID

data class ResolvedNodeBinding(
    val nodeType: NodeTypes,
    val attrs: String?
)

data class ResolvedLinkBinding(
    val linkType: LinkTypes,
    val attrs: String?
)

@Service
class NotationBindingService(
    private val notationsRepository: NotationsRepository,
    private val componentsRepository: ComponentsRepository,
    private val relationsRepository: RelationsRepository,
    private val nodeTypesRepository: NodeTypesRepository,
    private val linkTypesRepository: LinkTypesRepository,
    private val objectMapper: ObjectMapper
) {

    fun resolveNodeCreate(
        nodeTypeId: UUID?,
        notationId: UUID?,
        componentId: UUID?,
        componentName: String?,
        attrs: String?
    ): ResolvedNodeBinding {
        val hasComponentRef = componentId != null || !componentName.isNullOrBlank()
        if (!hasComponentRef) {
            val typeId = nodeTypeId
                ?: throw ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "nodeTypeId is required when componentId/componentName is not provided"
                )
            val nodeType = nodeTypesRepository.findById(typeId).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "NodeType $typeId not found")
            }
            return ResolvedNodeBinding(nodeType = nodeType, attrs = attrs)
        }

        val component = resolveComponent(notationId, componentId, componentName)
        val resolvedType = component.nodeType
        if (nodeTypeId != null && nodeTypeId != resolvedType.id) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "nodeTypeId $nodeTypeId does not match component ${component.id} nodeType ${resolvedType.id}"
            )
        }
        val mergedAttrs = mergeNotationComponentBinding(attrs, component.notation.id!!, component.id!!)
        return ResolvedNodeBinding(nodeType = resolvedType, attrs = mergedAttrs)
    }

    fun resolveLinkCreate(
        linkTypeId: UUID?,
        notationId: UUID?,
        relationId: UUID?,
        relationName: String?,
        attrs: String?
    ): ResolvedLinkBinding {
        val hasRelationRef = relationId != null || !relationName.isNullOrBlank()
        if (!hasRelationRef) {
            val typeId = linkTypeId
                ?: throw ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "linkTypeId is required when relationId/relationName is not provided"
                )
            val linkType = linkTypesRepository.findById(typeId).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "LinkType $typeId not found")
            }
            return ResolvedLinkBinding(linkType = linkType, attrs = attrs)
        }

        val relation = resolveRelation(notationId, relationId, relationName)
        val resolvedType = relation.linkType
        if (linkTypeId != null && linkTypeId != resolvedType.id) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "linkTypeId $linkTypeId does not match relation ${relation.id} linkType ${resolvedType.id}"
            )
        }
        val mergedAttrs = mergeNotationRelationBinding(attrs, relation.notation.id!!, relation.id!!)
        return ResolvedLinkBinding(linkType = resolvedType, attrs = mergedAttrs)
    }

    fun resolveComponent(notationId: UUID?, componentId: UUID?, componentName: String?): Components {
        if (componentId != null) {
            val component = componentsRepository.findById(componentId).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Component $componentId not found")
            }
            if (notationId != null && component.notation.id != notationId) {
                throw ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Component $componentId does not belong to notation $notationId"
                )
            }
            return component
        }
        val name = componentName?.trim().orEmpty()
        if (name.isEmpty()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "componentId or componentName is required")
        }
        val nid = notationId
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "notationId is required when using componentName")
        ensureNotationExists(nid)
        val matches = componentsRepository.findByNotation_IdAndNameIgnoreCase(nid, name)
        return pickUnique(matches, kind = "component", notationId = nid, query = name) { c ->
            AmbiguousNotationCandidate(c.id!!, c.name, c.version)
        }
    }

    fun resolveRelation(notationId: UUID?, relationId: UUID?, relationName: String?): Relations {
        if (relationId != null) {
            val relation = relationsRepository.findById(relationId).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Relation $relationId not found")
            }
            if (notationId != null && relation.notation.id != notationId) {
                throw ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Relation $relationId does not belong to notation $notationId"
                )
            }
            return relation
        }
        val name = relationName?.trim().orEmpty()
        if (name.isEmpty()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "relationId or relationName is required")
        }
        val nid = notationId
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "notationId is required when using relationName")
        ensureNotationExists(nid)
        val matches = relationsRepository.findByNotation_IdAndNameIgnoreCase(nid, name)
        return pickUnique(matches, kind = "relation", notationId = nid, query = name) { r ->
            AmbiguousNotationCandidate(r.id!!, r.name, r.version)
        }
    }

    fun mergeNotationComponentBinding(attrs: String?, notationId: UUID, componentId: UUID): String =
        mergeBinding(attrs, mapKey = "notationComponents", notationId = notationId, idKey = "componentId", id = componentId)

    fun mergeNotationRelationBinding(attrs: String?, notationId: UUID, relationId: UUID): String =
        mergeBinding(attrs, mapKey = "notationRelations", notationId = notationId, idKey = "relationId", id = relationId)

    fun readNodeComponentId(attrs: String?, notationId: UUID): UUID? {
        if (attrs.isNullOrBlank()) return null
        return try {
            val root = objectMapper.readTree(attrs) as? ObjectNode ?: return null
            val bindings = root.get("notationComponents") as? ObjectNode ?: return null
            val binding = bindings.get(notationId.toString()) as? ObjectNode ?: return null
            binding.get("componentId")?.asText()?.let { UUID.fromString(it) }
        } catch (_: Exception) {
            null
        }
    }

    private fun mergeBinding(
        attrs: String?,
        mapKey: String,
        notationId: UUID,
        idKey: String,
        id: UUID
    ): String {
        val root = parseAttrsObject(attrs)
        val mapNode = (root.get(mapKey) as? ObjectNode) ?: objectMapper.createObjectNode().also {
            root.set<ObjectNode>(mapKey, it)
        }
        val binding = objectMapper.createObjectNode()
        binding.put(idKey, id.toString())
        mapNode.set<ObjectNode>(notationId.toString(), binding)
        return objectMapper.writeValueAsString(root)
    }

    private fun parseAttrsObject(attrs: String?): ObjectNode {
        if (attrs.isNullOrBlank()) {
            return objectMapper.createObjectNode()
        }
        return try {
            val tree = objectMapper.readTree(attrs)
            if (tree is ObjectNode) tree.deepCopy() else objectMapper.createObjectNode()
        } catch (_: Exception) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "attrs must be a JSON object")
        }
    }

    private fun ensureNotationExists(notationId: UUID) {
        if (!notationsRepository.existsById(notationId)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Notation $notationId not found")
        }
    }

    private fun <T> pickUnique(
        matches: List<T>,
        kind: String,
        notationId: UUID,
        query: String,
        toCandidate: (T) -> AmbiguousNotationCandidate
    ): T {
        when (matches.size) {
            0 -> throw ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "$kind '$query' not found in notation $notationId"
            )
            1 -> return matches.first()
            else -> throw AmbiguousNotationElementException(
                kind = kind,
                notationId = notationId,
                query = query,
                candidates = matches.map(toCandidate)
            )
        }
    }
}
