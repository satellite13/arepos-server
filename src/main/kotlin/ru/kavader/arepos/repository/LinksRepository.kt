package ru.kavader.arepos.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import ru.kavader.arepos.model.*
import java.util.*

@Repository
interface LinksRepository : JpaRepository<Links, UUID> {
    fun findByModelOrderByIdAsc(model: Models, pageable: Pageable): Page<Links>
    fun findByOwner(owner: Users, pageable: Pageable): Page<Links>
    fun findBySource(source: Nodes, pageable: Pageable): Page<Links>
    fun findByTarget(target: Nodes, pageable: Pageable): Page<Links>
    fun findByLinkType(linkType: LinkTypes, pageable: Pageable): Page<Links>
    fun findByModelAndOwnerOrderByIdAsc(model: Models, owner: Users, pageable: Pageable): Page<Links>
    fun existsByLinkTypeId(linkTypeId: UUID): Boolean

    fun findByModel_IdAndIdIn(modelId: UUID, ids: Collection<UUID>): List<Links>

    @Query(
        """
        SELECT l.id FROM Links l
        WHERE l.model.id = :modelId
          AND l.id IN :linkIds
        """
    )
    fun findIdsByModelIdAndIdIn(
        @Param("modelId") modelId: UUID,
        @Param("linkIds") linkIds: Collection<UUID>
    ): List<UUID>

    @Query(
        """
        SELECT l.id FROM Links l
        WHERE l.model.id = :modelId
          AND (l.source.id IN :endpointNodeIds OR l.target.id IN :endpointNodeIds)
        ORDER BY l.id ASC
        """
    )
    fun findIdsByModelIdAndEndpointNodeIds(
        @Param("modelId") modelId: UUID,
        @Param("endpointNodeIds") endpointNodeIds: Collection<UUID>,
        pageable: Pageable
    ): List<UUID>

    fun findByModel_IdAndSource_IdAndTarget_IdAndLinkType_Id(
        modelId: UUID,
        sourceId: UUID,
        targetId: UUID,
        linkTypeId: UUID
    ): List<Links>

    fun findByModelIdAndStableIdIn(modelId: UUID, stableIds: Collection<UUID>): List<Links>

    @Query(
        value = """
            SELECT l.*
            FROM links l
            WHERE (:modelId IS NULL OR l.model = :modelId)
              AND (:ownerId IS NULL OR l.owner = :ownerId)
              AND (:sourceId IS NULL OR l.source = :sourceId)
              AND (:targetId IS NULL OR l.target = :targetId)
              AND (:linkTypeId IS NULL OR l.link_type = :linkTypeId)
              AND EXISTS (
                  SELECT 1
                  FROM models m
                  WHERE m.id = l.model
                    AND m.deleted = false
                    AND (
                        m.owner = :currentUserId
                        OR EXISTS (
                            SELECT 1
                            FROM resource_shares rs
                            WHERE rs.resource_type = 'MODEL'
                              AND rs.resource_id = m.id
                              AND rs.permission IN ('VIEW', 'EDIT')
                              AND (rs.grantee_user_id = :currentUserId OR rs.grantee_user_id IS NULL)
                        )
                    )
              )
            ORDER BY l.id
        """,
        countQuery = """
            SELECT COUNT(*)
            FROM links l
            WHERE (:modelId IS NULL OR l.model = :modelId)
              AND (:ownerId IS NULL OR l.owner = :ownerId)
              AND (:sourceId IS NULL OR l.source = :sourceId)
              AND (:targetId IS NULL OR l.target = :targetId)
              AND (:linkTypeId IS NULL OR l.link_type = :linkTypeId)
              AND EXISTS (
                  SELECT 1
                  FROM models m
                  WHERE m.id = l.model
                    AND m.deleted = false
                    AND (
                        m.owner = :currentUserId
                        OR EXISTS (
                            SELECT 1
                            FROM resource_shares rs
                            WHERE rs.resource_type = 'MODEL'
                              AND rs.resource_id = m.id
                              AND rs.permission IN ('VIEW', 'EDIT')
                              AND (rs.grantee_user_id = :currentUserId OR rs.grantee_user_id IS NULL)
                        )
                    )
              )
        """,
        nativeQuery = true
    )
    fun findAccessibleByFiltersForUser(
        @Param("modelId") modelId: UUID?,
        @Param("ownerId") ownerId: UUID?,
        @Param("sourceId") sourceId: UUID?,
        @Param("targetId") targetId: UUID?,
        @Param("linkTypeId") linkTypeId: UUID?,
        @Param("currentUserId") currentUserId: UUID,
        pageable: Pageable
    ): Page<Links>

    @Query(
        value = """
            SELECT DISTINCT l.link_type
            FROM links l
            WHERE l.model = :modelId
        """,
        nativeQuery = true
    )
    fun findDistinctLinkTypeIdsByModelId(@Param("modelId") modelId: UUID): List<UUID>

    @Query(
        """
        SELECT l FROM Links l
        WHERE l.model.id = :modelId
          AND (
            LOWER(l.source.name) LIKE LOWER(CONCAT('%', :q, '%'))
            OR LOWER(l.target.name) LIKE LOWER(CONCAT('%', :q, '%'))
          )
        ORDER BY l.source.name ASC, l.target.name ASC, l.id ASC
        """
    )
    fun searchByModelIdAndEndpointNames(
        @Param("modelId") modelId: UUID,
        @Param("q") q: String,
        pageable: Pageable
    ): Page<Links>
}


