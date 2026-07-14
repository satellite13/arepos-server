package ru.kavader.arepos.service

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.*

@Service
class ModelAttrsService(
    private val objectMapper: ObjectMapper
) {
    companion object {
        private val log = LoggerFactory.getLogger(ModelAttrsService::class.java)
    }

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
        } catch (ex: JsonProcessingException) {
            log.warn("Invalid model attrs JSON; replacing it with an empty object", ex)
            objectMapper.createObjectNode()
        }
}
