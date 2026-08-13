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
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.dto.modelpackage.PackageImportJobAcceptedResponse
import ru.kavader.arepos.dto.modelpackage.PackageImportJobRetryRequest
import ru.kavader.arepos.dto.modelpackage.PackageImportJobStatusResponse
import ru.kavader.arepos.repository.UsersRepository
import ru.kavader.arepos.security.ResourceAccessService
import ru.kavader.arepos.service.modelpackage.ModelPackageExportService
import ru.kavader.arepos.service.modelpackage.ModelPackageImportJobService
import java.util.UUID

@RestController
@RequestMapping("/api/v1/models")
@Tag(name = "Model Package", description = "Model package import/export endpoints")
class ModelPackageController(
    private val exportService: ModelPackageExportService,
    private val importJobService: ModelPackageImportJobService,
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
    @Operation(summary = "Start async import of a model package ZIP")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun importPackage(@RequestParam("file") file: MultipartFile): PackageImportJobAcceptedResponse {
        val currentUserId = accessService.currentUserId()
        val owner = usersRepository.findById(currentUserId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "User $currentUserId not found")
        }
        return importJobService.acceptUpload(file, owner)
    }

    @GetMapping("/package/jobs/{jobId}")
    @Operation(summary = "Get async model package import job status")
    fun getImportJob(@PathVariable jobId: UUID): PackageImportJobStatusResponse {
        return importJobService.getJob(jobId, accessService.currentUserId())
    }

    @PostMapping("/package/jobs/{jobId}/retry")
    @Operation(summary = "Retry a failed model package import after MODEL_EXISTS with name/version overrides")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun retryImportJob(
        @PathVariable jobId: UUID,
        @RequestBody request: PackageImportJobRetryRequest
    ): PackageImportJobAcceptedResponse {
        return importJobService.retryJob(jobId, accessService.currentUserId(), request)
    }
}
