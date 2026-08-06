package ru.kavader.arepos.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import ru.kavader.arepos.dto.apikey.ApiKeyResponse
import ru.kavader.arepos.dto.common.ListResponse
import ru.kavader.arepos.dto.common.toListResponse
import ru.kavader.arepos.service.ApiKeyService
import java.util.*

@RestController
@RequestMapping("/api/v1/admin/users/{userId}/api-keys")
@Tag(name = "Admin API Keys", description = "Admin endpoints for user API keys")
class AdminApiKeysController(
    private val apiKeyService: ApiKeyService
) {

    @GetMapping
    @Operation(summary = "List API keys for a user (admin)")
    fun list(@PathVariable userId: UUID): ListResponse<ApiKeyResponse> =
        apiKeyService.listForUser(userId).toListResponse()

    @DeleteMapping("/{keyId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Revoke a user's API key (admin)")
    fun revoke(@PathVariable userId: UUID, @PathVariable keyId: UUID) {
        apiKeyService.revokeForUser(userId, keyId)
    }
}
