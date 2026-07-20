package ru.kavader.arepos.service

import org.springframework.stereotype.Service
import org.w3c.dom.Element
import org.w3c.dom.Node
import ru.kavader.arepos.dto.oef.*
import javax.xml.parsers.DocumentBuilderFactory

@Service
class OefParseService {

    fun parseAndValidate(xmlBytes: ByteArray): OefNormalizeResponse {
        val parsed = parse(xmlBytes)
        val issues = validate(parsed)
        return parsed.copy(issues = issues)
    }

    fun parse(xmlBytes: ByteArray): OefNormalizeResponse {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            isExpandEntityReferences = false
            fun safeFeature(name: String, value: Boolean) {
                try {
                    setFeature(name, value)
                } catch (_: Exception) {
                    // Feature may be unsupported on some JDK/XML stacks.
                }
            }
            safeFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            safeFeature("http://xml.org/sax/features/external-general-entities", false)
            safeFeature("http://xml.org/sax/features/external-parameter-entities", false)
        }
        val doc = try {
            factory.newDocumentBuilder().parse(xmlBytes.inputStream())
        } catch (ex: Exception) {
            throw IllegalArgumentException("Invalid OEF XML: ${ex.message ?: "parse error"}")
        }

        val modelElement = doc.documentElement
            ?: throw IllegalArgumentException("Invalid OEF XML: missing <model>")
        if (modelElement.localName != "model") {
            throw IllegalArgumentException("Invalid OEF XML: missing <model>")
        }

