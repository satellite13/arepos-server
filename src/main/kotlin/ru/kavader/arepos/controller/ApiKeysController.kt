package ru.kavader.arepos.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import ru.kavader.arepos.dto.apikey.ApiKeyResponse
import ru.kavader.arepos.dto.apikey.CreateApiKeyRequest
import ru.kavader.arepos.dto.apikey.CreateApiKeyResponse
import ru.kavader.arepos.dto.apikey.UpdateApiKeyRequest
import ru.kavader.arepos.dto.common.ListResponse
import ru.kavader.arepos.dto.common.toListResponse
import ru.kavader.arepos.service.ApiKeyService
import java.util.*

@RestController
@RequestMapping("/api/v1/api-keys")
@Tag(name = "API Keys", description = "Personal API keys for MCP and external clients")
class ApiKeysController(
    private val apiKeyService: ApiKeyService
) {

    @GetMapping
    @Operation(summary = "List my API keys")
    fun list(): ListResponse<ApiKeyResponse> = apiKeyService.listMine().toListResponse()

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create API key (plaintext returned once)")
    fun create(@RequestBody @Valid request: CreateApiKeyRequest): CreateApiKeyResponse =
        apiKeyService.create(request)

    @PatchMapping("/{id}")
    @Operation(summary = "Update API key name/expiry (does not rotate secret)")
    fun update(
        @PathVariable id: UUID,
        @RequestBody @Valid request: UpdateApiKeyRequest
    ): ApiKeyResponse = apiKeyService.update(id, request)

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Revoke API key")
    fun revoke(@PathVariable id: UUID) {
        apiKeyService.revoke(id)
    }
}
