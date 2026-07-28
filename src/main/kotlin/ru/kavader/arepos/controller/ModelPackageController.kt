package ru.kavader.arepos.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.dto.modelpackage.ModelPackageImportResponse
import ru.kavader.arepos.repository.UsersRepository
import ru.kavader.arepos.security.ResourceAccessService
import ru.kavader.arepos.service.modelpackage.ModelPackageExportService
import ru.kavader.arepos.service.modelpackage.ModelPackageImportService
import ru.kavader.arepos.service.modelpackage.ModelPackageLimits
import java.util.UUID

@RestController
@RequestMapping("/api/v1/models")
@Tag(name = "Model Package", description = "Model package import/export endpoints")
class ModelPackageController(
    private val exportService: ModelPackageExportService,
    private val importService: ModelPackageImportService,
    private val usersRepository: UsersRepository,
    private val accessService: ResourceAccessService
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

    @PostMapping("/package", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @Operation(summary = "Import model package ZIP as a new owned model")
    @ResponseStatus(HttpStatus.CREATED)
    fun importPackage(@RequestParam("file") file: MultipartFile): ModelPackageImportResponse {
        if (file.isEmpty) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Package file is required")
        }
        if (file.size > ModelPackageLimits.MAX_ZIP_BYTES) {
            throw ResponseStatusException(
                HttpStatus.PAYLOAD_TOO_LARGE,
                "Package exceeds ${ModelPackageLimits.MAX_ZIP_BYTES} bytes limit"
            )
        }
        val currentUserId = accessService.currentUserId()
        val owner = usersRepository.findById(currentUserId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "User $currentUserId not found")
        }
        return importService.importPackage(file.bytes, owner)
    }
}