        return OefNormalizeResponse(
            model = OefModelDto(
                id = modelElement.getAttribute("identifier").trim(),
                name = textOfFirstDirectChild(modelElement, "name"),
            ),
            elements = parseElements(modelElement),
            relationships = parseRelationships(modelElement),
            views = parseViews(modelElement),
            issues = emptyList(),
        )
    }

    fun validate(parsed: OefNormalizeResponse): List<OefImportIssueDto> {
        val issues = mutableListOf<OefImportIssueDto>()
        val elementIds = parsed.elements.map { it.id }.toSet()
        val relationshipIds = parsed.relationships.map { it.id }.toSet()

        issues += duplicateIssues(parsed.elements.map { it.id }, "duplicateElementId") {
            "Duplicate element identifier \"$it\""
        }
        issues += duplicateIssues(parsed.relationships.map { it.id }, "duplicateRelationshipId") {
            "Duplicate relationship identifier \"$it\""
        }
        issues += duplicateIssues(parsed.views.map { it.id }, "duplicateViewId") {
            "Duplicate view identifier \"$it\""
        }

        for (element in parsed.elements) {
            if (element.type.isBlank()) {
                issues += issue(
                    code = "missingElementType",
                    level = "warning",
                    entityId = element.id,
                    message = "Element \"${element.id}\" has no xsi:type",
                )
            }
        }

        for (relationship in parsed.relationships) {
            if (relationship.type.isBlank()) {
                issues += issue(
                    code = "missingRelationshipType",
                    level = "warning",
                    entityId = relationship.id,
                    message = "Relationship \"${relationship.id}\" has no xsi:type",
                )
            }
            val sourceIsElement = relationship.sourceElementId in elementIds
            val targetIsElement = relationship.targetElementId in elementIds
            val sourceIsRelationship = relationship.sourceElementId in relationshipIds
            val targetIsRelationship = relationship.targetElementId in relationshipIds

            if (sourceIsRelationship || targetIsRelationship) {
                issues += issue(
                    code = "relationshipEndpointIsRelationship",
                    level = "warning",
                    entityId = relationship.id,
                    message =
                        "Relationship \"${relationship.id}\" attaches to another relationship and will be imported as diagram-only",
                )
            }
            if (!sourceIsElement && !sourceIsRelationship) {
                issues += issue(
                    code = "relationshipMissingSource",
                    level = "error",
                    entityId = relationship.id,
                    message =
                        "Relationship \"${relationship.id}\" points to missing source element \"${relationship.sourceElementId}\"",
                )
            }
            if (!targetIsElement && !targetIsRelationship) {
                issues += issue(
                    code = "relationshipMissingTarget",
                    level = "error",
                    entityId = relationship.id,
                    message =
                        "Relationship \"${relationship.id}\" points to missing target element \"${relationship.targetElementId}\"",
                )
            }
        }

        for (view in parsed.views) {
            issues += duplicateIssues(view.nodes.map { it.id }, "duplicateViewNodeId") {
                "View \"${view.id}\" contains duplicate node identifier \"$it\""
            }.map { it.copy(viewId = view.id) }
            issues += duplicateIssues(view.connections.map { it.id }, "duplicateViewConnectionId") {
                "View \"${view.id}\" contains duplicate connection identifier \"$it\""
            }.map { it.copy(viewId = view.id) }

            val nodeIds = view.nodes.map { it.id }.toSet()
            val connectionIds = view.connections.map { it.id }.toSet()

            for (node in view.nodes) {
                val diagramOnly = isDiagramOnlyViewNode(node.type)
                if (!diagramOnly && (node.elementId.isBlank() || node.elementId !in elementIds)) {
                    issues += issue(
                        code = "viewNodeMissingElementRef",
                        level = "error",
                        entityId = node.id,
                        viewId = view.id,
                        message = "View node \"${node.id}\" points to missing element \"${node.elementId}\"",
                    )
                }
            }

            for (node in view.nodes) {
                val diagramOnly = isDiagramOnlyViewNode(node.type)
                if (!diagramOnly && node.elementId.isBlank()) {
                    issues += issue(
                        code = "viewNodeMissingElementRef",
                        level = "error",
                        entityId = node.id,
                        viewId = view.id,
                        message = "View node \"${node.id}\" has no elementRef",
                    )
                }
                if (!node.x.isFinite() || !node.y.isFinite()) {
                    issues += issue(
                        code = "viewNodeMissingCoordinates",
                        level = "warning",
                        entityId = node.id,
                        viewId = view.id,
                        message = "View node \"${node.id}\" has invalid coordinates",
                    )
                }
            }

            for (connection in view.connections) {
                val noteLine = connection.type == "Line" && connection.relationshipId.isBlank()
                if (!noteLine && (connection.relationshipId.isBlank() || connection.relationshipId !in relationshipIds)) {
                    issues += issue(
                        code = "viewConnectionMissingRelationshipRef",
                        level = "error",
                        entityId = connection.id,
                        viewId = view.id,
                        message =
                            "View connection \"${connection.id}\" points to missing relationship \"${connection.relationshipId}\"",
                    )
                }
                val sourceOk =
                    connection.sourceNodeId in nodeIds || connection.sourceNodeId in connectionIds
                val targetOk =
                    connection.targetNodeId in nodeIds || connection.targetNodeId in connectionIds
                if (!sourceOk) {
                    issues += issue(
                        code = "viewConnectionMissingSourceNode",
                        level = "error",
                        entityId = connection.id,
                        viewId = view.id,
                        message =
                            "View connection \"${connection.id}\" points to missing source node \"${connection.sourceNodeId}\"",
                    )
                }
                if (!targetOk) {
                    issues += issue(
                        code = "viewConnectionMissingTargetNode",
                        level = "error",
                        entityId = connection.id,
                        viewId = view.id,
                        message =
                            "View connection \"${connection.id}\" points to missing target node \"${connection.targetNodeId}\"",
                    )
                }
            }
        }

        return issues
    }

    private fun parseElements(model: Element): List<OefElementDto> {
        val root = directChild(model, "elements") ?: return emptyList()
        return directChildren(root, "element").mapNotNull { el ->
            val id = el.getAttribute("identifier").trim()
            if (id.isEmpty()) null
            else OefElementDto(id = id, type = typeOf(el), name = textOfFirstDirectChild(el, "name"))
        }
    }

    private fun parseRelationships(model: Element): List<OefRelationshipDto> {
        val root = directChild(model, "relationships") ?: return emptyList()
        return directChildren(root, "relationship").mapNotNull { el ->
            val id = el.getAttribute("identifier").trim()
            if (id.isEmpty()) null
            else
                OefRelationshipDto(
                    id = id,
                    type = typeOf(el),
                    sourceElementId = el.getAttribute("source").trim(),
                    targetElementId = el.getAttribute("target").trim(),
                )
        }
    }

    private fun parseViews(model: Element): List<OefViewDto> {
        val viewsRoot = directChild(model, "views") ?: return emptyList()
        val diagramsRoot = directChild(viewsRoot, "diagrams") ?: return emptyList()
        return directChildren(diagramsRoot, "view").mapNotNull { view ->
            val id = view.getAttribute("identifier").trim()
            if (id.isEmpty()) null
            else
                OefViewDto(
                    id = id,
                    type = typeOf(view),
                    name = textOfFirstDirectChild(view, "name"),
                    nodes = parseViewNodes(view),
                    connections = parseViewConnections(view),
                )
        }
    }

    private fun parseViewNodes(view: Element): List<OefViewNodeDto> =
        descendantsByLocalName(view, "node").mapNotNull { el ->
            val id = el.getAttribute("identifier").trim()
            if (id.isEmpty()) return@mapNotNull null
            val nodeType = typeOf(el)
            val width = parseNumber(el.getAttribute("w"))
            val height = parseNumber(el.getAttribute("h"))
            val labelText =
                if (isDiagramOnlyViewNode(nodeType)) {
                    textOfFirstDirectChild(el, "label").ifBlank { textOfFirstDirectChild(el, "name") }
                } else {
                    null
                }
            OefViewNodeDto(
                id = id,
                elementId = el.getAttribute("elementRef").trim(),
                type = nodeType,
                x = parseNumber(el.getAttribute("x")) ?: 0.0,
                y = parseNumber(el.getAttribute("y")) ?: 0.0,
                width = width,
                height = height,
                labelText = labelText?.takeIf { it.isNotBlank() },
            )
        }

    private fun parseViewConnections(view: Element): List<OefViewConnectionDto> =
        descendantsByLocalName(view, "connection").mapNotNull { el ->
            val id = el.getAttribute("identifier").trim()
            if (id.isEmpty()) null
            else
                OefViewConnectionDto(
                    id = id,
                    relationshipId = el.getAttribute("relationshipRef").trim(),
                    sourceNodeId = el.getAttribute("source").trim(),
                    targetNodeId = el.getAttribute("target").trim(),
                    type = typeOf(el),
                )
        }

    private fun isDiagramOnlyViewNode(type: String): Boolean =
        type == "Label" || type == "Note" || type == "Container"

    private fun typeOf(element: Element): String {
        val nsType = element.getAttributeNS(XSI_NS, "type").trim()
        if (nsType.isNotEmpty()) return nsType
        val prefixed = element.getAttribute("xsi:type").trim()
        if (prefixed.isNotEmpty()) return prefixed
        return element.getAttribute("type").trim()
    }

    private fun textOfFirstDirectChild(parent: Element, localName: String): String =
        directChild(parent, localName)?.textContent?.trim().orEmpty()

    private fun directChild(parent: Element, localName: String): Element? =
        directChildren(parent, localName).firstOrNull()

    private fun directChildren(parent: Element, localName: String): List<Element> {
        val out = mutableListOf<Element>()
        var child = parent.firstChild
        while (child != null) {
            if (child.nodeType == Node.ELEMENT_NODE) {
                val el = child as Element
                if (el.localName == localName) out += el
            }
            child = child.nextSibling
        }
        return out
    }

    private fun descendantsByLocalName(parent: Element, localName: String): List<Element> {
        val out = mutableListOf<Element>()
        fun visit(node: Element) {
            var child = node.firstChild
            while (child != null) {
                if (child.nodeType == Node.ELEMENT_NODE) {
                    val el = child as Element
                    if (el.localName == localName) out += el
                    visit(el)
                }
                child = child.nextSibling
            }
        }
        visit(parent)
        return out
    }

    private fun parseNumber(value: String?): Double? {
        if (value.isNullOrBlank()) return null
        return value.toDoubleOrNull()
    }

    private fun duplicateIssues(
        ids: List<String>,
        code: String,
        message: (String) -> String,
    ): List<OefImportIssueDto> {
        val seen = HashSet<String>()
        val duplicates = LinkedHashSet<String>()
        for (id in ids) {
            if (id.isBlank()) continue
            if (!seen.add(id)) duplicates += id
        }
        return duplicates.map { issue(code = code, level = "error", entityId = it, message = message(it)) }
    }

    private fun issue(
        code: String,
        level: String,
        message: String,
        entityId: String? = null,
        viewId: String? = null,
    ): OefImportIssueDto =
        OefImportIssueDto(
            code = code,
            level = level,
            message = message,
            entityId = entityId,
            viewId = viewId,
        )

    companion object {
        private const val XSI_NS = "http://www.w3.org/2001/XMLSchema-instance"
        const val MAX_UPLOAD_BYTES: Long = 100L * 1024L * 1024L
    }
}
