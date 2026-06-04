package ru.kavader.arepos.util

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.JsonNode
import ru.kavader.arepos.dto.model.BatchDeleteEntry
import java.time.Instant
import java.util.*

/**
 * Поддерживает старый формат `["uuid", ...]` и объектный `{ "id", "baseUpdatedAt?" }`.
 */
class BatchDeleteListDeserializer : JsonDeserializer<List<BatchDeleteEntry>>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): List<BatchDeleteEntry> {
        val node: JsonNode = p.codec.readTree(p) ?: return emptyList()
        if (!node.isArray) return emptyList()
        val out = ArrayList<BatchDeleteEntry>(node.size())
        for (el in node) {
            when {
                el.isTextual -> out.add(BatchDeleteEntry(UUID.fromString(el.asText()), null))
                el.isObject -> {
                    val idNode = el.get("id") ?: throw JsonMappingException.from(p, "delete entry missing id")
                    val id = UUID.fromString(idNode.asText())
                    val baseNode = el.get("baseUpdatedAt")
                    val base = when {
                        baseNode == null || baseNode.isNull -> null
                        baseNode.isTextual -> Instant.parse(baseNode.asText())
                        else -> throw JsonMappingException.from(p, "baseUpdatedAt must be ISO-8601 string")
                    }
                    out.add(BatchDeleteEntry(id, base))
                }

                else -> throw JsonMappingException.from(p, "delete entry must be uuid string or object with id")
            }
        }
        return out
    }
}
