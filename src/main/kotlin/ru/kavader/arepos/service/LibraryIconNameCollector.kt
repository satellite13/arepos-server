package ru.kavader.arepos.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Component

@Component
class LibraryIconNameCollector(
    private val objectMapper: ObjectMapper
) {
    fun collectFromJson(vararg json: String?): Set<String> {
        val names = linkedSetOf<String>()
        for (raw in json) {
            if (raw.isNullOrBlank()) continue
            try {
                walk(objectMapper.readTree(raw), names)
            } catch (_: Exception) {
                continue
            }
        }
        return names
    }

    fun collectFromNode(node: JsonNode?): Set<String> {
        val names = linkedSetOf<String>()
        if (node != null) walk(node, names)
        return names
    }

    private fun walk(node: JsonNode, names: MutableSet<String>) {
        when {
            node.isObject -> {
                for (entry in node.properties()) {
                    val key = entry.key
                    val value = entry.value
                    if (key in NAME_KEYS && value.isTextual) {
                        addName(value.asText(), names)
                    } else if (key == "source" && value.isTextual) {
                        addFromSource(value.asText(), names)
                    } else {
                        walk(value, names)
                    }
                }
            }
            node.isArray -> node.forEach { walk(it, names) }
            node.isTextual -> addFromSource(node.asText(), names)
        }
    }

    private fun addFromSource(value: String, names: MutableSet<String>) {
        val match = ICON_PATH.find(value.trim())
        if (match != null) {
            addName(match.groupValues[1], names)
        }
    }

    private fun addName(raw: String, names: MutableSet<String>) {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return
        val fromPath = ICON_PATH.find(trimmed)
        val name = (fromPath?.groupValues?.get(1) ?: trimmed)
            .removeSuffix(".svg")
            .lowercase()
        if (name.isNotEmpty()) names.add(name)
    }

    companion object {
        private val NAME_KEYS = setOf(
            "iconName",
            "paletteMaterialIcon",
            "icon",
            "interactiveIcon"
        )
        private val ICON_PATH = Regex("""(?:^|/)icons/([^/]+?)\.svg$""", RegexOption.IGNORE_CASE)
    }
}
