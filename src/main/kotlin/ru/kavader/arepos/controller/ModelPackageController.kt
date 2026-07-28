package ru.kavader.arepos.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import ru.kavader.arepos.service.modelpackage.ModelPackageExportService
import java.util.UUID

@RestController
@RequestMapping("/api/v1/models")
@Tag(name = "Model Package", description = "Model package import/export endpoints")
class ModelPackageController(
    private val exportService: ModelPackageExportService
) {
    @GetMapping("/{id}/package")
    @Operation(summary = "Export model as self-contained ZIP package")
    fun exportPackage(@PathVariable id: UUID): ResponseEntity<ByteArray> {
        val bytes = exportService.export(id)
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"model-package.zip\"")
            .contentType(MediaType.parseMediaType("application/zip"))
            .body(bytes)
    }
}
