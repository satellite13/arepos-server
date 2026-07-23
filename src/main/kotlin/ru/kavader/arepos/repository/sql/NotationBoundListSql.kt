package ru.kavader.arepos.repository.sql

/**
 * Shared admin-list filter predicates for notation-bound Components / Relations.
 * Aliases must match the consuming query (`c` or `r`).
 */
object NotationBoundListSql {
    const val FILTERS_C = """
              (:notationId IS NULL OR c.notation = :notationId)
              AND (:ownerId IS NULL OR c.owner = :ownerId)
              AND (:name IS NULL OR c.name ILIKE CONCAT('%', :name, '%'))
              AND (:tagsJson IS NULL OR COALESCE(c.attrs -> 'tags', '[]'::jsonb) @> CAST(:tagsJson AS jsonb))
    """

    const val FILTERS_R = """
              (:notationId IS NULL OR r.notation = :notationId)
              AND (:ownerId IS NULL OR r.owner = :ownerId)
              AND (:name IS NULL OR r.name ILIKE CONCAT('%', :name, '%'))
              AND (:tagsJson IS NULL OR COALESCE(r.attrs -> 'tags', '[]'::jsonb) @> CAST(:tagsJson AS jsonb))
    """

    const val COMPONENTS_FIND_BY_FILTERS = """
            SELECT *
            FROM components c
            WHERE $FILTERS_C
            ORDER BY c.name, c.id
        """

    const val COMPONENTS_COUNT_BY_FILTERS = """
            SELECT COUNT(*)
            FROM components c
            WHERE $FILTERS_C
        """

    const val RELATIONS_FIND_BY_FILTERS = """
            SELECT *
            FROM relations r
            WHERE $FILTERS_R
            ORDER BY r.name, r.id
        """

    const val RELATIONS_COUNT_BY_FILTERS = """
            SELECT COUNT(*)
            FROM relations r
            WHERE $FILTERS_R
        """
}
