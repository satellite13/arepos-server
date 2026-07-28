-- Soft-delete for node/link types and node shapes (admin trash + permanent delete).

ALTER TABLE public.node_types
    ADD COLUMN IF NOT EXISTS deleted BOOLEAN NOT NULL DEFAULT false;

ALTER TABLE public.link_types
    ADD COLUMN IF NOT EXISTS deleted BOOLEAN NOT NULL DEFAULT false;

ALTER TABLE public.node_shapes
    ADD COLUMN IF NOT EXISTS deleted BOOLEAN NOT NULL DEFAULT false;

COMMENT ON COLUMN public.node_types.deleted IS 'Soft-delete flag; excluded from catalog lists when true';
COMMENT ON COLUMN public.link_types.deleted IS 'Soft-delete flag; excluded from catalog lists when true';
COMMENT ON COLUMN public.node_shapes.deleted IS 'Soft-delete flag; excluded from catalog lists when true';

CREATE INDEX IF NOT EXISTS node_types_deleted_idx ON public.node_types (deleted) WHERE deleted = false;
CREATE INDEX IF NOT EXISTS link_types_deleted_idx ON public.link_types (deleted) WHERE deleted = false;
CREATE INDEX IF NOT EXISTS node_shapes_deleted_idx ON public.node_shapes (deleted) WHERE deleted = false;

-- Uniqueness only among active (non-deleted) rows, so names can be reused after soft-delete.
DROP INDEX IF EXISTS public.node_types_owner_name_lower_key;
CREATE UNIQUE INDEX node_types_owner_name_lower_undeleted_key
    ON public.node_types (owner, lower(name))
    WHERE deleted = false;

DROP INDEX IF EXISTS public.link_types_owner_name_lower_key;
CREATE UNIQUE INDEX link_types_owner_name_lower_undeleted_key
    ON public.link_types (owner, lower(name))
    WHERE deleted = false;
