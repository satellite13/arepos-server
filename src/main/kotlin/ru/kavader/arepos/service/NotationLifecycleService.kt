package ru.kavader.arepos.service

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.model.Notations
import ru.kavader.arepos.repository.NotationsRepository
import java.util.UUID

@Service
class NotationLifecycleService(
    private val notationsRepository: NotationsRepository
) {
    @Transactional
    fun permanentDeleteNotation(notation: Notations) {
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
