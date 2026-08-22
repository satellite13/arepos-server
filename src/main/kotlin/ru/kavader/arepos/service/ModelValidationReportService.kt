package ru.kavader.arepos.service

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.dto.model.DuplicateLinkGroup
import ru.kavader.arepos.dto.model.DuplicateLinkMember
import ru.kavader.arepos.dto.model.DuplicateNodeGroup
import ru.kavader.arepos.dto.model.DuplicateNodeMember
import ru.kavader.arepos.dto.model.ValidationReportResponse
import ru.kavader.arepos.repository.DuplicateLinkMemberProjection
import ru.kavader.arepos.repository.DuplicateNodeMemberProjection
import ru.kavader.arepos.repository.LinksRepository
import ru.kavader.arepos.repository.ModelsRepository
import ru.kavader.arepos.repository.NodesRepository
import ru.kavader.arepos.security.ResourceAccessService
import java.time.Instant
import java.util.UUID

@Service
class ModelValidationReportService(
    private val modelsRepository: ModelsRepository,
    private val accessService: ResourceAccessService,
    private val nodesRepository: NodesRepository,
    private val linksRepository: LinksRepository
) {
    fun report(modelId: UUID): ValidationReportResponse {
        val model = modelsRepository.findById(modelId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Model $modelId not found")
        }
        accessService.requireCanViewModel(model)
        return ValidationReportResponse(
            modelId = model.id!!,
            generatedAt = Instant.now(),
            duplicateNodes = assembleNodeGroups(nodesRepository.findDuplicateNodeMembers(modelId)),
            duplicateLinks = assembleLinkGroups(linksRepository.findDuplicateLinkMembers(modelId))
        )
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
}
