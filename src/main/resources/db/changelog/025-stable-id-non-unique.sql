-- stable_id должен повторяться между версиями одной и той же модели при копировании:
-- один и тот же логический узел/связь в разных версиях имеет одинаковый stable_id.
ALTER TABLE public.nodes
    DROP CONSTRAINT IF EXISTS nodes_stable_id_key;

ALTER TABLE public.links
    DROP CONSTRAINT IF EXISTS links_stable_id_key;

CREATE INDEX IF NOT EXISTS idx_nodes_stable_id
    ON public.nodes (stable_id);

CREATE INDEX IF NOT EXISTS idx_links_stable_id
    ON public.links (stable_id);
