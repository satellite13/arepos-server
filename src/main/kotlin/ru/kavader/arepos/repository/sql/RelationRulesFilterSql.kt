package ru.kavader.arepos.repository.sql

/**
 * Shared FROM/JOIN + filter WHERE for relation_rules list queries.
 * SELECT lists differ for entity / projection / light.
 */
object RelationRulesFilterSql {
    const val FROM_JOINS = """
            FROM relation_rules rr
            JOIN relations r ON rr.relation = r.id
            JOIN components c_from ON rr.from_component = c_from.id
            JOIN components c_to ON rr.to_component = c_to.id
    """

    const val WHERE_FILTERS = """
              (:relationId IS NULL OR rr.relation = :relationId)
              AND (:ownerId IS NULL OR rr.owner = :ownerId)
              AND (
                :notationId IS NULL OR (
                  r.notation = :notationId AND
                  c_from.notation = :notationId AND
                  c_to.notation = :notationId
                )
              )
    """

    const val SELECT_ENTITY = "SELECT rr.*"

    const val SELECT_PROJECTED = """
            SELECT
                rr.id AS id,
                rr.relation AS relationId,
                rr.from_component AS fromComponentId,
                rr.to_component AS toComponentId,
                rr.owner AS ownerId,
                rr.attrs AS attrs,
                rr.created_at AS createdAt,
                rr.updated_at AS updatedAt
    """

    const val SELECT_PROJECTED_LIGHT = """
            SELECT
                rr.id AS id,
                rr.relation AS relationId,
                rr.from_component AS fromComponentId,
                rr.to_component AS toComponentId,
                rr.owner AS ownerId,
                rr.created_at AS createdAt,
                rr.updated_at AS updatedAt
    """

    const val FIND_ENTITY = """
            $SELECT_ENTITY
            $FROM_JOINS
            WHERE $WHERE_FILTERS
            ORDER BY rr.id
        """

    const val COUNT = """
            SELECT COUNT(*)
            $FROM_JOINS
            WHERE $WHERE_FILTERS
        """

    const val FIND_PROJECTED = """
            $SELECT_PROJECTED
            $FROM_JOINS
            WHERE $WHERE_FILTERS
            ORDER BY rr.id
        """

    const val FIND_PROJECTED_LIGHT = """
            $SELECT_PROJECTED_LIGHT
            $FROM_JOINS
            WHERE $WHERE_FILTERS
            ORDER BY rr.id
        """
}
