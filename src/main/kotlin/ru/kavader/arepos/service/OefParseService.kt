package ru.kavader.arepos.service

import org.springframework.stereotype.Service
import ru.kavader.arepos.dto.oef.*
import java.io.ByteArrayInputStream
import java.io.InputStream
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants
import javax.xml.stream.XMLStreamException
import javax.xml.stream.XMLStreamReader

/**
 * Streaming OEF (Open Exchange) parser. Uses StAX instead of DOM so large ArchiMate
 * exports do not inflate a full document tree into the heap (DOM OOM with ~1 Gi limit).
 */
@Service
class OefParseService {

    fun parseAndValidate(xmlBytes: ByteArray): OefNormalizeResponse {
        val parsed = parse(xmlBytes)
        val issues = validate(parsed)
        return parsed.copy(issues = issues)
    }

    fun parseAndValidate(input: InputStream): OefNormalizeResponse {
        val parsed = parse(input)
        val issues = validate(parsed)
        return parsed.copy(issues = issues)
    }

    fun parse(xmlBytes: ByteArray): OefNormalizeResponse =
        parse(ByteArrayInputStream(xmlBytes))

    fun parse(input: InputStream): OefNormalizeResponse {
        val reader = try {
            newSafeReader(input)
        } catch (ex: Exception) {
            throw IllegalArgumentException("Invalid OEF XML: ${ex.message ?: "parse error"}")
        }

        try {
            return parseWithReader(reader)
        } catch (ex: XMLStreamException) {
            throw IllegalArgumentException("Invalid OEF XML: ${ex.message ?: "parse error"}")
        } catch (ex: IllegalArgumentException) {
            throw ex
        } catch (ex: Exception) {
            throw IllegalArgumentException("Invalid OEF XML: ${ex.message ?: "parse error"}")
        } finally {
            try {
                reader.close()
            } catch (_: Exception) {
                // ignore close failures
            }
        }
    }

