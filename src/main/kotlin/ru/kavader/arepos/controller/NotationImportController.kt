package ru.kavader.arepos.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.dto.import.NotationImportRequest
import ru.kavader.arepos.dto.import.NotationImportResponse
import ru.kavader.arepos.repository.UsersRepository
import ru.kavader.arepos.security.ResourceAccessService
import ru.kavader.arepos.service.NotationImportService

@RestController
@RequestMapping("/api/v1/notations")
@Tag(name = "Notation Import", description = "Notation import and migration endpoints")
class NotationImportController(
    private val usersRepository: UsersRepository,
    private val accessService: ResourceAccessService,
    private val notationImportService: NotationImportService
) {

    @PostMapping("/import")
    @Operation(summary = "Import notation package")
    @ResponseStatus(HttpStatus.CREATED)
    fun importNotation(@RequestBody @Valid request: NotationImportRequest): NotationImportResponse {
        val currentUserId = accessService.currentUserId()
        val owner = usersRepository.findById(currentUserId)
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "User $currentUserId not found")
            }

        return notationImportService.import(request, owner)
    }
}
