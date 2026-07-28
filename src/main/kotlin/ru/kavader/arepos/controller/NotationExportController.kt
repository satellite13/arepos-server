package ru.kavader.arepos.controller

import com.fasterxml.jackson.databind.ObjectMapper
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.repository.NotationsRepository
import ru.kavader.arepos.security.ResourceAccessService
import ru.kavader.arepos.service.modelpackage.NotationPackageAssembler
import java.util.UUID

@RestController
@RequestMapping("/api/v1/notations")
@Tag(name = "Notation Export", description = "Notation package export endpoints")
class NotationExportController(
    private val notationsRepository: NotationsRepository,
    private val accessService: ResourceAccessService,
    private val assembler: NotationPackageAssembler,
    private val objectMapper: ObjectMapper
) {
    @GetMapping("/{id}/export")
    @Operation(summary = "Export notation as warchi-notation-export v2 JSON")
    fun exportNotation(@PathVariable id: UUID): ResponseEntity<ByteArray> {
        val notation = notationsRepository.findById(id).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Notation $id not found")
        }
        accessService.requireCanViewNotation(notation)
        val json = objectMapper.writerWithDefaultPrettyPrinter()
            .writeValueAsBytes(assembler.toClientExportDocument(notation))
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"notation-export.json\"")
            .contentType(MediaType.APPLICATION_JSON)
            .body(json)
    }
}
