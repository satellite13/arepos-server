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
import java.time.Instant
import java.util.*

interface NodeTreePageProjection {
    fun getId(): UUID
    fun getStableId(): UUID
    fun getName(): String
    fun getModelId(): UUID
    fun getOwnerId(): UUID
    fun getNodeTypeId(): UUID
    fun getParentNodeId(): UUID?
    fun getAttrs(): String?
    fun getCreatedAt(): Instant?
    fun getUpdatedAt(): Instant?
    fun getHasChildren(): Boolean
}

interface NodeAncestorProjection {
    fun getId(): UUID
    fun getStableId(): UUID
    fun getName(): String
    fun getModelId(): UUID
    fun getOwnerId(): UUID
    fun getNodeTypeId(): UUID
    fun getParentNodeId(): UUID?
    fun getAttrs(): String?
    fun getCreatedAt(): Instant?
    fun getUpdatedAt(): Instant?
    fun getHasChildren(): Boolean
    fun getHiddenTreeRoot(): Boolean
    fun getDepth(): Int
    fun getCycle(): Boolean
}

interface DuplicateNodeMemberProjection {
    fun getNodeTypeId(): UUID
    fun getNodeTypeName(): String
    fun getNameKey(): String
    fun getGroupCount(): Long
    fun getId(): UUID
    fun getName(): String
    fun getParentId(): UUID?
    fun getParentName(): String?
}

