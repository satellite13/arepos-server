package ru.kavader.arepos.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import ru.kavader.arepos.model.Models
import ru.kavader.arepos.model.Nodes
import ru.kavader.arepos.model.Users
import java.util.*

@Repository
interface NodesRepository : JpaRepository<Nodes, UUID> {
    fun findByModel(model: Models, pageable: Pageable): Page<Nodes>
    fun findByOwner(owner: Users, pageable: Pageable): Page<Nodes>
    fun findByNameContainingIgnoreCase(name: String, pageable: Pageable): Page<Nodes>
    fun existsByNodeTypeId(nodeTypeId: UUID): Boolean

    @Query(
        value = """
            SELECT *
            FROM nodes n
            WHERE n.model = :modelId
            ORDER BY
              n.parent_node NULLS FIRST,
              COALESCE((n.attrs ->> 'treeOrder')::int, 0),
              n.id
        """,
        countQuery = """
            SELECT COUNT(*)
            FROM nodes n
            WHERE n.model = :modelId
        """,
        nativeQuery = true
    )
    fun findByModelIdOrdered(
        @Param("modelId") modelId: UUID,
        pageable: Pageable
    ): Page<Nodes>

    @Query(
        value = """
            SELECT *
            FROM nodes n
            WHERE (:modelId IS NULL OR n.model = :modelId)
              AND (:ownerId IS NULL OR n.owner = :ownerId)
              AND (:name IS NULL OR n.name ILIKE CONCAT('%', :name, '%'))
              AND EXISTS (
                  SELECT 1
                  FROM models m
                  WHERE m.id = n.model
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
            ORDER BY n.id
        """,
        countQuery = """
            SELECT COUNT(*)
            FROM nodes n
            WHERE (:modelId IS NULL OR n.model = :modelId)
              AND (:ownerId IS NULL OR n.owner = :ownerId)
              AND (:name IS NULL OR n.name ILIKE CONCAT('%', :name, '%'))
              AND EXISTS (
                  SELECT 1
                  FROM models m
                  WHERE m.id = n.model
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
        @Param("name") name: String?,
        @Param("currentUserId") currentUserId: UUID,
        pageable: Pageable
    ): Page<Nodes>

    @Query(
        value = """
            SELECT DISTINCT n.node_type
            FROM nodes n
            WHERE n.model = :modelId
        """,
        nativeQuery = true
    )
    fun findDistinctNodeTypeIdsByModelId(@Param("modelId") modelId: UUID): List<UUID>

    fun findByModelIdAndStableIdIn(modelId: UUID, stableIds: Collection<UUID>): List<Nodes>
}


