package ru.kavader.arepos.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.web.bind.annotation.*
import ru.kavader.arepos.dto.document.DocumentItem
import ru.kavader.arepos.dto.document.RegisterDocumentRefRequest
import ru.kavader.arepos.service.DocumentRefsService
import java.util.*

@RestController
@RequestMapping("/api/v1/documents")
@ConditionalOnBean(DocumentRefsService::class)
@Tag(name = "Documents", description = "Document reference registration and lookup")
class DocumentsController(
    private val documentRefsService: DocumentRefsService
) {

    @PostMapping
    @Operation(summary = "Register document reference in entity context")
    fun registerRef(@RequestBody @Valid request: RegisterDocumentRefRequest): DocumentItem =
        documentRefsService.registerRef(request)

    @GetMapping
    @Operation(summary = "List documents for selected context filters")
    fun list(
        @RequestParam(required = false) modelId: UUID?,
        @RequestParam(required = false) notationId: UUID?,
        @RequestParam(required = false) componentId: UUID?,
        @RequestParam(required = false) nodeId: UUID?,
        @RequestParam(required = false) nodeTypeId: UUID?,
        @RequestParam(required = false) linkTypeId: UUID?,
        @RequestParam(required = false) diagramId: UUID?,
        @RequestParam(required = false) relationId: UUID?,
        @RequestParam(required = false) nodeShapeId: UUID?
    ) = documentRefsService.listForSelect(
        modelId = modelId,
        notationId = notationId,
        componentId = componentId,
        nodeId = nodeId,
        nodeTypeId = nodeTypeId,
        linkTypeId = linkTypeId,
        diagramId = diagramId,
        relationId = relationId,
        nodeShapeId = nodeShapeId
    )
}
