DROP INDEX CONCURRENTLY IF EXISTS public.links_model_source_idx;
CREATE INDEX CONCURRENTLY links_model_source_idx
    ON public.links (model, source);

DROP INDEX CONCURRENTLY IF EXISTS public.links_model_target_idx;
CREATE INDEX CONCURRENTLY links_model_target_idx
    ON public.links (model, target);
