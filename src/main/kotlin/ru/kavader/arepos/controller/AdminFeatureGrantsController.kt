package ru.kavader.arepos.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.dto.featuregrant.FeatureGrantAllowsResponse
import ru.kavader.arepos.dto.featuregrant.FeatureGrantsUpdateRequest
import ru.kavader.arepos.dto.featuregrant.RoleFeatureGrantsMatrixResponse
import ru.kavader.arepos.featuregrant.FeatureGrantKeys
import ru.kavader.arepos.featuregrant.FeatureGrantService
import ru.kavader.arepos.model.Role
import ru.kavader.arepos.repository.UsersRepository
import ru.kavader.arepos.security.ResourceAccessService
import java.util.UUID

@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Admin Feature Grants", description = "Admin endpoints for role and user feature grants")
class AdminFeatureGrantsController(
    private val featureGrantService: FeatureGrantService,
    private val usersRepository: UsersRepository,
    private val accessService: ResourceAccessService,
) {

    @GetMapping("/role-feature-grants")
    @Operation(summary = "Get role feature grants matrix and catalog")
    fun getRoleFeatureGrantsMatrix(): RoleFeatureGrantsMatrixResponse {
        accessService.requireCanManageUsers()
        return RoleFeatureGrantsMatrixResponse(
            roles = featureGrantService.roleGrantsMatrix(),
            catalog = FeatureGrantKeys.ALL,
        )
    }

    @PutMapping("/role-feature-grants/{role}")
    @Operation(summary = "Replace feature grants for a role")
    fun replaceRoleFeatureGrants(
        @PathVariable role: Role,
        @RequestBody body: FeatureGrantsUpdateRequest,
    ): FeatureGrantAllowsResponse {
        accessService.requireCanManageUsers()
        featureGrantService.replaceRoleGrants(role, body.grants)
        val grants = featureGrantService.roleGrantsMatrix()[role.name].orEmpty()
        return FeatureGrantAllowsResponse(grants = grants)
    }

    @GetMapping("/users/{id}/feature-grant-allows")
    @Operation(summary = "Get per-user feature grant allows")
    fun getUserFeatureGrantAllows(@PathVariable id: UUID): FeatureGrantAllowsResponse {
        accessService.requireCanManageUsers()
        requireUserExists(id)
        return FeatureGrantAllowsResponse(grants = featureGrantService.userAllows(id))
    }

    @PutMapping("/users/{id}/feature-grant-allows")
    @Operation(summary = "Replace per-user feature grant allows")
    fun replaceUserFeatureGrantAllows(
        @PathVariable id: UUID,
        @RequestBody body: FeatureGrantsUpdateRequest,
    ): FeatureGrantAllowsResponse {
        accessService.requireCanManageUsers()
        requireUserExists(id)
        featureGrantService.replaceUserAllows(id, body.grants)
        return FeatureGrantAllowsResponse(grants = featureGrantService.userAllows(id))
    }

    private fun requireUserExists(id: UUID) {
        if (!usersRepository.existsById(id)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "User $id not found")
        }
    }
}
