package ru.kavader.arepos.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.*
import ru.kavader.arepos.dto.system.RelationRulesSyncRequest
import ru.kavader.arepos.dto.system.RelationRulesSyncResponse
import ru.kavader.arepos.service.RelationRulesSyncService
import java.util.*

@RestController
@RequestMapping("/api/v1/notations/{notationId}/relation-rules")
@Tag(name = "Relation Rules Sync", description = "Bulk relation rules synchronization endpoints")
class RelationRulesSyncController(
    private val relationRulesSyncService: RelationRulesSyncService
) {

    @PutMapping("/sync")
    @Operation(summary = "Synchronize relation rules for notation")
    fun syncRelationRules(
        @PathVariable notationId: UUID,
        @RequestBody @Valid request: RelationRulesSyncRequest
    ): RelationRulesSyncResponse = relationRulesSyncService.sync(notationId, request)
}

