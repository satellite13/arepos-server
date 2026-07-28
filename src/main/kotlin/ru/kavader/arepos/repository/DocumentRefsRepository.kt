package ru.kavader.arepos.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import ru.kavader.arepos.model.DocumentRefs
import java.util.*

@Repository
interface DocumentRefsRepository : JpaRepository<DocumentRefs, UUID> {

    fun findFirstByFileIdAndNodeTypeId(fileId: UUID, nodeTypeId: UUID): Optional<DocumentRefs>
    fun findFirstByFileIdAndLinkTypeId(fileId: UUID, linkTypeId: UUID): Optional<DocumentRefs>

    fun findAllByFileId(fileId: UUID): List<DocumentRefs>

    fun findAllByModelId(modelId: UUID): List<DocumentRefs>

    /**
     * Removes wiki/document bindings for a model before hard-delete.
     * Needed because document_refs use ON DELETE SET NULL + CHECK(at least one context),
     * so cascading FK nulling alone fails with a check violation.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        DELETE FROM DocumentRefs dr
        WHERE dr.model.id = :modelId
           OR dr.node.id IN (SELECT n.id FROM Nodes n WHERE n.model.id = :modelId)
           OR dr.diagram.id IN (SELECT d.id FROM Diagrams d WHERE d.model.id = :modelId)
        """
    )
    fun deleteAllBelongingToModel(@Param("modelId") modelId: UUID): Int

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM DocumentRefs dr WHERE dr.nodeType.id = :nodeTypeId")
    fun deleteAllByNodeTypeId(@Param("nodeTypeId") nodeTypeId: UUID): Int

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM DocumentRefs dr WHERE dr.linkType.id = :linkTypeId")
    fun deleteAllByLinkTypeId(@Param("linkTypeId") linkTypeId: UUID): Int

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM DocumentRefs dr WHERE dr.nodeShape.id = :nodeShapeId")
    fun deleteAllByNodeShapeId(@Param("nodeShapeId") nodeShapeId: UUID): Int

    fun findAllByNodeIdIn(nodeIds: Collection<UUID>): List<DocumentRefs>

    fun findAllByDiagramIdIn(diagramIds: Collection<UUID>): List<DocumentRefs>

    fun findAllByNotationIdIn(notationIds: Collection<UUID>): List<DocumentRefs>

    fun findAllByComponentIdIn(componentIds: Collection<UUID>): List<DocumentRefs>

    fun findAllByRelationIdIn(relationIds: Collection<UUID>): List<DocumentRefs>

    @Query(
        """
        SELECT dr FROM DocumentRefs dr
        JOIN FETCH dr.file f
        WHERE dr.createdBy.id = :userId
          AND (:modelId IS NULL OR dr.model.id = :modelId)
          AND (:notationId IS NULL OR dr.notation.id = :notationId)
          AND (:componentId IS NULL OR dr.component.id = :componentId)
          AND (:nodeId IS NULL OR dr.node.id = :nodeId)
          AND (:nodeTypeId IS NULL OR dr.nodeType.id = :nodeTypeId)
          AND (:linkTypeId IS NULL OR dr.linkType.id = :linkTypeId)
          AND (:diagramId IS NULL OR dr.diagram.id = :diagramId)
          AND (:relationId IS NULL OR dr.relation.id = :relationId)
          AND (:nodeShapeId IS NULL OR dr.nodeShape.id = :nodeShapeId)
        ORDER BY dr.createdAt DESC
        """
    )
    fun findByFilters(
        @Param("userId") userId: UUID,
        @Param("modelId") modelId: UUID?,
        @Param("notationId") notationId: UUID?,
        @Param("componentId") componentId: UUID?,
        @Param("nodeId") nodeId: UUID?,
        @Param("nodeTypeId") nodeTypeId: UUID?,
        @Param("linkTypeId") linkTypeId: UUID?,
        @Param("diagramId") diagramId: UUID?,
        @Param("relationId") relationId: UUID?,
        @Param("nodeShapeId") nodeShapeId: UUID?
    ): List<DocumentRefs>
}
