package ru.kavader.arepos.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.repository.FilesRepository
import java.util.*

@Service
class MdFileLinkValidator(
    private val filesRepository: FilesRepository,
    private val objectMapper: ObjectMapper
) {
    companion object {
        private val log = LoggerFactory.getLogger(MdFileLinkValidator::class.java)
        private val MDFILE_PATTERN = Regex("""mdfile://([0-9a-fA-F-]{36})""")
        private const val MAX_DEPTH = 10
        private const val MAX_ATTRS_BYTES = 1 * 1024 * 1024
    }

    /**
     * Validates all mdfile:// references in the given attrs JSON string.
     * Throws ResponseStatusException if any referenced file does not exist.
     */
    fun validate(attrs: String?) {
        if (attrs.isNullOrBlank()) return

        val root = parseSafe(attrs)
        val uuids = mutableSetOf<UUID>()
        extractUuidsFromNode(root, uuids, 0)
        if (uuids.isEmpty()) return

        // Check all files exist
        val missingUuids = uuids.filter { uuid ->
            !filesRepository.existsById(uuid)
        }

        if (missingUuids.isNotEmpty()) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Referenced file(s) not found: ${missingUuids.joinToString(", ")}"
            )
        }
    }

    /**
     * Extracts all mdfile:// UUIDs from a JSON string.
     * Recursively searches through the JSON structure.
     */
    fun extractFileUuids(attrs: String?): Set<UUID> {
        if (attrs.isNullOrBlank()) return emptySet()
        val root = parseSafe(attrs)
        val uuids = mutableSetOf<UUID>()
        extractUuidsFromNode(root, uuids, 0)
        return uuids
    }

    private fun extractUuidsFromNode(node: JsonNode, uuids: MutableSet<UUID>, depth: Int) {
        if (depth > MAX_DEPTH) return

        when {
            node.isTextual -> {
                val text = node.asText()
                val matches = MDFILE_PATTERN.findAll(text)
                matches.forEach { match ->
                    try {
                        uuids.add(UUID.fromString(match.groupValues[1]))
                    } catch (e: IllegalArgumentException) {
                        // Invalid UUID format, ignore
                    }
                }
            }

            node.isObject -> {
                node.properties().forEach { (_, value) ->
                    extractUuidsFromNode(value, uuids, depth + 1)
                }
            }

            node.isArray -> {
                node.forEach { element ->
                    extractUuidsFromNode(element, uuids, depth + 1)
                }
            }
        }
    }

    private fun parseSafe(attrs: String): JsonNode {
        if (attrs.toByteArray(Charsets.UTF_8).size > MAX_ATTRS_BYTES) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Attrs payload is too large for mdfile validation (max $MAX_ATTRS_BYTES bytes)"
            )
        }
        return try {
            objectMapper.readTree(attrs)
        } catch (ex: Exception) {
            log.warn("Invalid attrs JSON for mdfile validation", ex)
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Invalid attrs JSON for mdfile validation"
            )
        }
    }
}
