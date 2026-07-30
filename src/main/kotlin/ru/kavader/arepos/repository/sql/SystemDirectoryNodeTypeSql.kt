package ru.kavader.arepos.repository.sql

/**
 * SQL predicate for Directory types marked as system via attrs
 * (`system.hiddenTreeRootType` or legacy `system`+`kind`), same attrs rules as
 * [ru.kavader.arepos.service.SystemRootNodeTypeService.isProtectedSystemDirectory].
 * Alias must be `nt` (node_types).
 */
object SystemDirectoryNodeTypeSql {
    // Avoid `#>> '{...}'` — Spring Data treats `{...}` in @Query as a template token.
    // COALESCE keeps the predicate boolean (NULL = 'true' would drop non-system Directory from COUNT).
    const val IS_SYSTEM_DIRECTORY = """
              LOWER(nt.name) = 'directory'
              AND COALESCE(
                  (nt.attrs -> 'system' ->> 'hiddenTreeRootType') = 'true'
                  OR (
                      jsonb_typeof(nt.attrs -> 'system') = 'boolean'
                      AND (nt.attrs ->> 'system') = 'true'
                      AND LOWER(COALESCE(nt.attrs ->> 'kind', '')) = 'directory'
                  ),
                  false
              )
    """
}
