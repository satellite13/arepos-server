package ru.kavader.arepos.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.repository.UsersRepository
import ru.kavader.arepos.security.ResourceAccessService
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("/api/v1/admin/users/{userId}/sso")
@Tag(name = "Admin SSO", description = "Admin endpoints for user SSO linkage")
class AdminOidcController(
    private val userRepository: UsersRepository,
    private val accessService: ResourceAccessService,
) {

    @DeleteMapping
    @Operation(summary = "Unlink SSO (OIDC) from a user")
    fun unlink(@PathVariable userId: UUID): OidcStatusResponse {
        accessService.requireCanManageUsers()

        val user = userRepository.findById(userId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "User $userId not found") }

        if (user.oidcSub.isNullOrBlank()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "OIDC is not linked")
        }

        user.oidcSub = null
        user.updatedAt = Instant.now()
        userRepository.save(user)

        return OidcStatusResponse(linked = false, oidcSub = null)
    }
}
