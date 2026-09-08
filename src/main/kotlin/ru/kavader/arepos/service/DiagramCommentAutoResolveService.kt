package ru.kavader.arepos.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.kavader.arepos.model.Diagrams
import ru.kavader.arepos.repository.DiagramCommentRepository
import java.time.Instant

/**
 * Авто-resolve тредов комментариев при удалении элементов с холста:
 * diff старых/новых `instances.nodes[].id` и `instances.edges[].id`.
 * Вызывается из точек сохранения attrs диаграммы (PUT /diagrams, instances:merge, batch-save).
 */
@Service
class DiagramCommentAutoResolveService(
    private val commentRepository: DiagramCommentRepository,
    private val objectMapper: ObjectMapper,
    private val commentEventBroadcaster: CommentEventBroadcaster
) {
    fun extractNodeInstanceIds(attrs: String?): Set<String> = extractInstanceIds(attrs, "nodes")
    fun extractEdgeInstanceIds(attrs: String?): Set<String> = extractInstanceIds(attrs, "edges")

    fun extractInstanceIds(attrs: String?, container: String): Set<String> {
        if (attrs.isNullOrBlank()) return emptySet()
        return try {
            val root = objectMapper.readTree(attrs)
            val ids = linkedSetOf<String>()
            fun scan(containerNode: JsonNode?) {
                val arr = containerNode?.get(container) ?: return
                if (!arr.isArray) return
                for (el in arr) {
                    val raw = el.path("id").asText(null) ?: continue
                    if (raw.isNotBlank()) ids.add(raw)
                }
            }
            scan(root)
            scan(root.get("instances"))
            ids
        } catch (_: Exception) {
            emptySet()
        }
    }

    /**
     * Помечает resolved треды, привязанные к инстансам, отсутствующим в новых attrs.
     * @return число resolve'нутых тредов
     */
    @Transactional
    fun autoResolveRemovedInstances(diagram: Diagrams, oldAttrs: String?, newAttrs: String?): Int {
        val diagramId = diagram.id ?: return 0
        if (oldAttrs == newAttrs) return 0
        val before = extractNodeInstanceIds(oldAttrs) + extractEdgeInstanceIds(oldAttrs)
        val after = extractNodeInstanceIds(newAttrs) + extractEdgeInstanceIds(newAttrs)
        val removed = before - after
        if (removed.isEmpty()) return 0

        val resolved = commentRepository.resolveByRemovedInstanceIds(diagramId, removed, Instant.now())
        if (resolved > 0) {
            log.info(
                "Auto-resolved {} comment thread(s) for removed canvas instances (diagramId={}, instances={})",
                resolved, diagramId, removed
            )
            commentEventBroadcaster.broadcastCommentEvent(diagram, "resolved", null)
        }
        return resolved
    }

    companion object {
        private val log = LoggerFactory.getLogger(DiagramCommentAutoResolveService::class.java)
    }
}
