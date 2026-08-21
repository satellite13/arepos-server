CREATE INDEX CONCURRENTLY IF NOT EXISTS links_model_source_idx
    ON public.links (model, source);

CREATE INDEX CONCURRENTLY IF NOT EXISTS links_model_target_idx
    ON public.links (model, target);
