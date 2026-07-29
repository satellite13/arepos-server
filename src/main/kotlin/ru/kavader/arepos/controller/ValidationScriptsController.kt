package ru.kavader.arepos.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.dto.script.ValidationScriptRequest
import ru.kavader.arepos.dto.script.ValidationScriptResponse
import ru.kavader.arepos.dto.script.ValidationScriptUpdateRequest
import ru.kavader.arepos.mapper.ValidationScriptMapper
import ru.kavader.arepos.model.ShareResourceType
import ru.kavader.arepos.model.ValidationScripts
import ru.kavader.arepos.repository.ResourceSharesRepository
import ru.kavader.arepos.repository.UsersRepository
import ru.kavader.arepos.repository.ValidationScriptsRepository
import ru.kavader.arepos.security.ResourceAccessService
import java.time.Instant
import java.util.*

@RestController
@RequestMapping("/api/v1/validation-scripts")
@Tag(name = "Validation Scripts", description = "Shareable model validation scripts")
class ValidationScriptsController(
    private val validationScriptsRepository: ValidationScriptsRepository,
    private val usersRepository: UsersRepository,
    private val resourceSharesRepository: ResourceSharesRepository,
    private val accessService: ResourceAccessService,
    private val validationScriptMapper: ValidationScriptMapper
) {

    @GetMapping
    @Operation(summary = "List validation scripts")
    fun list(
        pageable: Pageable,
        @RequestParam(required = false) ownerId: UUID?,
        @RequestParam(required = false) name: String?
    ): Page<ValidationScriptResponse> {
        accessService.currentUserId()
        val allScripts = when {
            ownerId != null -> {
                val owner = usersRepository.findById(ownerId).orElseThrow {
                    ResponseStatusException(HttpStatus.NOT_FOUND, "Owner $ownerId not found")
                }
                validationScriptsRepository.findByOwner(owner, Pageable.unpaged()).content
            }

            else -> validationScriptsRepository.findAll()
        }
        val normalizedName = name?.trim().orEmpty()
        val visible = accessService.filterViewableValidationScripts(allScripts)
            .asSequence()
            .filter { normalizedName.isEmpty() || it.name.contains(normalizedName, ignoreCase = true) }
            .toList()
        return mapScriptsPage(visible.toPage(pageable))
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get validation script by id")
    fun get(@PathVariable id: UUID): ValidationScriptResponse {
        accessService.currentUserId()
        val script = validationScriptsRepository.findById(id).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "ValidationScript $id not found")
        }
        accessService.requireCanViewValidationScript(script)
        return validationScriptMapper.toResponse(script)
    }

    @PostMapping
    @Operation(summary = "Create validation script")
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@RequestBody @Valid request: ValidationScriptRequest): ValidationScriptResponse {
        val currentUserId = accessService.currentUserId()
        val owner = usersRepository.findById(currentUserId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Current user $currentUserId not found")
        }
        val name = request.name.trim()
        val source = request.source.trim()
        if (name.isEmpty() || source.isEmpty()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "name and source must not be blank")
        }
        if (validationScriptsRepository.findByOwnerAndNameIgnoreCase(owner, name) != null) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Validation script name already exists")
        }
        val now = Instant.now()
        val saved = validationScriptsRepository.save(
            ValidationScripts(
                name = name,
                description = request.description?.trim()?.ifEmpty { null },
                source = source,
                owner = owner,
                attrs = request.attrs,
                createdAt = now,
                updatedAt = now
            )
        )
        return validationScriptMapper.toResponse(saved)
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update validation script")
    fun update(
        @PathVariable id: UUID,
        @RequestBody @Valid request: ValidationScriptUpdateRequest
    ): ValidationScriptResponse {
        val script = validationScriptsRepository.findById(id).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "ValidationScript $id not found")
        }
        accessService.requireCanEditValidationScript(script)

        request.name?.let { raw ->
            val name = raw.trim()
            if (name.isEmpty()) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "name must not be blank")
            }
            val existing = validationScriptsRepository.findByOwnerAndNameIgnoreCase(script.owner, name)
            if (existing != null && existing.id != script.id) {
                throw ResponseStatusException(HttpStatus.CONFLICT, "Validation script name already exists")
            }
            script.name = name
        }
        if (request.description != null) {
            script.description = request.description.trim().ifEmpty { null }
        }
        request.source?.let { raw ->
            val source = raw.trim()
            if (source.isEmpty()) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "source must not be blank")
            }
            script.source = source
        }
        if (request.attrs != null) {
            script.attrs = request.attrs
        }
        script.updatedAt = Instant.now()
        return validationScriptMapper.toResponse(validationScriptsRepository.save(script))
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete validation script")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    fun delete(@PathVariable id: UUID) {
        val script = validationScriptsRepository.findById(id).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "ValidationScript $id not found")
        }
        accessService.requireCanEditValidationScript(script)
        resourceSharesRepository.deleteByResourceTypeAndResourceId(ShareResourceType.VALIDATION_SCRIPT, id)
        validationScriptsRepository.delete(script)
    }

    private fun mapScriptsPage(page: Page<ValidationScripts>): Page<ValidationScriptResponse> =
        page.mapWithPermissions(
            loadPermissions = accessService::validationScriptAccessPermissions,
            idOf = ValidationScripts::id,
            transform = validationScriptMapper::toResponse
        )
}
