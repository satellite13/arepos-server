-- Close remaining FK index gaps for high-cardinality joins.
CREATE INDEX IF NOT EXISTS nodes_node_type_idx
    ON public.nodes (node_type);

CREATE INDEX IF NOT EXISTS diagram_preview_links_created_by_idx
    ON public.diagram_preview_links (created_by);

CREATE INDEX IF NOT EXISTS model_sync_outbox_model_id_idx
    ON public.model_sync_outbox (model_id);
