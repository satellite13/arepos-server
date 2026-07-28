package ru.kavader.arepos.service.modelpackage

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import java.util.UUID

class MdFileLinkRewriter(
    private val objectMapper: ObjectMapper = ObjectMapper()
) {
    companion object {
        private val MDFILE_PATTERN = Regex("""mdfile://([0-9a-fA-F-]{36})""")
        private const val DOCUMENT_FILE_ID_FIELD = "documentFileId"
        private const val MAX_DEPTH = 10
    }

    fun extractFileUuids(text: String?): Set<UUID> {
        if (text.isNullOrBlank()) return emptySet()
        return MDFILE_PATTERN.findAll(text)
            .mapNotNull { match ->
                try {
                    UUID.fromString(match.groupValues[1])
                } catch (_: IllegalArgumentException) {
                    null
                }
            }
            .toSet()
    }

    fun rewrite(text: String, fileIdMap: Map<UUID, UUID>): String {
        return MDFILE_PATTERN.replace(text) { match ->
            val id = UUID.fromString(match.groupValues[1])
            val mapped = fileIdMap[id] ?: id
            "mdfile://$mapped"
        }
    }

    fun rewriteAttrsJson(attrs: String?, fileIdMap: Map<UUID, UUID>): String? {
        if (attrs.isNullOrBlank()) return attrs
        val root = objectMapper.readTree(attrs)
        rewriteNode(root, fileIdMap, 0)
        return objectMapper.writeValueAsString(root)
    }

    private fun rewriteNode(node: JsonNode, fileIdMap: Map<UUID, UUID>, depth: Int) {
        if (depth > MAX_DEPTH) return

        when {
            node.isObject -> {
                val objectNode = node as ObjectNode
                if (objectNode.has(DOCUMENT_FILE_ID_FIELD) && objectNode.get(DOCUMENT_FILE_ID_FIELD).isTextual) {
                    val current = objectNode.get(DOCUMENT_FILE_ID_FIELD).asText()
                    try {
                        val id = UUID.fromString(current)
                        val mapped = fileIdMap[id] ?: id
                        objectNode.put(DOCUMENT_FILE_ID_FIELD, mapped.toString())
                    } catch (_: IllegalArgumentException) {
                        // keep as-is
                    }
                }
                val fieldNames = objectNode.fieldNames().asSequence().toList()
                for (fieldName in fieldNames) {
                    val value = objectNode.get(fieldName) ?: continue
                    if (value.isTextual) {
                        val rewritten = rewrite(value.asText(), fileIdMap)
                        if (rewritten != value.asText()) {
                            objectNode.put(fieldName, rewritten)
                        }
                    } else {
                        rewriteNode(value, fileIdMap, depth + 1)
                    }
                }
            }

            node.isArray -> {
                val arrayNode = node as ArrayNode
                for (i in 0 until arrayNode.size()) {
                    val element = arrayNode.get(i)
                    if (element.isTextual) {
                        val rewritten = rewrite(element.asText(), fileIdMap)
                        if (rewritten != element.asText()) {
                            arrayNode.set(i, rewritten)
                        }
                    } else {
                        rewriteNode(element, fileIdMap, depth + 1)
                    }
                }
            }
        }
    }
}
