package ru.kavader.arepos.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.springframework.stereotype.Service
import java.util.*

@Service
class ModelAttrsService(
    private val objectMapper: ObjectMapper
) {
    fun mergeWithTreeRootNodeId(existingAttrs: String?, rootNodeId: UUID): String {
        val baseNode = parseAttrsObjectOrEmpty(existingAttrs)
        baseNode.put("treeRootNodeId", rootNodeId.toString())
        return objectMapper.writeValueAsString(baseNode)
    }

    private fun parseAttrsObjectOrEmpty(existingAttrs: String?): ObjectNode =
        try {
            existingAttrs
                ?.takeIf { it.isNotBlank() }
                ?.let { objectMapper.readTree(it) }
                ?.takeIf { it.isObject }
                ?.deepCopy()
                ?: objectMapper.createObjectNode()
        } catch (_: Exception) {
            objectMapper.createObjectNode()
        }
}
