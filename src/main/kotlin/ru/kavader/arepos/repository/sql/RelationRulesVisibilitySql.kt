package ru.kavader.arepos.repository.sql

/**
 * ForUser ACL for RelationRules (`resource_shares`).
 * Intentionally separate from NotationVisibilitySql (`v_resource_grants` + deleted checks).
 */
object RelationRulesVisibilitySql {
    const val ACCESSIBLE = """
                r.notation IN (
                  SELECT n.id
                  FROM notations n
                  WHERE
                    n.owner = :currentUserId
                    OR EXISTS (
                      SELECT 1
                      FROM resource_shares rs
                      WHERE rs.resource_type = 'NOTATION'
                        AND rs.resource_id = n.id
                        AND rs.permission IN ('VIEW', 'EDIT')
                        AND (rs.grantee_user_id = :currentUserId OR rs.grantee_user_id IS NULL)
                    )
                )
                OR r.notation IN (
                  SELECT DISTINCT d.notation_id
                  FROM diagrams d
                  JOIN models m ON d.model = m.id
                  WHERE
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
                OR (
                  :diagramEditorModelId IS NOT NULL
                  AND :notationId IS NOT NULL
                  AND r.notation = :notationId
                  AND EXISTS (
                    SELECT 1
                    FROM models m
                    WHERE m.id = :diagramEditorModelId
                      AND m.deleted = false
                      AND (
                        m.owner = :currentUserId
                        OR EXISTS (
                          SELECT 1
                          FROM resource_shares rs
                          WHERE rs.resource_type = 'MODEL'
                            AND rs.resource_id = m.id
                            AND rs.permission = 'EDIT'
                            AND (rs.grantee_user_id = :currentUserId OR rs.grantee_user_id IS NULL)
                        )
                      )
                  )
                )
    """

    const val FIND_PROJECTED_FOR_USER = """
            ${RelationRulesFilterSql.SELECT_PROJECTED}
            ${RelationRulesFilterSql.FROM_JOINS}
            WHERE ${RelationRulesFilterSql.WHERE_FILTERS}
              AND (
$ACCESSIBLE
              )
            ORDER BY rr.id
        """

    const val FIND_PROJECTED_LIGHT_FOR_USER = """
            ${RelationRulesFilterSql.SELECT_PROJECTED_LIGHT}
            ${RelationRulesFilterSql.FROM_JOINS}
            WHERE ${RelationRulesFilterSql.WHERE_FILTERS}
              AND (
$ACCESSIBLE
              )
            ORDER BY rr.id
        """

    const val COUNT_FOR_USER = """
            SELECT COUNT(*)
            ${RelationRulesFilterSql.FROM_JOINS}
            WHERE ${RelationRulesFilterSql.WHERE_FILTERS}
              AND (
$ACCESSIBLE
              )
        """
}
