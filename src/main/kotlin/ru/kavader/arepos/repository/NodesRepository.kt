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
import java.util.UUID

@Repository
interface NodesRepository : JpaRepository<Nodes, UUID> {
    fun findByModel(model: Models, pageable: Pageable): Page<Nodes>
    fun findByOwner(owner: Users, pageable: Pageable): Page<Nodes>
    fun findByNameContainingIgnoreCase(name: String, pageable: Pageable): Page<Nodes>

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
}