@Repository
interface NodesRepository : JpaRepository<Nodes, UUID> {
    fun findByModel(model: Models, pageable: Pageable): Page<Nodes>
    fun findByOwner(owner: Users, pageable: Pageable): Page<Nodes>
    fun findByNameContainingIgnoreCase(name: String, pageable: Pageable): Page<Nodes>
    fun existsByNodeTypeId(nodeTypeId: UUID): Boolean
    fun existsByIdAndModel_Id(id: UUID, modelId: UUID): Boolean
    fun existsByParentNode_Id(parentNodeId: UUID): Boolean
    fun findByModel_IdAndIdIn(modelId: UUID, ids: Collection<UUID>): List<Nodes>

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
            SELECT
                n.id AS id,
                n.stable_id AS "stableId",
                n.name AS name,
                n.model AS "modelId",
                n.owner AS "ownerId",
                n.node_type AS "nodeTypeId",
                n.parent_node AS "parentNodeId",
                CAST(n.attrs AS text) AS attrs,
                n.created_at AS "createdAt",
                n.updated_at AS "updatedAt",
                EXISTS (
                    SELECT 1
                    FROM nodes child
                    JOIN node_types child_type ON child_type.id = child.node_type
                    WHERE child.model = :modelId
                      AND child.parent_node = n.id
                      AND (
                          NOT :excludeSystem
                          OR LOWER(COALESCE(child.attrs #>> '{system,hiddenTreeRoot}', 'false')) <> 'true'
                      )
                      AND (NOT :foldersOnly OR LOWER(child_type.name) = 'directory')
                ) AS hasChildren
            FROM nodes n
            JOIN node_types node_type ON node_type.id = n.node_type
            WHERE n.model = :modelId
              AND n.parent_node IS NOT DISTINCT FROM CAST(:parentNodeId AS uuid)
              AND (
                  NOT :excludeSystem
                  OR LOWER(COALESCE(n.attrs #>> '{system,hiddenTreeRoot}', 'false')) <> 'true'
              )
              AND (NOT :foldersOnly OR LOWER(node_type.name) = 'directory')
            ORDER BY COALESCE((n.attrs ->> 'treeOrder')::int, 0), n.id
        """,
        countQuery = """
            SELECT COUNT(*)
            FROM nodes n
            JOIN node_types node_type ON node_type.id = n.node_type
            WHERE n.model = :modelId
              AND n.parent_node IS NOT DISTINCT FROM CAST(:parentNodeId AS uuid)
              AND (
                  NOT :excludeSystem
                  OR LOWER(COALESCE(n.attrs #>> '{system,hiddenTreeRoot}', 'false')) <> 'true'
              )
              AND (NOT :foldersOnly OR LOWER(node_type.name) = 'directory')
        """,
        nativeQuery = true
    )
    fun findDirectChildrenPage(
        @Param("modelId") modelId: UUID,
        @Param("parentNodeId") parentNodeId: UUID?,
        @Param("excludeSystem") excludeSystem: Boolean,
        @Param("foldersOnly") foldersOnly: Boolean,
        pageable: Pageable
    ): Page<NodeTreePageProjection>

    @Query(
        value = """
            WITH RECURSIVE ancestor_path AS (
                SELECT
                    n.id,
                    n.stable_id,
                    n.name,
                    n.model,
                    n.owner,
                    n.node_type,
                    n.parent_node,
                    n.attrs,
                    n.created_at,
                    n.updated_at,
                    0 AS depth,
                    ARRAY[n.id]::uuid[] AS visited,
                    false AS cycle
                FROM nodes n
                WHERE n.id = :nodeId
                  AND n.model = :modelId

                UNION ALL

                SELECT
                    parent.id,
                    parent.stable_id,
                    parent.name,
                    parent.model,
                    parent.owner,
                    parent.node_type,
                    parent.parent_node,
                    parent.attrs,
                    parent.created_at,
                    parent.updated_at,
                    path.depth + 1,
                    path.visited || parent.id,
                    parent.id = ANY(path.visited)
                FROM ancestor_path path
                JOIN nodes parent ON parent.id = path.parent_node
                WHERE path.parent_node IS NOT NULL
                  AND path.depth < :maxDepthPlusOne
                  AND NOT path.cycle
                  AND LOWER(COALESCE(path.attrs #>> '{system,hiddenTreeRoot}', 'false')) <> 'true'
                  AND (
                      CAST(:configuredRootId AS uuid) IS NULL
                      OR path.id <> CAST(:configuredRootId AS uuid)
                  )
            )
            SELECT
                path.id AS id,
                path.stable_id AS "stableId",
                path.name AS name,
                path.model AS "modelId",
                path.owner AS "ownerId",
                path.node_type AS "nodeTypeId",
                path.parent_node AS "parentNodeId",
                CAST(path.attrs AS text) AS attrs,
                path.created_at AS "createdAt",
                path.updated_at AS "updatedAt",
                EXISTS (
                    SELECT 1
                    FROM nodes child
                    WHERE child.model = :modelId
                      AND child.parent_node = path.id
                      AND LOWER(COALESCE(child.attrs #>> '{system,hiddenTreeRoot}', 'false')) <> 'true'
                ) AS "hasChildren",
                LOWER(COALESCE(path.attrs #>> '{system,hiddenTreeRoot}', 'false')) = 'true'
                    AS "hiddenTreeRoot",
                path.depth AS depth,
                path.cycle AS cycle
            FROM ancestor_path path
            ORDER BY path.depth DESC
        """,
        nativeQuery = true
    )
    fun findAncestorPath(
        @Param("modelId") modelId: UUID,
        @Param("nodeId") nodeId: UUID,
        @Param("configuredRootId") configuredRootId: UUID?,
        @Param("maxDepthPlusOne") maxDepthPlusOne: Int
    ): List<NodeAncestorProjection>

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

    fun findByModel_IdAndParentNode_IdAndNameIgnoreCase(
        modelId: UUID,
        parentNodeId: UUID,
        name: String
    ): List<Nodes>

    @Query(
        """
        SELECT n FROM Nodes n
        WHERE n.model.id = :modelId
          AND n.parentNode IS NULL
          AND LOWER(n.name) = LOWER(:name)
        """
    )
    fun findRootByModelIdAndNameIgnoreCase(
        @Param("modelId") modelId: UUID,
        @Param("name") name: String
    ): List<Nodes>

    @Query(
        value = """
            SELECT *
            FROM nodes n
            WHERE n.model = :modelId
              AND n.name ILIKE CONCAT('%', :q, '%')
              AND LOWER(COALESCE(n.attrs #>> '{system,hiddenTreeRoot}', 'false')) <> 'true'
            ORDER BY n.name ASC, n.id ASC
        """,
        countQuery = """
            SELECT COUNT(*)
            FROM nodes n
            WHERE n.model = :modelId
              AND n.name ILIKE CONCAT('%', :q, '%')
              AND LOWER(COALESCE(n.attrs #>> '{system,hiddenTreeRoot}', 'false')) <> 'true'
        """,
        nativeQuery = true
    )
    fun searchByModelIdAndName(
        @Param("modelId") modelId: UUID,
        @Param("q") q: String,
        pageable: Pageable
    ): Page<Nodes>

    @Query(
        value = """
            WITH duplicate_groups AS (
                SELECT n.node_type AS node_type_id,
                       lower(trim(n.name)) AS name_key,
                       COUNT(*) AS cnt
                FROM nodes n
                JOIN node_types t ON t.id = n.node_type
                WHERE n.model = :modelId
                  AND lower(t.name) <> 'directory'
                GROUP BY n.node_type, lower(trim(n.name))
                HAVING COUNT(*) > 1
            ),
            ranked AS (
                SELECT
                    n.id,
                    n.name,
                    n.node_type AS node_type_id,
                    t.name AS node_type_name,
                    n.parent_node AS parent_id,
                    p.name AS parent_name,
                    p.attrs AS parent_attrs,
                    g.name_key,
                    g.cnt,
                    m.attrs AS model_attrs,
                    ROW_NUMBER() OVER (
                        PARTITION BY n.node_type, g.name_key
                        ORDER BY n.id ASC
                    ) AS rn
                FROM nodes n
                JOIN duplicate_groups g
                  ON g.node_type_id = n.node_type
                 AND g.name_key = lower(trim(n.name))
                JOIN node_types t ON t.id = n.node_type
                JOIN models m ON m.id = n.model
                LEFT JOIN nodes p ON p.id = n.parent_node
                WHERE n.model = :modelId
            )
            SELECT
                r.node_type_id AS "nodeTypeId",
                r.node_type_name AS "nodeTypeName",
                r.name_key AS "nameKey",
                r.cnt AS "groupCount",
                r.id AS id,
                r.name AS name,
                CASE
                    WHEN r.parent_id IS NULL THEN NULL
                    WHEN r.parent_id::text = NULLIF(TRIM(r.model_attrs ->> 'treeRootNodeId'), '') THEN NULL
                    WHEN LOWER(COALESCE(r.parent_attrs #>> '{system,hiddenTreeRoot}', 'false')) = 'true' THEN NULL
                    ELSE r.parent_id
                END AS "parentId",
                CASE
                    WHEN r.parent_id IS NULL THEN NULL
                    WHEN r.parent_id::text = NULLIF(TRIM(r.model_attrs ->> 'treeRootNodeId'), '') THEN NULL
                    WHEN LOWER(COALESCE(r.parent_attrs #>> '{system,hiddenTreeRoot}', 'false')) = 'true' THEN NULL
                    ELSE r.parent_name
                END AS "parentName"
            FROM ranked r
            WHERE r.rn <= 50
            ORDER BY r.node_type_id, r.name_key, r.id
        """,
        nativeQuery = true
    )
    fun findDuplicateNodeMembers(@Param("modelId") modelId: UUID): List<DuplicateNodeMemberProjection>
}


