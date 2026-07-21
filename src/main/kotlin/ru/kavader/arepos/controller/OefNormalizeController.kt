package ru.kavader.arepos.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.dto.oef.OefNormalizeResponse
import ru.kavader.arepos.repository.ModelsRepository
import ru.kavader.arepos.security.ResourceAccessService
import ru.kavader.arepos.service.OefParseService
import java.util.*

@RestController
@RequestMapping("/api/v1/models")
@Tag(name = "OEF Import", description = "Open Exchange Format normalize endpoints")
class OefNormalizeController(
    private val modelsRepository: ModelsRepository,
    private val accessService: ResourceAccessService,
    private val oefParseService: OefParseService,
) {
    @PostMapping(
        "/{modelId}/oef/normalize",
        consumes = [MediaType.MULTIPART_FORM_DATA_VALUE],
    )
    @Operation(summary = "Normalize OEF XML into compact import JSON")
    fun normalize(
        @PathVariable modelId: UUID,
        @RequestParam("file") file: MultipartFile,
    ): OefNormalizeResponse {
        val model = modelsRepository.findById(modelId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Model $modelId not found")
        }
        accessService.requireCanEditModel(model)

        if (file.isEmpty) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "OEF file is required")
        }
        if (file.size > OefParseService.MAX_UPLOAD_BYTES) {
            throw ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "OEF file exceeds 100 MB limit")
        }

        val contentType = file.contentType?.lowercase().orEmpty()
        val filename = file.originalFilename.orEmpty().lowercase()
        val looksXml =
            contentType.contains("xml") ||
                contentType == "application/octet-stream" ||
                contentType.isBlank() ||
                filename.endsWith(".xml")
        if (!looksXml) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "OEF file must be XML")
        }

        return try {
            file.inputStream.use { stream ->
                oefParseService.parseAndValidate(stream)
            }
        } catch (ex: IllegalArgumentException) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, ex.message ?: "Invalid OEF XML")
        } catch (ex: OutOfMemoryError) {
            // Prefer not to kill the JVM silently; large residual heaps can still leave the process unstable.
            throw ResponseStatusException(
                HttpStatus.PAYLOAD_TOO_LARGE,
                "OEF file is too large to process in memory",
            )
        } catch (ex: Exception) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Failed to read OEF file: ${ex.message ?: "read error"}",
            )
        }
    }
}
