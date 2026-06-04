package ru.kavader.arepos.config

import jakarta.validation.ConstraintViolationException
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.security.access.AccessDeniedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.dto.model.BatchConflictItem
import ru.kavader.arepos.dto.model.BatchSaveConflictException
import java.util.*

/**
 * Global exception handler that produces structured error responses.
 * Provides a consistent {error, message, traceId } body for all error paths.
 */
@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(javaClass)

    data class ErrorBody(
        val error: String,
        val message: String?,
        val traceId: String
    )

    data class ValidationErrorBody(
        val error: String,
        val message: String?,
        val traceId: String,
        val fieldErrors: List<FieldErrorDetail>
    )

    data class BatchConflictErrorBody(
        val error: String,
        val message: String?,
        val traceId: String,
        val conflicts: List<BatchConflictItem>
    )

    data class FieldErrorDetail(
        val field: String,
        val message: String?
    )

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(ex: MethodArgumentNotValidException): ResponseEntity<ValidationErrorBody> {
        val traceId = newTraceId()
        val fieldErrors = ex.bindingResult.fieldErrors.map { FieldErrorDetail(it.field, it.defaultMessage) }
        log.warn("Validation failed [{}]: {}", traceId, fieldErrors)
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(
                ValidationErrorBody(
                    "VALIDATION_ERROR",
                    ex.body?.detail ?: "Request validation failed",
                    traceId,
                    fieldErrors
                )
            )
    }

    @ExceptionHandler(ConstraintViolationException::class)
    fun handleConstraintViolation(ex: ConstraintViolationException): ResponseEntity<ErrorBody> {
        val traceId = newTraceId()
        log.warn("Constraint violation [{}]: {}", traceId, ex.message)
        return buildResponse(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", ex.message, traceId)
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleMalformedBody(ex: HttpMessageNotReadableException): ResponseEntity<ErrorBody> {
        val traceId = newTraceId()
        log.warn("Malformed request body [{}]: {}", traceId, ex.message)
        return buildResponse(
            HttpStatus.BAD_REQUEST,
            "MALFORMED_BODY",
            "Request body is malformed or unreadable",
            traceId
        )
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(ex: IllegalArgumentException): ResponseEntity<ErrorBody> {
        val traceId = newTraceId()
        log.warn("Bad request [{}]: {}", traceId, ex.message)
        return buildResponse(HttpStatus.BAD_REQUEST, "BAD_REQUEST", ex.message, traceId)
    }

    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDenied(ex: AccessDeniedException): ResponseEntity<ErrorBody> {
        val traceId = newTraceId()
        log.warn("Access denied [{}]: {}", traceId, ex.message)
        return buildResponse(HttpStatus.FORBIDDEN, "ACCESS_DENIED", ex.message, traceId)
    }

    @ExceptionHandler(DataIntegrityViolationException::class)
    fun handleDataIntegrity(ex: DataIntegrityViolationException): ResponseEntity<ErrorBody> {
        val traceId = newTraceId()
        log.error("Data integrity violation [{}]: {}", traceId, ex.mostSpecificCause.message, ex)
        return buildResponse(HttpStatus.CONFLICT, "DATA_INTEGRITY", "Operation conflicts with existing data", traceId)
    }

    @ExceptionHandler(BatchSaveConflictException::class)
    fun handleBatchSaveConflict(ex: BatchSaveConflictException): ResponseEntity<BatchConflictErrorBody> {
        val traceId = newTraceId()
        log.warn("Batch save conflict [{}]: {} conflict(s)", traceId, ex.conflicts.size)
        return ResponseEntity
            .status(HttpStatus.CONFLICT)
            .body(
                BatchConflictErrorBody(
                    error = "BATCH_SAVE_CONFLICT",
                    message = "Concurrent modification",
                    traceId = traceId,
                    conflicts = ex.conflicts
                )
            )
    }

    @ExceptionHandler(ResponseStatusException::class)
    fun handleResponseStatus(ex: ResponseStatusException): ResponseEntity<ErrorBody> {
        val traceId = newTraceId()
        val status = HttpStatus.valueOf(ex.statusCode.value())
        if (ex.statusCode.is5xxServerError) {
            log.error("Server error [{}]: {}", traceId, ex.reason, ex)
        }
        val message = ex.reason ?: status.reasonPhrase
        return buildResponse(status, status.name, message, traceId)
    }

    private fun buildResponse(status: HttpStatus, error: String, message: String?, traceId: String) =
        ResponseEntity.status(status).body(ErrorBody(error, message, traceId))

    private fun newTraceId() = MDC.get("requestId") ?: UUID.randomUUID().toString().takeLast(12)
}
