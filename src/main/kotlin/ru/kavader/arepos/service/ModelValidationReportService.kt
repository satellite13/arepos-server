package ru.kavader.arepos.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.dto.model.DiagramIssue
import ru.kavader.arepos.dto.model.DiagramIssueGroup
import ru.kavader.arepos.dto.model.DeleteUnusedRequest
import ru.kavader.arepos.dto.model.DeleteUnusedResponse
import ru.kavader.arepos.dto.model.DuplicateLinkGroup
import ru.kavader.arepos.dto.model.DuplicateLinkMember
import ru.kavader.arepos.dto.model.DuplicateNodeGroup
import ru.kavader.arepos.dto.model.DuplicateNodeMember
import ru.kavader.arepos.dto.model.UnusedLink
import ru.kavader.arepos.dto.model.UnusedNode
import ru.kavader.arepos.dto.model.ValidationReportResponse
import ru.kavader.arepos.dto.system.ModelSyncEntityEvent
import ru.kavader.arepos.dto.system.ModelSyncEventType
import ru.kavader.arepos.model.Diagrams
import ru.kavader.arepos.repository.DiagramsRepository
import ru.kavader.arepos.repository.DuplicateLinkMemberProjection
import ru.kavader.arepos.repository.DuplicateNodeMemberProjection
import ru.kavader.arepos.repository.RefUsageProjection
import ru.kavader.arepos.repository.LinksRepository
import ru.kavader.arepos.repository.ModelsRepository
import ru.kavader.arepos.repository.NodesRepository
import ru.kavader.arepos.security.ResourceAccessService
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * Отчёт валидации модели: дубликаты нод/связей (SQL) + целостность диаграмм
 * (правила, перенесённые из Archi-плагина ModelCheckerLemana: dangling refs,
 * концы визуальных связей против концов модельной связи, дубли instance id).
 */
