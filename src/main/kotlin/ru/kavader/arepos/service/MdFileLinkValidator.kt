package ru.kavader.arepos.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
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
        private val MDFILE_PATTERN = Regex("""mdfile://([0-9a-fA-F-]{36})""")
        private const val MAX_DEPTH = 10
    }

    /**
     * Validates all mdfile:// references in the given attrs JSON string.
     * Throws ResponseStatusException if any referenced file does not exist.
     */
    fun validate(attrs: String?) {
        if (attrs.isNullOrBlank()) return
        
        val uuids = extractFileUuids(attrs)
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
        
        return try {
            val root = objectMapper.readTree(attrs)
            val uuids = mutableSetOf<UUID>()
            extractUuidsFromNode(root, uuids, 0)
            uuids
        } catch (e: Exception) {
            // If JSON is invalid, try to extract UUIDs directly from the string
            extractUuidsFromString(attrs)
        }
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

    private fun extractUuidsFromString(text: String): Set<UUID> {
        val uuids = mutableSetOf<UUID>()
        val matches = MDFILE_PATTERN.findAll(text)
        matches.forEach { match ->
            try {
                uuids.add(UUID.fromString(match.groupValues[1]))
            } catch (e: IllegalArgumentException) {
                // Invalid UUID format, ignore
            }
        }
        return uuids
    }

    /**
     * Checks if the given string contains any mdfile:// references.
     */
    fun containsMdFileRefs(attrs: String?): Boolean {
        if (attrs.isNullOrBlank()) return false
        return MDFILE_PATTERN.containsMatchIn(attrs)
    }
}
