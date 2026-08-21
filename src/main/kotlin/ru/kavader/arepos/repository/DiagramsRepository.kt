package ru.kavader.arepos.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import ru.kavader.arepos.model.Diagrams
import ru.kavader.arepos.model.Models
import java.util.*

interface DiagramReferenceProjection {
    fun getId(): UUID
    fun getName(): String
    fun getVersion(): String
    fun getNotationId(): UUID
    fun getNodeId(): UUID?
}

@Repository
interface DiagramsRepository : JpaRepository<Diagrams, UUID> {

    fun findByModelIdAndNameAndDeletedFalse(modelId: UUID, name: String): List<Diagrams>

    fun findByModelIdAndName(modelId: UUID, name: String): List<Diagrams>

    @Query(
        value = """
            SELECT
                d.id,
                d.name,
                d.version,
                d.notation_id AS "notationId",
                d.node_id AS "nodeId"
            FROM diagrams d
            WHERE d.model = :modelId
              AND d.deleted = false
              AND d.attrs @@ CAST(:nodeJsonPath AS jsonpath)
            ORDER BY d.name, d.id
        """,
        countQuery = """
            SELECT COUNT(*)
            FROM diagrams d
            WHERE d.model = :modelId
              AND d.deleted = false
              AND d.attrs @@ CAST(:nodeJsonPath AS jsonpath)
        """,
        nativeQuery = true
    )
    fun findDiagramReferences(
        modelId: UUID,
        nodeJsonPath: String,
        pageable: Pageable
    ): Page<DiagramReferenceProjection>

    @Query("SELECT d FROM Diagrams d WHERE d.deleted = false")
    override fun findAll(pageable: Pageable): Page<Diagrams>

    @Query("SELECT d FROM Diagrams d WHERE d.id = :id AND d.deleted = false")
    override fun findById(id: UUID): Optional<Diagrams>

    @Query(
        """
        SELECT d FROM Diagrams d
        WHERE d.deleted = false
          AND (:ownerId IS NULL OR d.owner.id = :ownerId)
          AND (:modelId IS NULL OR d.model.id = :modelId)
          AND (:nodeId IS NULL OR d.node.id = :nodeId)
          AND (:notationId IS NULL OR d.notation.id = :notationId)
          AND LOWER(d.name) LIKE LOWER(CONCAT('%', :name, '%'))
        """
    )
    fun findByFilters(
        ownerId: UUID?,
        modelId: UUID?,
        nodeId: UUID?,
        notationId: UUID?,
        name: String,
        pageable: Pageable
    ): Page<Diagrams>

    @Query(
        "SELECT CASE WHEN COUNT(d) > 0 THEN true ELSE false END " +
                "FROM Diagrams d WHERE d.model.id = :#{#model.id} AND d.name = :name AND d.version = :version"
    )
    fun existsByModelAndNameAndVersion(model: Models, name: String, version: String): Boolean

    @Query(
        "SELECT CASE WHEN COUNT(d) > 0 THEN true ELSE false END " +
                "FROM Diagrams d WHERE d.model.id = :#{#model.id} AND d.name = :name AND d.version = :version AND d.id != :id"
    )
    fun existsByModelAndNameAndVersionAndIdNot(model: Models, name: String, version: String, id: UUID): Boolean

    fun existsByModelIdAndNotationIdAndDeletedFalse(modelId: UUID, notationId: UUID): Boolean

    @Query("SELECT d FROM Diagrams d JOIN FETCH d.model WHERE d.notation.id = :notationId")
    fun findAllByNotationIdWithModel(notationId: UUID): List<Diagrams>

    @Query("SELECT CASE WHEN COUNT(d) > 0 THEN true ELSE false END FROM Diagrams d WHERE d.id = :id AND d.deleted = false")
    override fun existsById(id: UUID): Boolean

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Diagrams d SET d.deleted = true WHERE d.id = :id")
    fun softDeleteById(id: UUID): Int

