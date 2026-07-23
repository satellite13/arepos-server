package ru.kavader.arepos.repository.sql

/**
 * ACL predicate for Components / Relations list-for-user queries (`v_resource_grants`).
 * Keep separate from RelationRulesVisibilitySql (`resource_shares`).
 */
object NotationVisibilitySql {
    const val ACCESSIBLE_C = """
                EXISTS (
                    SELECT 1
                    FROM notations n
                    WHERE n.id = c.notation
                      AND n.deleted = false
                      AND (
                        n.owner = :currentUserId
                        OR EXISTS (
                            SELECT 1
                            FROM v_resource_grants rg
                            WHERE rg.resource_type = 'NOTATION'
                              AND rg.resource_id = n.id
                              AND rg.permission IN ('VIEW', 'EDIT')
                              AND (rg.grantee_user_id = :currentUserId OR rg.grantee_user_id IS NULL)
                        )
                      )
                )
                OR EXISTS (
                    SELECT 1
                    FROM diagrams d
                    JOIN models m ON m.id = d.model
                    WHERE d.deleted = false
                      AND m.deleted = false
                      AND d.notation_id = c.notation
                      AND (
                        m.owner = :currentUserId
                        OR EXISTS (
                            SELECT 1
                            FROM v_resource_grants rg
                            WHERE rg.resource_type = 'MODEL'
                              AND rg.resource_id = m.id
                              AND rg.permission IN ('VIEW', 'EDIT')
                              AND (rg.grantee_user_id = :currentUserId OR rg.grantee_user_id IS NULL)
                        )
                      )
                )
                OR (
                  :diagramEditorModelId IS NOT NULL
                  AND :notationId IS NOT NULL
                  AND c.notation = :notationId
                  AND EXISTS (
                    SELECT 1
                    FROM models m
                    WHERE m.id = :diagramEditorModelId
                      AND m.deleted = false
                      AND (
                        m.owner = :currentUserId
                        OR EXISTS (
                          SELECT 1
                          FROM v_resource_grants rg
                          WHERE rg.resource_type = 'MODEL'
                            AND rg.resource_id = m.id
                            AND rg.permission = 'EDIT'
                            AND (rg.grantee_user_id = :currentUserId OR rg.grantee_user_id IS NULL)
                        )
                      )
                  )
                )
    """

    const val ACCESSIBLE_R = """
                EXISTS (
                    SELECT 1
                    FROM notations n
                    WHERE n.id = r.notation
                      AND n.deleted = false
                      AND (
                        n.owner = :currentUserId
                        OR EXISTS (
                            SELECT 1
                            FROM v_resource_grants rg
                            WHERE rg.resource_type = 'NOTATION'
                              AND rg.resource_id = n.id
                              AND rg.permission IN ('VIEW', 'EDIT')
                              AND (rg.grantee_user_id = :currentUserId OR rg.grantee_user_id IS NULL)
                        )
                      )
                )
                OR EXISTS (
                    SELECT 1
                    FROM diagrams d
                    JOIN models m ON m.id = d.model
                    WHERE d.deleted = false
                      AND m.deleted = false
                      AND d.notation_id = r.notation
                      AND (
                        m.owner = :currentUserId
                        OR EXISTS (
                            SELECT 1
                            FROM v_resource_grants rg
                            WHERE rg.resource_type = 'MODEL'
                              AND rg.resource_id = m.id
                              AND rg.permission IN ('VIEW', 'EDIT')
                              AND (rg.grantee_user_id = :currentUserId OR rg.grantee_user_id IS NULL)
                        )
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
                          FROM v_resource_grants rg
                          WHERE rg.resource_type = 'MODEL'
                            AND rg.resource_id = m.id
                            AND rg.permission = 'EDIT'
                            AND (rg.grantee_user_id = :currentUserId OR rg.grantee_user_id IS NULL)
                        )
                      )
                  )
                )
    """

    const val COMPONENTS_FIND_ACCESSIBLE = """
            SELECT c.*
            FROM components c
            WHERE ${NotationBoundListSql.FILTERS_C}
              AND (
$ACCESSIBLE_C
              )
            ORDER BY c.name, c.id
        """

    const val COMPONENTS_COUNT_ACCESSIBLE = """
            SELECT COUNT(*)
            FROM components c
            WHERE ${NotationBoundListSql.FILTERS_C}
              AND (
$ACCESSIBLE_C
              )
        """

    const val RELATIONS_FIND_ACCESSIBLE = """
            SELECT r.*
            FROM relations r
            WHERE ${NotationBoundListSql.FILTERS_R}
              AND (
$ACCESSIBLE_R
              )
            ORDER BY r.name, r.id
        """

    const val RELATIONS_COUNT_ACCESSIBLE = """
            SELECT COUNT(*)
            FROM relations r
            WHERE ${NotationBoundListSql.FILTERS_R}
              AND (
$ACCESSIBLE_R
              )
        """
}
