package ru.kavader.arepos.service

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.StringReader
import java.io.StringWriter
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import org.xml.sax.InputSource

@Service
class SvgSanitizer {
    fun sanitize(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "SVG must not be blank")
        }
        if (trimmed.length > MAX_BYTES) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "SVG exceeds $MAX_BYTES bytes")
        }
        if (DOCTYPE_OR_ENTITY.containsMatchIn(trimmed)) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "SVG must not contain DOCTYPE or ENTITY")
        }
        if (!trimmed.contains("<svg", ignoreCase = true)) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "File is not a valid SVG")
        }

        val document = parseSecurely(trimmed)
        val root = document.documentElement
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "File is not a valid SVG")
        if (!root.tagName.equals("svg", ignoreCase = true) &&
            !root.localName.equals("svg", ignoreCase = true)
        ) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Root element must be svg")
        }
        sanitizeElement(root)
        return serialize(document)
    }

    fun contentHash(svg: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(svg.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun parseSecurely(raw: String): Document {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false)
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
        factory.isExpandEntityReferences = false
        factory.isXIncludeAware = false
        val builder = factory.newDocumentBuilder()
        return try {
            builder.parse(InputSource(StringReader(raw)))
        } catch (ex: Exception) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid SVG XML")
        }
    }

    private fun sanitizeElement(element: Element) {
        val local = element.localName ?: element.tagName
        val name = local.substringAfterLast(':').lowercase()
        if (name !in ALLOWED_ELEMENTS) {
            val parent = element.parentNode
            parent?.removeChild(element)
            return
        }
        val toRemove = mutableListOf<org.w3c.dom.Attr>()
        val attributes = element.attributes
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i) as org.w3c.dom.Attr
            if (!isAllowedAttribute(attr)) {
                toRemove.add(attr)
            }
        }
        toRemove.forEach { element.removeAttributeNode(it) }

        val children = mutableListOf<Node>()
        var child = element.firstChild
        while (child != null) {
            children.add(child)
            child = child.nextSibling
        }
        for (node in children) {
            when (node.nodeType) {
                Node.ELEMENT_NODE -> sanitizeElement(node as Element)
                Node.COMMENT_NODE, Node.PROCESSING_INSTRUCTION_NODE -> element.removeChild(node)
                Node.TEXT_NODE, Node.CDATA_SECTION_NODE -> Unit
                else -> element.removeChild(node)
            }
        }
    }

    private fun isAllowedAttribute(attr: org.w3c.dom.Attr): Boolean {
        val rawName = attr.localName ?: attr.name
        val name = rawName.substringAfterLast(':').lowercase()
        if (name.startsWith("on")) return false
        if (name == "xmlns" || attr.name.startsWith("xmlns")) return true
        if (name !in ALLOWED_ATTRIBUTES) return false
        val value = attr.value.trim()
        if (EVENT_OR_JS.containsMatchIn(value)) return false
        if (name == "href" || name == "xlink:href" || rawName.equals("xlink:href", ignoreCase = true)) {
            return value.startsWith("#")
        }
        if (name == "style") return false
        return true
    }

    private fun serialize(document: Document): String {
        val transformer = TransformerFactory.newInstance().newTransformer()
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes")
        transformer.setOutputProperty(OutputKeys.INDENT, "no")
        val writer = StringWriter()
        transformer.transform(DOMSource(document), StreamResult(writer))
        return writer.toString().trim()
    }

    companion object {
        const val MAX_BYTES = 102_400
        private val DOCTYPE_OR_ENTITY = Regex("""<!DOCTYPE|<!ENTITY""", RegexOption.IGNORE_CASE)
        private val EVENT_OR_JS = Regex("""javascript\s*:|data\s*:""", RegexOption.IGNORE_CASE)
        private val ALLOWED_ELEMENTS = setOf(
            "svg", "g", "path", "circle", "ellipse", "rect", "line", "polyline", "polygon",
            "defs", "clippath", "mask", "title", "desc", "use"
        )
        private val ALLOWED_ATTRIBUTES = setOf(
            "viewbox", "width", "height", "fill", "stroke", "fill-rule", "fill-opacity",
            "stroke-width", "stroke-linecap", "stroke-linejoin", "stroke-opacity", "stroke-dasharray",
            "d", "cx", "cy", "r", "rx", "ry", "x", "y", "x1", "y1", "x2", "y2", "points",
            "transform", "opacity", "clip-path", "mask", "id", "href", "xlink:href",
            "preserveaspectratio", "xmlns"
        )
    }
}