@Service
class ModelValidationReportService(
    private val modelsRepository: ModelsRepository,
    private val accessService: ResourceAccessService,
    private val nodesRepository: NodesRepository,
    private val linksRepository: LinksRepository,
    private val diagramsRepository: DiagramsRepository,
    private val modelSyncBroadcaster: ModelSyncBroadcaster,
    private val objectMapper: ObjectMapper,
    @Value("\${arepos.validation-report.max-groups:200}")
    private val maxGroups: Int,
    @Value("\${arepos.validation-report.max-members-per-group:500}")
    private val maxMembersPerGroup: Int,
    @Value("\${arepos.validation-report.max-diagrams:500}")
    private val maxDiagrams: Int,
    @Value("\${arepos.validation-report.max-issues-per-diagram:50}")
    private val maxIssuesPerDiagram: Int,
    @Value("\${arepos.validation-report.max-unused:500}")
    private val maxUnused: Int,
    @Value("\${arepos.validation-report.excluded-view-folders:_СоМ (Соглашение о Моделировании)}")
    private val excludedViewFolders: String
) {
    fun report(modelId: UUID): ValidationReportResponse {
        val model = modelsRepository.findById(modelId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Model $modelId not found")
        }
        accessService.requireCanViewModel(model)
        val nodeRows = nodesRepository.findDuplicateNodeMembers(modelId, maxGroups, maxMembersPerGroup)
        val linkRows = linksRepository.findDuplicateLinkMembers(modelId, maxGroups, maxMembersPerGroup)
        val excludedFolders = excludedFolderNames()
        val (nodeGroups, linkGroups) = assembleAnnotatedGroups(modelId, nodeRows, linkRows, excludedFolders)
        val excludedDiagramIds = excludedDiagramIds(modelId, excludedFolders)
        val (diagramIssues, diagramIssuesTotal) = diagramIntegrityIssues(modelId, excludedDiagramIds)
        val (unusedNodes, unusedNodesTotal) = unusedNodes(modelId)
        val (unusedLinks, unusedLinksTotal) = unusedLinks(modelId)
        return ValidationReportResponse(
            modelId = model.id!!,
            generatedAt = Instant.now(),
            duplicateNodes = nodeGroups,
            duplicateLinks = linkGroups,
            duplicateNodesTotal = nodeRows.firstOrNull()?.getTotalGroups()?.toInt() ?: 0,
            duplicateLinksTotal = linkRows.firstOrNull()?.getTotalGroups()?.toInt() ?: 0,
            diagramIssues = diagramIssues,
            diagramIssuesTotal = diagramIssuesTotal,
            unusedNodes = unusedNodes,
            unusedLinks = unusedLinks,
            unusedNodesTotal = unusedNodesTotal,
            unusedLinksTotal = unusedLinksTotal
        )
    }

    /**
     * Узлы-сироты: не размещены ни на одной активной диаграмме (любой версии,
     * включая СоМ), без связей, детей, привязанных диаграмм и документов.
     */
    private fun unusedNodes(modelId: UUID): Pair<List<UnusedNode>, Int> {
        val total = nodesRepository.countUnusedNodes(modelId).toInt()
        if (total == 0) return emptyList<UnusedNode>() to 0
        val rows = nodesRepository.findUnusedNodes(modelId, maxUnused)
        return rows.map { row ->
            UnusedNode(
                id = row.getId(),
                name = row.getName(),
                parentId = row.getParentId(),
                parentName = row.getParentName()
            )
        } to total
    }

    /** Связи, не размещённые ни на одной активной диаграмме (любой версии). */
    private fun unusedLinks(modelId: UUID): Pair<List<UnusedLink>, Int> {
        val total = linksRepository.countUnusedLinks(modelId).toInt()
        if (total == 0) return emptyList<UnusedLink>() to 0
        val rows = linksRepository.findUnusedLinks(modelId, maxUnused)
        return rows.map { row ->
            UnusedLink(
                id = row.getId(),
                sourceName = row.getSourceName(),
                targetName = row.getTargetName(),
                linkTypeName = row.getLinkTypeName()
            )
        } to total
    }

    /**
     * Удаляет неиспользуемые элементы. Каждый id заново проверяется критериями
     * сироты перед удалением; связи удаляются раньше узлов.
     */
    @Transactional
    fun deleteUnused(modelId: UUID, request: DeleteUnusedRequest): DeleteUnusedResponse {
        val model = modelsRepository.findById(modelId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Model $modelId not found")
        }
        accessService.requireCanEditModel(model)
        if (request.nodeIds.size > maxUnused || request.linkIds.size > maxUnused) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Too many ids; limit is $maxUnused per call"
            )
        }

        val deletedNodeIds = if (request.nodeIds.isEmpty()) {
            emptyList()
        } else {
            nodesRepository.findUnusedNodeIds(modelId, request.nodeIds)
        }
        val deletedLinkIds = if (request.linkIds.isEmpty()) {
            emptyList()
        } else {
            linksRepository.findUnusedLinkIds(modelId, request.linkIds)
        }

        if (deletedLinkIds.isNotEmpty()) {
            linksRepository.deleteAllByIdInBatch(deletedLinkIds)
        }
        if (deletedNodeIds.isNotEmpty()) {
            nodesRepository.deleteAllByIdInBatch(deletedNodeIds)
        }

        if (deletedLinkIds.isNotEmpty() || deletedNodeIds.isNotEmpty()) {
            val events = mutableListOf<ModelSyncEntityEvent>()
            for (linkId in deletedLinkIds) {
                events.add(
                    ModelSyncEntityEvent(
                        ModelSyncEventType.LINK_DELETED.wireValue,
                        ModelSyncEventType.LINK_DELETED.entity,
                        linkId
                    )
                )
            }
            for (nodeId in deletedNodeIds) {
                events.add(
                    ModelSyncEntityEvent(
                        ModelSyncEventType.NODE_DELETED.wireValue,
                        ModelSyncEventType.NODE_DELETED.entity,
                        nodeId
                    )
                )
            }
            modelSyncBroadcaster.broadcastModelChanged(modelId, "validation_delete_unused", events)
        }

        return DeleteUnusedResponse(
            deletedNodeIds = deletedNodeIds,
            deletedLinkIds = deletedLinkIds,
            skippedNodeIds = request.nodeIds.filter { it !in deletedNodeIds },
            skippedLinkIds = request.linkIds.filter { it !in deletedLinkIds }
        )
    }

    /** Имена папок-исключений (Views → СоМ и т.п.), настраивается списком через запятую. */
    private fun excludedFolderNames(): List<String> =
        excludedViewFolders.split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    /** Диаграммы, привязанные к узлам в папках-исключениях (и их подпапках). */
    private fun excludedDiagramIds(modelId: UUID, excludedFolders: List<String>): Set<UUID> =
        excludedFolders
            .flatMap { folderName -> diagramsRepository.findDiagramIdsUnderFolder(modelId, folderName) }
            .toSet()

    /**
     * Проставляет членам групп число диаграмм (последние версии), где используется
     * экземпляр, сортирует членов по использованию (самый используемый — первым)
     * и упорядочивает группы по максимальному использованию. Члены, входящие в
     * элементы диаграмм папок-исключений (СоМ), из отчёта убираются.
     */
    private fun assembleAnnotatedGroups(
        modelId: UUID,
        nodeRows: List<DuplicateNodeMemberProjection>,
        linkRows: List<DuplicateLinkMemberProjection>,
        excludedFolders: List<String>
    ): Pair<List<DuplicateNodeGroup>, List<DuplicateLinkGroup>> {
        val linkGroups = assembleLinkGroups(linkRows)
        val nodeGroups = assembleNodeGroups(nodeRows)

        val excludedNodeRefs = fetchExcludedRefs(
            modelId, excludedFolders,
            nodeGroups.flatMap { group -> group.nodes.map { it.id } }
        ) { folderName, ids -> nodesRepository.findNodeRefsInExcludedDiagrams(modelId, folderName, ids) }
        val excludedLinkRefs = fetchExcludedRefs(
            modelId, excludedFolders,
            linkGroups.flatMap { group -> group.links.map { it.id } }
        ) { folderName, ids -> linksRepository.findLinkRefsInExcludedDiagrams(modelId, folderName, ids) }

        val filteredNodeGroups = nodeGroups.mapNotNull { group ->
            val members = group.nodes.filter { it.id !in excludedNodeRefs }
            if (members.size == group.nodes.size) {
                group
            } else if (members.size < 2) {
                null
            } else {
                group.copy(nodes = members, count = members.size)
            }
        }
        val filteredLinkGroups = linkGroups.mapNotNull { group ->
            val members = group.links.filter { it.id !in excludedLinkRefs }
            if (members.size == group.links.size) {
                group
            } else if (members.size < 2) {
                null
            } else {
                group.copy(links = members, count = members.size)
            }
        }

        val nodeUsage = fetchUsage(filteredNodeGroups.flatMap { group -> group.nodes.map { it.id } }) { ids ->
            nodesRepository.findDiagramUsageByNodeIds(modelId, ids)
        }
        val linkUsage = fetchUsage(filteredLinkGroups.flatMap { group -> group.links.map { it.id } }) { ids ->
            linksRepository.findDiagramUsageByLinkIds(modelId, ids)
        }

        val sortedNodeGroups = filteredNodeGroups
            .map { group ->
                group.copy(
                    nodes = group.nodes
                        .map { it.copy(diagramCount = nodeUsage[it.id] ?: 0) }
                        .sortedWith(compareByDescending<DuplicateNodeMember> { it.diagramCount }.thenBy { it.id })
                )
            }
            .sortedWith(
                compareByDescending<DuplicateNodeGroup> { group -> group.nodes.maxOf { it.diagramCount } }
                    .thenByDescending { it.count }
                    .thenBy { it.name }
            )

        val sortedLinkGroups = filteredLinkGroups
            .map { group ->
                group.copy(
                    links = group.links
                        .map { it.copy(diagramCount = linkUsage[it.id] ?: 0) }
                        .sortedWith(compareByDescending<DuplicateLinkMember> { it.diagramCount }.thenBy { it.id })
                )
            }
            .sortedWith(
                compareByDescending<DuplicateLinkGroup> { group -> group.links.maxOf { it.diagramCount } }
                    .thenByDescending { it.count }
                    .thenBy { it.sourceName }
                    .thenBy { it.targetName }
            )

        return sortedNodeGroups to sortedLinkGroups
    }

    private fun fetchUsage(
        candidateIds: List<UUID>,
        query: (Collection<String>) -> List<RefUsageProjection>
    ): Map<UUID, Int> {
        val distinctIds = candidateIds.distinct()
        if (distinctIds.isEmpty()) return emptyMap()
        return query(distinctIds.map { it.toString() }).associate {
            UUID.fromString(it.getRefId()) to it.getDiagramCount().toInt()
        }
    }

    /** Рефы кандидатов, размещённые на диаграммах папок-исключений. */
    private fun fetchExcludedRefs(
        modelId: UUID,
        excludedFolders: List<String>,
        candidateIds: List<UUID>,
        query: (String, Collection<String>) -> List<String>
    ): Set<UUID> {
        val distinctIds = candidateIds.distinct()
        if (distinctIds.isEmpty()) return emptySet()
        val ids = distinctIds.map { it.toString() }
        return excludedFolders
            .flatMap { folderName -> query(folderName, ids) }
            .mapNotNull { runCatching { UUID.fromString(it) }.getOrNull() }
            .toSet()
    }

    // ==================================================================
    // Diagram integrity (ported from Archi plugin ModelCheckerLemana)
    // ==================================================================

    /**
     * Сканирует последние версии всех диаграмм модели и возвращает группы issues
     * по каждой диаграмме вместе с общим количеством найденных issues.
     * Диаграммы папок-исключений (СоМ) не сканируются.
     */
    fun diagramIntegrityIssues(
        modelId: UUID,
        excludedDiagramIds: Set<UUID> = emptySet()
    ): Pair<List<DiagramIssueGroup>, Int> {
        val diagrams = diagramsRepository.findAllActiveByModelId(modelId)
        if (diagrams.isEmpty()) {
            return emptyList<DiagramIssueGroup>() to 0
        }
        val nodeIds = nodesRepository.findIdsByModelId(modelId).map { it.getId() }.toSet()
        val linksById = linksRepository.findEndpointsByModelId(modelId)
            .associate { it.getId() to (it.getSourceId() to it.getTargetId()) }

        val groups = mutableListOf<DiagramIssueGroup>()
        var total = 0
        val latest = latestVersions(diagrams)
            .filter { it.id == null || it.id !in excludedDiagramIds }
            .sortedBy { it.name }
            .take(maxDiagrams)
        for (diagram in latest) {
            val issues = checkDiagram(diagram, nodeIds, linksById)
            if (issues.isNotEmpty()) {
                total += issues.size
                groups.add(
                    DiagramIssueGroup(
                        diagramId = requireNotNull(diagram.id),
                        diagramName = diagram.name,
                        issues = issues
                    )
                )
            }
        }
        return groups to total
    }

    private fun checkDiagram(
        diagram: Diagrams,
        nodeIds: Set<UUID>,
        linksById: Map<UUID, Pair<UUID, UUID>>
    ): List<DiagramIssue> {
        val root = parseAttrsRoot(diagram) ?: return emptyList()
        val instances = root.get("instances") as? ObjectNode ?: return emptyList()
        val nodeInstances = instances.get("nodes")?.takeIf { it.isArray } ?: return emptyList()
        val edgeInstances = instances.get("edges")?.takeIf { it.isArray }

        val issues = mutableListOf<DiagramIssue>()
        fun add(code: String, level: String, instanceId: String?, message: String) {
            if (issues.size < maxIssuesPerDiagram) {
                issues.add(DiagramIssue(code = code, level = level, instanceId = instanceId, message = message))
            }
        }

        // modelNodeId -> instanceId (первый инстанс), instanceId -> modelNodeId
        val instanceIdByModelNodeId = LinkedHashMap<String, String>()
        val modelNodeIdByInstanceId = HashMap<String, UUID>()
        val nodeInstanceIds = HashMap<String, Int>()

        // Diagram-only декорации OEF-импорта (заметки, рамки, якоря) носят синтетический
        // не-UUID modelNodeId по дизайну. Это не модельные узлы: не warning, не dangling,
        // но их instance-id валидны как концы note-рёбер.
        val diagramOnlyInstanceIds = HashSet<String>()
        for (el in nodeInstances) {
            if (el !is ObjectNode) continue
            val instanceId = el.get("id")?.asText()
            if (instanceId != null) nodeInstanceIds.merge(instanceId, 1, Int::plus)
            val raw = el.get("modelNodeId")?.asText()?.trim().takeUnless { it.isNullOrEmpty() } ?: continue
            if (DIAGRAM_ONLY_NODE_MODEL_ID_PREFIXES.any { raw.startsWith(it) }) {
                if (instanceId != null) diagramOnlyInstanceIds.add(instanceId)
                continue
            }
            val modelNodeId = runCatching { UUID.fromString(raw) }.getOrNull()
            if (modelNodeId == null) {
                add(
                    "diagramNodeInvalidModelRef", "warning", instanceId,
                    "Diagram node \"$instanceId\" has non-UUID modelNodeId \"$raw\""
                )
                continue
            }
            if (instanceId != null) {
                modelNodeIdByInstanceId[instanceId] = modelNodeId
                instanceIdByModelNodeId.putIfAbsent(raw, instanceId)
            }
            if (modelNodeId !in nodeIds) {
                add(
                    "diagramNodeDanglingRef", "error", instanceId,
                    "Diagram node \"$instanceId\" references missing model node \"$raw\""
                )
            }
        }
        for ((instanceId, count) in nodeInstanceIds) {
            if (count > 1) {
                add(
                    "duplicateDiagramInstanceId", "error", instanceId,
                    "Node instance id \"$instanceId\" is used $count times in diagram \"${diagram.name}\""
                )
            }
        }

        val edgeInstanceIds = HashMap<String, Int>()
        val edgeInstanceIdsSet = HashSet<String>()
        if (edgeInstances != null) {
            for (el in edgeInstances) {
                if (el !is ObjectNode) continue
                val instanceId = el.get("id")?.asText()
                if (instanceId != null) {
                    edgeInstanceIds.merge(instanceId, 1, Int::plus)
                    edgeInstanceIdsSet.add(instanceId)
                }
                val raw = el.get("modelLinkId")?.asText()?.trim().takeUnless { it.isNullOrEmpty() }
                val diagramOnly = el.get("attrs")?.get("isDiagramOnly")?.asBoolean(false) ?: false
                var linkEnds: Pair<UUID, UUID>? = null
                var linkIdText: String? = null
                if (raw != null && !diagramOnly && DIAGRAM_ONLY_EDGE_MODEL_ID_PREFIXES.none { raw.startsWith(it) }) {
                    val modelLinkId = runCatching { UUID.fromString(raw) }.getOrNull()
                    if (modelLinkId == null) {
                        add(
                            "diagramLinkInvalidModelRef", "warning", instanceId,
                            "Diagram edge \"$instanceId\" has non-UUID modelLinkId \"$raw\""
                        )
                    } else {
                        linkEnds = linksById[modelLinkId]
                        linkIdText = raw
                        if (linkEnds == null) {
                            add(
                                "diagramLinkDanglingRef", "error", instanceId,
                                "Diagram edge \"$instanceId\" references missing model link \"$raw\""
                            )
                        }
                    }
                }

                val sourceInstanceId = el.get("sourceInstanceId")?.asText()?.trim().takeUnless { it.isNullOrEmpty() }
                val targetInstanceId = el.get("targetInstanceId")?.asText()?.trim().takeUnless { it.isNullOrEmpty() }
                if (sourceInstanceId != null &&
                    sourceInstanceId !in modelNodeIdByInstanceId &&
                    sourceInstanceId !in edgeInstanceIdsSet &&
                    sourceInstanceId !in diagramOnlyInstanceIds
                ) {
                    add(
                        "diagramEdgeMissingEndpointInstance", "error", instanceId,
                        "Diagram edge \"$instanceId\" points to missing source instance \"$sourceInstanceId\""
                    )
                }
                if (targetInstanceId != null &&
                    targetInstanceId !in modelNodeIdByInstanceId &&
                    targetInstanceId !in edgeInstanceIdsSet &&
                    targetInstanceId !in diagramOnlyInstanceIds
                ) {
                    add(
                        "diagramEdgeMissingEndpointInstance", "error", instanceId,
                        "Diagram edge \"$instanceId\" points to missing target instance \"$targetInstanceId\""
                    )
                }

                if (linkEnds != null && linkIdText != null) {
                    val sourceModelNodeId =
                        if (sourceInstanceId != null) modelNodeIdByInstanceId[sourceInstanceId] else null
                    val targetModelNodeId =
                        if (targetInstanceId != null) modelNodeIdByInstanceId[targetInstanceId] else null
                    if (sourceModelNodeId != null && targetModelNodeId != null &&
                        (sourceModelNodeId != linkEnds.first || targetModelNodeId != linkEnds.second)
                    ) {
                        add(
                            "diagramLinkEndpointMismatch", "error", instanceId,
                            "Diagram edge \"$instanceId\" endpoints (\"$sourceModelNodeId\" -> \"$targetModelNodeId\") " +
                                "do not match model link \"$linkIdText\" endpoints " +
                                "(\"${linkEnds.first}\", \"${linkEnds.second}\")"
                        )
                    }
                }
            }
        }
        for ((instanceId, count) in edgeInstanceIds) {
            if (count > 1) {
                add(
                    "duplicateDiagramInstanceId", "error", instanceId,
                    "Edge instance id \"$instanceId\" is used $count times in diagram \"${diagram.name}\""
                )
            }
        }
        return issues
    }

    private fun parseAttrsRoot(diagram: Diagrams): ObjectNode? {
        val attrs = diagram.attrs
        if (attrs.isNullOrBlank()) return null
        return try {
            objectMapper.readTree(attrs) as? ObjectNode
        } catch (ex: Exception) {
            log.debug("Invalid attrs JSON for diagram {} ({}); skipped", diagram.id, ex.message)
            null
        }
    }

    private fun latestVersions(diagrams: List<Diagrams>): List<Diagrams> {
        val byName = LinkedHashMap<String, Diagrams>()
        for (diagram in diagrams) {
            val current = byName[diagram.name]
            if (current == null || compareVersions(diagram.version, current.version) > 0) {
                byName[diagram.name] = diagram
            }
        }
        return byName.values.toList()
    }

    private fun compareVersions(a: String, b: String): Int {
        val left = a.split('.')
        val right = b.split('.')
        val size = maxOf(left.size, right.size)
        for (i in 0 until size) {
            val l = left.getOrNull(i)?.trim()?.toIntOrNull() ?: 0
            val r = right.getOrNull(i)?.trim()?.toIntOrNull() ?: 0
            if (l != r) return l.compareTo(r)
        }
        return 0
    }

    private fun assembleNodeGroups(rows: List<DuplicateNodeMemberProjection>): List<DuplicateNodeGroup> {
        val groups = linkedMapOf<Pair<UUID, String>, MutableList<DuplicateNodeMemberProjection>>()
        for (row in rows) {
            groups.getOrPut(row.getNodeTypeId() to row.getNameKey()) { mutableListOf() }.add(row)
        }
        return groups.values.map { members ->
            val first = members.first()
            DuplicateNodeGroup(
                nodeTypeId = first.getNodeTypeId(),
                nodeTypeName = first.getNodeTypeName(),
                name = first.getName(),
                count = first.getGroupCount().toInt(),
                nodes = members.map { member ->
                    DuplicateNodeMember(
                        id = member.getId(),
                        name = member.getName(),
                        parentId = member.getParentId(),
                        parentName = member.getParentName()
                    )
                }
            )
        }
    }

    private fun assembleLinkGroups(rows: List<DuplicateLinkMemberProjection>): List<DuplicateLinkGroup> {
        val groups = linkedMapOf<Triple<UUID, UUID, UUID>, MutableList<DuplicateLinkMemberProjection>>()
        for (row in rows) {
            groups.getOrPut(Triple(row.getSourceId(), row.getTargetId(), row.getLinkTypeId())) { mutableListOf() }
                .add(row)
        }
        return groups.values.map { members ->
            val first = members.first()
            DuplicateLinkGroup(
                sourceId = first.getSourceId(),
                sourceName = first.getSourceName(),
                targetId = first.getTargetId(),
                targetName = first.getTargetName(),
                linkTypeId = first.getLinkTypeId(),
                linkTypeName = first.getLinkTypeName(),
                count = first.getGroupCount().toInt(),
                links = members.map { DuplicateLinkMember(it.getId()) }
            )
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(ModelValidationReportService::class.java)

        /** Служебные префиксы modelNodeId у diagram-only элементов OEF-импорта (не модельные узлы). */
        private val DIAGRAM_ONLY_NODE_MODEL_ID_PREFIXES =
            listOf("__diagram-note__:", "__diagram-container__:", "__diagram-edge-anchor__:")

        /** Служебные префиксы modelLinkId у diagram-only рёбер (не модельные связи). */
        private val DIAGRAM_ONLY_EDGE_MODEL_ID_PREFIXES =
            listOf("__diagram-note-edge__:", "__diagram-untyped-edge__:")
    }
}
