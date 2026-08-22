package ru.kavader.arepos.service

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.dto.system.ModelSyncChangeType
import ru.kavader.arepos.dto.system.ModelSyncEntityEvent
import ru.kavader.arepos.dto.system.ModelSyncEventType
import ru.kavader.arepos.model.Diagrams
import ru.kavader.arepos.repository.DiagramsRepository
import ru.kavader.arepos.util.VersionUtils
import java.time.Instant

@Service
class DiagramLifecycleService(
    private val diagramsRepository: DiagramsRepository,
    private val mdFileLinkValidator: MdFileLinkValidator,
    private val modelSyncBroadcaster: ModelSyncBroadcaster,
    private val diagramOnlyOrphanCleanupService: DiagramOnlyOrphanCleanupService
) {
    @Transactional
    fun createBaseline(diagram: Diagrams): Diagrams {
        requireLatestDiagramVersion(diagram, "used to create baseline")
        val newVersion = bumpMinorVersion(diagram.version)
            ?: throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Invalid diagram version '${diagram.version}'; expected semantic version (e.g. 1.2.3)"
            )
        if (diagramsRepository.existsByModelAndNameAndVersion(diagram.model, diagram.name, newVersion)) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "Diagram with name '${diagram.name}' and version '$newVersion' already exists"
            )
        }
        mdFileLinkValidator.validate(diagram.attrs)
        val now = Instant.now()
        val saved = diagramsRepository.save(
            Diagrams(
                name = diagram.name,
                createdAt = now,
                updatedAt = now,
                attrs = diagram.attrs,
                version = newVersion,
                owner = diagram.owner,
                deleted = false,
                model = diagram.model,
                notation = diagram.notation,
                node = diagram.node
            )
        )
        modelSyncBroadcaster.broadcastModelChanged(
            requireNotNull(diagram.model.id),
            ModelSyncChangeType.DIAGRAM_BASELINE.wireValue,
            listOf(
                ModelSyncEntityEvent(
                    ModelSyncEventType.DIAGRAM_CREATED.wireValue,
                    ModelSyncEventType.DIAGRAM_CREATED.entity,
                    requireNotNull(saved.id)
                )
            )
        )
        return saved
    }

    @Transactional
    fun softDeleteDiagram(diagram: Diagrams) {
        val id = requireNotNull(diagram.id)
        val deletedCount = diagramsRepository.softDeleteById(id)
        if (deletedCount == 0) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Diagram $id not found")
        }
        diagramOnlyOrphanCleanupService.deleteOrphansAfterDiagramsDeleted(
            requireNotNull(diagram.model.id),
            listOf(diagram)
        )
        modelSyncBroadcaster.broadcastModelChanged(
            requireNotNull(diagram.model.id),
            ModelSyncChangeType.DIAGRAM_DELETE.wireValue,
            listOf(
                ModelSyncEntityEvent(
                    ModelSyncEventType.DIAGRAM_DELETED.wireValue,
                    ModelSyncEventType.DIAGRAM_DELETED.entity,
                    id
                )
            )
        )
    }

    fun requireLatestDiagramVersion(diagram: Diagrams, action: String) {
        val modelId = diagram.model.id ?: return
        val allByName = diagramsRepository.findByModelIdAndNameAndDeletedFalse(modelId, diagram.name)
        if (allByName.isEmpty()) return
        val latest = allByName.maxWithOrNull(::compareDiagramVersions) ?: return
        if (latest.id != diagram.id) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "Only latest diagram version can be $action. Latest version is '${latest.version}'."
            )
        }
    }

    fun bumpMinorVersion(version: String): String? {
        val parts = version.trim().split(".")
        if (parts.size < 2) return null
        val major = parts[0].toIntOrNull() ?: return null
        val minor = parts.getOrNull(1)?.toIntOrNull() ?: return null
        return "$major.${minor + 1}.0"
    }

    fun compareDiagramVersions(a: Diagrams, b: Diagrams): Int {
        val semverComparison = VersionUtils.compareSemver(a.version, b.version)
        if (semverComparison != null) {
            if (semverComparison != 0) return semverComparison
        } else {
            return a.version.compareTo(b.version)
        }
        val aUpdated = a.updatedAt ?: a.createdAt ?: Instant.EPOCH
        val bUpdated = b.updatedAt ?: b.createdAt ?: Instant.EPOCH
        val timeCmp = aUpdated.compareTo(bUpdated)
        if (timeCmp != 0) return timeCmp
        val aId = a.id?.toString().orEmpty()
        val bId = b.id?.toString().orEmpty()
        return aId.compareTo(bId)
    }
}
