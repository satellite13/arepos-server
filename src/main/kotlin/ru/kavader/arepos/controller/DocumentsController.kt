package ru.kavader.arepos.controller

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import ru.kavader.arepos.service.DocumentItem
import ru.kavader.arepos.service.DocumentRefsService
import ru.kavader.arepos.service.RegisterDocumentRefRequest
import java.util.UUID

@RestController
@RequestMapping("/api/v1/documents")
@ConditionalOnBean(DocumentRefsService::class)
class DocumentsController(
    private val documentRefsService: DocumentRefsService
) {

    @PostMapping
    fun registerRef(@RequestBody request: RegisterDocumentRefRequest): DocumentItem =
        documentRefsService.registerRef(request)

    @GetMapping
    fun list(
        @RequestParam(required = false) modelId: UUID?,
        @RequestParam(required = false) notationId: UUID?,
        @RequestParam(required = false) componentId: UUID?,
        @RequestParam(required = false) nodeId: UUID?,
        @RequestParam(required = false) nodeTypeId: UUID?,
        @RequestParam(required = false) linkTypeId: UUID?
    ) = documentRefsService.listForSelect(
        modelId = modelId,
        notationId = notationId,
        componentId = componentId,
        nodeId = nodeId,
        nodeTypeId = nodeTypeId,
        linkTypeId = linkTypeId
    )
}
