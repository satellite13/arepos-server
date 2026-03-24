package ru.kavader.arepos.config

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import ru.kavader.arepos.dto.BatchSaveConflictBody
import ru.kavader.arepos.dto.BatchSaveConflictException

@RestControllerAdvice
class BatchSaveConflictAdvice {
    @ExceptionHandler(BatchSaveConflictException::class)
    fun handleBatchSaveConflict(ex: BatchSaveConflictException): ResponseEntity<BatchSaveConflictBody> {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
            BatchSaveConflictBody(
                message = "Concurrent modification",
                conflicts = ex.conflicts
            )
        )
    }
}