    private fun parseWithReader(reader: XMLStreamReader): OefNormalizeResponse {
        var sawRoot = false
        var modelId = ""
        var modelName = ""
        var modelNameCaptured = false

        val elements = mutableListOf<OefElementDto>()
        val relationships = mutableListOf<OefRelationshipDto>()
        val views = mutableListOf<OefViewDto>()

        // Path of open element local names (for section detection).
        val path = ArrayDeque<String>()

        var currentElement: MutableElement? = null
        var currentRelationship: MutableRelationship? = null
        var currentView: MutableView? = null
        val openNodes = ArrayDeque<MutableViewNode>()
        var currentConnection: MutableConnection? = null

        // Text capture for direct-child name/label of the current entity.
        var textTarget: TextTarget? = null
        val textBuf = StringBuilder()

        while (reader.hasNext()) {
            when (reader.next()) {
                XMLStreamConstants.START_ELEMENT -> {
                    val local = reader.localName
                    path.addLast(local)

                    if (!sawRoot) {
                        sawRoot = true
                        if (local != "model") {
                            throw IllegalArgumentException("Invalid OEF XML: missing <model>")
                        }
                        modelId = attr(reader, "identifier")
                    }

                    when {
                        pathEquals(path, "model", "name") && !modelNameCaptured -> {
                            textTarget = TextTarget.MODEL_NAME
                            textBuf.setLength(0)
                        }

                        pathEquals(path, "model", "elements", "element") -> {
                            val id = attr(reader, "identifier")
                            if (id.isNotEmpty()) {
                                currentElement = MutableElement(id = id, type = typeOf(reader))
                            }
                        }

                        pathEquals(path, "model", "elements", "element", "name") &&
                            currentElement != null -> {
                            textTarget = TextTarget.ELEMENT_NAME
                            textBuf.setLength(0)
                        }

                        pathEquals(path, "model", "relationships", "relationship") -> {
                            val id = attr(reader, "identifier")
                            if (id.isNotEmpty()) {
                                currentRelationship =
                                    MutableRelationship(
                                        id = id,
                                        type = typeOf(reader),
                                        sourceElementId = attr(reader, "source"),
                                        targetElementId = attr(reader, "target"),
                                    )
                            }
                        }

                        pathEquals(path, "model", "views", "diagrams", "view") -> {
                            val id = attr(reader, "identifier")
                            if (id.isNotEmpty()) {
                                currentView =
                                    MutableView(
                                        id = id,
                                        type = typeOf(reader),
                                    )
                            }
                        }

                        pathEquals(path, "model", "views", "diagrams", "view", "name") &&
                            currentView != null &&
                            currentView!!.name.isEmpty() -> {
                            textTarget = TextTarget.VIEW_NAME
                            textBuf.setLength(0)
                        }

                        inView(path) && local == "node" && currentView != null -> {
                            val id = attr(reader, "identifier")
                            if (id.isNotEmpty()) {
                                val node =
                                    MutableViewNode(
                                        id = id,
                                        elementId = attr(reader, "elementRef"),
                                        type = typeOf(reader),
                                        x = parseNumber(attr(reader, "x")) ?: 0.0,
                                        y = parseNumber(attr(reader, "y")) ?: 0.0,
                                        width = parseNumber(attr(reader, "w")),
                                        height = parseNumber(attr(reader, "h")),
                                    )
                                // Document order: parent before nested children (matches DOM walk).
                                openNodes.addLast(node)
                                currentView!!.mutableNodes += node
                            }
                        }

                        inView(path) && local == "connection" && currentView != null -> {
                            val id = attr(reader, "identifier")
                            if (id.isNotEmpty()) {
                                currentConnection =
                                    MutableConnection(
                                        id = id,
                                        relationshipId = attr(reader, "relationshipRef"),
                                        sourceNodeId = attr(reader, "source"),
                                        targetNodeId = attr(reader, "target"),
                                        type = typeOf(reader),
                                    )
                            }
                        }

                        openNodes.isNotEmpty() && (local == "label" || local == "name") &&
                            isDirectChildOfCurrentNode(path, local) -> {
                            val node = openNodes.last()
                            if (isDiagramOnlyViewNode(node.type)) {
                                textTarget =
                                    if (local == "label") TextTarget.NODE_LABEL else TextTarget.NODE_NAME
                                textBuf.setLength(0)
                            }
                        }
                    }
                }

                XMLStreamConstants.CHARACTERS, XMLStreamConstants.CDATA -> {
                    if (textTarget != null) {
                        textBuf.append(reader.text)
                    }
                }

                XMLStreamConstants.END_ELEMENT -> {
                    val local = reader.localName

                    when (textTarget) {
                        TextTarget.MODEL_NAME -> {
                            if (local == "name") {
                                modelName = textBuf.toString().trim()
                                modelNameCaptured = true
                                textTarget = null
                            }
                        }
                        TextTarget.ELEMENT_NAME -> {
                            if (local == "name") {
                                currentElement?.name = textBuf.toString().trim()
                                textTarget = null
                            }
                        }
                        TextTarget.VIEW_NAME -> {
                            if (local == "name") {
                                currentView?.name = textBuf.toString().trim()
                                textTarget = null
                            }
                        }
                        TextTarget.NODE_LABEL -> {
                            if (local == "label") {
                                openNodes.lastOrNull()?.labelText = textBuf.toString().trim()
                                textTarget = null
                            }
                        }
                        TextTarget.NODE_NAME -> {
                            if (local == "name") {
                                openNodes.lastOrNull()?.nameText = textBuf.toString().trim()
                                textTarget = null
                            }
                        }
                        null -> Unit
                    }

                    when {
                        local == "element" && pathEquals(path, "model", "elements", "element") -> {
                            currentElement?.let { elements += it.toDto() }
                            currentElement = null
                        }
                        local == "relationship" &&
                            pathEquals(path, "model", "relationships", "relationship") -> {
                            currentRelationship?.let { relationships += it.toDto() }
                            currentRelationship = null
                        }
                        local == "node" && inView(path) && openNodes.isNotEmpty() -> {
                            openNodes.removeLast()
                        }
                        local == "connection" && inView(path) && currentConnection != null -> {
                            currentView?.mutableConnections?.add(currentConnection!!)
                            currentConnection = null
                        }
                        local == "view" && pathEquals(path, "model", "views", "diagrams", "view") -> {
                            currentView?.let { views += it.toDto() }
                            currentView = null
                        }
                    }

                    if (path.isNotEmpty() && path.last() == local) {
                        path.removeLast()
                    }
                }
            }
        }

        if (!sawRoot) {
            throw IllegalArgumentException("Invalid OEF XML: missing <model>")
        }

        return OefNormalizeResponse(
            model = OefModelDto(id = modelId, name = modelName),
            elements = elements,
            relationships = relationships,
            views = views,
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

    private fun newSafeReader(input: InputStream): XMLStreamReader {
        val factory = XMLInputFactory.newFactory()
        fun safeProperty(name: String, value: Any) {
            try {
                factory.setProperty(name, value)
            } catch (_: Exception) {
                // Property may be unsupported on some XML stacks.
            }
        }
        safeProperty(XMLInputFactory.SUPPORT_DTD, false)
        safeProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false)
        return factory.createXMLStreamReader(input)
    }

    private fun attr(reader: XMLStreamReader, localName: String): String {
        val direct = reader.getAttributeValue(null, localName)
        if (direct != null) return direct.trim()
        for (i in 0 until reader.attributeCount) {
            if (reader.getAttributeLocalName(i) == localName) {
                return reader.getAttributeValue(i).trim()
            }
        }
        return ""
    }

    private fun typeOf(reader: XMLStreamReader): String {
        val nsType = reader.getAttributeValue(XSI_NS, "type")?.trim().orEmpty()
        if (nsType.isNotEmpty()) return nsType
        for (i in 0 until reader.attributeCount) {
            val local = reader.getAttributeLocalName(i)
            if (local != "type") continue
            val prefix = reader.getAttributePrefix(i).orEmpty()
            if (prefix == "xsi") return reader.getAttributeValue(i).trim()
        }
        return attr(reader, "type")
    }

    private fun pathEquals(path: ArrayDeque<String>, vararg expected: String): Boolean {
        if (path.size != expected.size) return false
        var i = 0
        for (segment in path) {
            if (segment != expected[i]) return false
            i++
        }
        return true
    }

    private fun inView(path: ArrayDeque<String>): Boolean {
        // model / views / diagrams / view / …
        if (path.size < 4) return false
        val it = path.iterator()
        if (it.next() != "model") return false
        if (it.next() != "views") return false
        if (it.next() != "diagrams") return false
        return it.next() == "view"
    }

    private fun isDirectChildOfCurrentNode(path: ArrayDeque<String>, childLocal: String): Boolean {
        if (path.size < 2) return false
        if (path.last() != childLocal) return false
        // … / node / label|name
        val parent = path.elementAt(path.size - 2)
        return parent == "node"
    }

    private fun isDiagramOnlyViewNode(type: String): Boolean =
        type == "Label" || type == "Note" || type == "Container"

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

    private enum class TextTarget {
        MODEL_NAME,
        ELEMENT_NAME,
        VIEW_NAME,
        NODE_LABEL,
        NODE_NAME,
    }

    private class MutableElement(
        val id: String,
        val type: String,
        var name: String = "",
    ) {
        fun toDto(): OefElementDto = OefElementDto(id = id, type = type, name = name)
    }

    private class MutableRelationship(
        val id: String,
        val type: String,
        val sourceElementId: String,
        val targetElementId: String,
    ) {
        fun toDto(): OefRelationshipDto =
            OefRelationshipDto(
                id = id,
                type = type,
                sourceElementId = sourceElementId,
                targetElementId = targetElementId,
            )
    }

    private class MutableView(
        val id: String,
        val type: String,
        var name: String = "",
        val mutableNodes: MutableList<MutableViewNode> = mutableListOf(),
        val mutableConnections: MutableList<MutableConnection> = mutableListOf(),
    ) {
        fun toDto(): OefViewDto =
            OefViewDto(
                id = id,
                type = type,
                name = name,
                nodes = mutableNodes.map { it.toDto() },
                connections = mutableConnections.map { it.toDto() },
            )
    }

    private class MutableViewNode(
        val id: String,
        val elementId: String,
        val type: String,
        val x: Double,
        val y: Double,
        val width: Double?,
        val height: Double?,
        var labelText: String? = null,
        var nameText: String? = null,
    ) {
        fun toDto(): OefViewNodeDto {
            val label =
                if (type == "Label" || type == "Note" || type == "Container") {
                    labelText?.takeIf { it.isNotBlank() }
                        ?: nameText?.takeIf { it.isNotBlank() }
                } else {
                    null
                }
            return OefViewNodeDto(
                id = id,
                elementId = elementId,
                type = type,
                x = x,
                y = y,
                width = width,
                height = height,
                labelText = label,
            )
        }
    }

    private class MutableConnection(
        val id: String,
        val relationshipId: String,
        val sourceNodeId: String,
        val targetNodeId: String,
        val type: String,
    ) {
        fun toDto(): OefViewConnectionDto =
            OefViewConnectionDto(
                id = id,
                relationshipId = relationshipId,
                sourceNodeId = sourceNodeId,
                targetNodeId = targetNodeId,
                type = type,
            )
    }

    companion object {
        private const val XSI_NS = "http://www.w3.org/2001/XMLSchema-instance"
        const val MAX_UPLOAD_BYTES: Long = 100L * 1024L * 1024L
    }
}
