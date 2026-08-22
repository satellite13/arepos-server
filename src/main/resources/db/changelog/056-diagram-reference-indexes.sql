DROP INDEX CONCURRENTLY IF EXISTS public.diagrams_active_attrs_path_idx;
CREATE INDEX CONCURRENTLY diagrams_active_attrs_path_idx
    ON public.diagrams USING GIN (attrs jsonb_path_ops)
    WHERE deleted = false;

DROP INDEX CONCURRENTLY IF EXISTS public.diagrams_active_model_name_id_idx;
CREATE INDEX CONCURRENTLY diagrams_active_model_name_id_idx
    ON public.diagrams (model, name, id)
    WHERE deleted = false;
