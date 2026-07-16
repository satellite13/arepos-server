package ru.kavader.arepos.service

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.model.Notations
import ru.kavader.arepos.model.ShareResourceType
import ru.kavader.arepos.repository.DiagramsRepository
import ru.kavader.arepos.repository.NotationsRepository
import ru.kavader.arepos.repository.ResourceSharesRepository
import java.util.UUID

@Service
class NotationLifecycleService(
    private val notationsRepository: NotationsRepository,
    private val diagramsRepository: DiagramsRepository,
    private val resourceSharesRepository: ResourceSharesRepository
) {
    @Transactional
    fun permanentDeleteNotation(notation: Notations) {
        val notationId = notation.id
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Notation id is required")

        val diagrams = diagramsRepository.findAllByNotationIdWithModel(notationId)
        val activeModelNames = diagrams
            .asSequence()
            .filter { !it.model.deleted }
            .map { it.model.name }
            .distinct()
            .take(5)
            .toList()

        if (activeModelNames.isNotEmpty()) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "Notation is still used by diagrams in active models: ${activeModelNames.joinToString()}"
            )
        }

        if (diagrams.isNotEmpty()) {
            // Soft-deleted models keep diagrams with ON DELETE RESTRICT on notation_id.
            diagramsRepository.deleteAll(diagrams)
            diagramsRepository.flush()
        }

        resourceSharesRepository.deleteByResourceTypeAndResourceId(ShareResourceType.NOTATION, notationId)
        notationsRepository.delete(notation)
    }

    @Transactional
    fun softDeleteNotation(id: UUID) {
        val deletedCount = notationsRepository.softDeleteById(id)
        if (deletedCount == 0) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Notation $id not found")
        }
    }
}
