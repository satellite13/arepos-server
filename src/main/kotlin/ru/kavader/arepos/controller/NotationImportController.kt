package ru.kavader.arepos.controller

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.dto.import.NotationImportRequest
import ru.kavader.arepos.dto.import.NotationImportResponse
import ru.kavader.arepos.repository.UsersRepository
import ru.kavader.arepos.security.ResourceAccessService
import ru.kavader.arepos.service.NotationImportService
import ru.kavader.arepos.service.modelpackage.NotationExportDocumentMapper

@RestController
@RequestMapping("/api/v1/notations")
@Tag(name = "Notation Import", description = "Notation import and migration endpoints")
class NotationImportController(
    private val usersRepository: UsersRepository,
    private val accessService: ResourceAccessService,
    private val notationImportService: NotationImportService,
    private val exportDocumentMapper: NotationExportDocumentMapper,
    private val objectMapper: ObjectMapper
) {

    @PostMapping("/import")
    @Operation(summary = "Import notation package (flat NotationImportRequest or warchi-notation-export v2)")
    @ResponseStatus(HttpStatus.CREATED)
    fun importNotation(@RequestBody body: JsonNode): NotationImportResponse {
        val currentUserId = accessService.currentUserId()
        val owner = usersRepository.findById(currentUserId)
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "User $currentUserId not found")
            }

        val request = if (NotationExportDocumentMapper.isExportDocument(body)) {
            exportDocumentMapper.toImportRequest(body)
        } else if (body.hasNonNull("format")) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown notation export format")
        } else {
            try {
                objectMapper.treeToValue(body, NotationImportRequest::class.java)
            } catch (ex: Exception) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid notation import payload", ex)
            }
        }

        return notationImportService.import(request, owner)
    }
}