    @Query(
        value = """
            SELECT d.*
            FROM diagrams d
            WHERE d.deleted = false
              AND (:ownerId IS NULL OR d.owner = :ownerId)
              AND (:modelId IS NULL OR d.model = :modelId)
              AND (:nodeId IS NULL OR d.node_id = :nodeId)
              AND (:notationId IS NULL OR d.notation_id = :notationId)
              AND (:name IS NULL OR d.name ILIKE CONCAT('%', :name, '%'))
              AND EXISTS (
                  SELECT 1
                  FROM models m
                  WHERE m.id = d.model
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
        countQuery = """
            SELECT COUNT(*)
            FROM diagrams d
            WHERE d.deleted = false
              AND (:ownerId IS NULL OR d.owner = :ownerId)
              AND (:modelId IS NULL OR d.model = :modelId)
              AND (:nodeId IS NULL OR d.node_id = :nodeId)
              AND (:notationId IS NULL OR d.notation_id = :notationId)
              AND (:name IS NULL OR d.name ILIKE CONCAT('%', :name, '%'))
              AND EXISTS (
                  SELECT 1
                  FROM models m
                  WHERE m.id = d.model
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
        ownerId: UUID?,
        modelId: UUID?,
        nodeId: UUID?,
        notationId: UUID?,
        name: String?,
        currentUserId: UUID,
        pageable: Pageable
    ): Page<Diagrams>

    /**
     * Нотация используется активной диаграммой в модели, которую пользователь может просматривать
     * (владелец или шаринг MODEL с VIEW/EDIT, в т.ч. grantee_user_id IS NULL).
     */
    @Query(
        value = """
            SELECT EXISTS (
                SELECT 1
                FROM diagrams d
                INNER JOIN models m ON m.id = d.model
                WHERE d.deleted = false
                  AND d.notation_id = :notationId
                  AND m.deleted = false
                  AND (
                      m.owner = :userId
                      OR EXISTS (
                          SELECT 1
                          FROM resource_shares rs
                          WHERE rs.resource_type = 'MODEL'
                            AND rs.resource_id = m.id
                            AND rs.permission IN ('VIEW', 'EDIT')
                            AND (rs.grantee_user_id = :userId OR rs.grantee_user_id IS NULL)
                      )
                  )
            )
        """,
        nativeQuery = true
    )
    fun existsViewableModelDiagramWithNotation(notationId: UUID, userId: UUID): Boolean

    @Query(
        value = """
            SELECT DISTINCT d.notation_id
            FROM diagrams d
            WHERE d.deleted = false
              AND d.model = :modelId
        """,
        nativeQuery = true
    )
    fun findDistinctNotationIdsByModelId(modelId: UUID): List<UUID>

    @Query("SELECT d FROM Diagrams d WHERE d.model.id = :modelId AND d.deleted = false")
    fun findAllActiveByModelId(modelId: UUID): List<Diagrams>

    @Query("SELECT d FROM Diagrams d WHERE d.model.id = :modelId AND d.deleted = true")
    fun findAllDeletedByModelId(modelId: UUID): List<Diagrams>

    @Query(
        value = """
            SELECT d.*
            FROM diagrams d
            WHERE d.deleted = false
              AND EXISTS (
                  SELECT 1
                  FROM models m
                  WHERE m.id = d.model
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
            ORDER BY d.updated_at DESC NULLS LAST
            LIMIT :limit
        """,
        nativeQuery = true
    )
    fun findRecentAccessibleForUser(currentUserId: UUID, limit: Int): List<Diagrams>

    @Query(
        "SELECT d FROM Diagrams d JOIN FETCH d.model WHERE d.deleted = false ORDER BY d.updatedAt DESC NULLS LAST"
    )
    fun findRecentWithModel(pageable: Pageable): List<Diagrams>

    @Query("SELECT d FROM Diagrams d JOIN FETCH d.model WHERE d.id IN :ids")
    fun findAllWithModelByIdIn(ids: Collection<UUID>): List<Diagrams>
}
