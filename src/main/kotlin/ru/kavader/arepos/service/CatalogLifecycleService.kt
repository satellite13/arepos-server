package ru.kavader.arepos.service

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.model.LinkTypes
import ru.kavader.arepos.model.NodeShapes
import ru.kavader.arepos.model.NodeTypes
import ru.kavader.arepos.model.ShareResourceType
import ru.kavader.arepos.repository.ComponentsRepository
import ru.kavader.arepos.repository.DocumentRefsRepository
import ru.kavader.arepos.repository.LinkTypesRepository
import ru.kavader.arepos.repository.LinksRepository
import ru.kavader.arepos.repository.NodeShapesRepository
import ru.kavader.arepos.repository.NodeTypesRepository
import ru.kavader.arepos.repository.NodesRepository
import ru.kavader.arepos.repository.RelationsRepository
import ru.kavader.arepos.repository.ResourceSharesRepository
import java.util.UUID

@Service
class CatalogLifecycleService(
    private val nodeTypesRepository: NodeTypesRepository,
    private val linkTypesRepository: LinkTypesRepository,
    private val nodeShapesRepository: NodeShapesRepository,
    private val nodesRepository: NodesRepository,
    private val linksRepository: LinksRepository,
    private val componentsRepository: ComponentsRepository,
    private val relationsRepository: RelationsRepository,
    private val documentRefsRepository: DocumentRefsRepository,
    private val resourceSharesRepository: ResourceSharesRepository,
    private val systemRootNodeTypeService: SystemRootNodeTypeService
) {
    @Transactional
    fun softDeleteNodeType(id: UUID) {
        val nodeType = nodeTypesRepository.findById(id).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "NodeType $id not found")
        }
        systemRootNodeTypeService.assertMutable(nodeType)
        val deletedCount = nodeTypesRepository.softDeleteById(id)
        if (deletedCount == 0) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "NodeType $id not found")
        }
    }

    @Transactional
    fun permanentDeleteNodeType(nodeType: NodeTypes) {
        systemRootNodeTypeService.assertMutable(nodeType)
        val id = nodeType.id
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "NodeType id is required")
        if (nodesRepository.existsByNodeTypeId(id) || componentsRepository.existsByNodeTypeId(id)) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "Node type is still used by nodes or components"
            )
        }
        documentRefsRepository.deleteAllByNodeTypeId(id)
        resourceSharesRepository.deleteByResourceTypeAndResourceId(ShareResourceType.NODE_TYPE, id)
        nodeTypesRepository.delete(nodeType)
    }

    @Transactional
    fun softDeleteLinkType(id: UUID) {
        val deletedCount = linkTypesRepository.softDeleteById(id)
        if (deletedCount == 0) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "LinkType $id not found")
        }
    }

    @Transactional
    fun permanentDeleteLinkType(linkType: LinkTypes) {
        val id = linkType.id
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "LinkType id is required")
        if (linksRepository.existsByLinkTypeId(id) || relationsRepository.existsByLinkTypeId(id)) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "Link type is still used by links or relations"
            )
        }
        documentRefsRepository.deleteAllByLinkTypeId(id)
        resourceSharesRepository.deleteByResourceTypeAndResourceId(ShareResourceType.LINK_TYPE, id)
        linkTypesRepository.delete(linkType)
    }

    @Transactional
    fun softDeleteNodeShape(id: UUID) {
        val deletedCount = nodeShapesRepository.softDeleteById(id)
        if (deletedCount == 0) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "NodeShape $id not found")
        }
    }

    @Transactional
    fun permanentDeleteNodeShape(shape: NodeShapes) {
        val id = shape.id
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "NodeShape id is required")
        documentRefsRepository.deleteAllByNodeShapeId(id)
        resourceSharesRepository.deleteByResourceTypeAndResourceId(ShareResourceType.NODE_SHAPE, id)
        nodeShapesRepository.delete(shape)
    }
}
