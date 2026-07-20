-- Node/link type names are unique per owner (multi-tenant catalogs / ArchiMate import).
ALTER TABLE public.node_types
    DROP CONSTRAINT IF EXISTS node_types_name_key;

DROP INDEX IF EXISTS public.node_types_name_key;

CREATE UNIQUE INDEX IF NOT EXISTS node_types_owner_name_lower_key
    ON public.node_types (owner, lower(name));

COMMENT ON INDEX public.node_types_owner_name_lower_key IS
    'One node type name (case-insensitive) per owner.';

ALTER TABLE public.link_types
    DROP CONSTRAINT IF EXISTS link_types_name_key;

DROP INDEX IF EXISTS public.link_types_name_key;

CREATE UNIQUE INDEX IF NOT EXISTS link_types_owner_name_lower_key
    ON public.link_types (owner, lower(name));

COMMENT ON INDEX public.link_types_owner_name_lower_key IS
    'One link type name (case-insensitive) per owner.';
