package ru.kavader.arepos.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.dto.libraryicon.LibraryIconBatchCreateRequest
import ru.kavader.arepos.dto.libraryicon.LibraryIconBundle
import ru.kavader.arepos.dto.libraryicon.LibraryIconBundleImportResult
import ru.kavader.arepos.dto.libraryicon.LibraryIconCreateRequest
import ru.kavader.arepos.dto.libraryicon.LibraryIconResponse
import ru.kavader.arepos.security.ADMIN_ONLY
import ru.kavader.arepos.security.ResourceAccessService
import ru.kavader.arepos.service.LibraryIconNames
import ru.kavader.arepos.service.LibraryIconService
import java.util.UUID

@RestController
@RequestMapping("/api/v1/library-icons")
@Tag(name = "Library Icons", description = "Instance-wide SVG icon library")
class LibraryIconsController(
    private val libraryIconService: LibraryIconService,
    private val accessService: ResourceAccessService
) {
    @GetMapping
    @Operation(summary = "List library icons")
    fun list(): List<LibraryIconResponse> {
        accessService.currentUserId()
        return libraryIconService.list()
    }

    @PostMapping
    @Operation(summary = "Create one library icon (admin)")
    fun create(@Valid @RequestBody request: LibraryIconCreateRequest): LibraryIconResponse {
        requireAdmin()
        return libraryIconService.create(request)
    }

    @PostMapping("/batch")
    @Operation(summary = "Create several library icons (admin)")
    fun createBatch(@Valid @RequestBody request: LibraryIconBatchCreateRequest): List<LibraryIconResponse> {
        requireAdmin()
        return libraryIconService.createMany(request.icons)
    }

    @PostMapping("/upload")
    @Operation(summary = "Upload SVG files into the library (admin)")
    fun upload(@RequestPart("files") files: List<MultipartFile>): List<LibraryIconResponse> {
        requireAdmin()
        if (files.isEmpty()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "No files uploaded")
        }
        if (files.size > 100) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Too many files")
        }
        val requests = files.map { file ->
            val name = LibraryIconNames.fromFilename(file.originalFilename ?: "icon.svg")
            val svg = file.bytes.toString(Charsets.UTF_8)
            LibraryIconCreateRequest(name = name, svg = svg)
        }
        return libraryIconService.createMany(requests)
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a library icon (admin)")
    fun delete(@PathVariable id: UUID) {
        requireAdmin()
        libraryIconService.delete(id)
    }

    @GetMapping("/bundle")
    @Operation(summary = "Export the icon library as a bundle (admin)")
    fun exportBundle(): LibraryIconBundle {
        requireAdmin()
        return libraryIconService.exportBundle()
    }

    @PostMapping("/bundle")
    @Operation(summary = "Import an icon bundle, overwriting same names (admin)")
    fun importBundle(@Valid @RequestBody bundle: LibraryIconBundle): LibraryIconBundleImportResult {
        requireAdmin()
        return libraryIconService.importBundle(bundle)
    }

    private fun requireAdmin() {
        if (!accessService.canViewAdminPanel()) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, ADMIN_ONLY)
        }
    }
}
