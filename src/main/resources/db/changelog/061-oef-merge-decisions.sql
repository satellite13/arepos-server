-- Persisted duplicate-merge decisions for OEF imports.
-- Keyed by the OEF entity identifier (from the exchange XML) so a repeated import
-- auto-merges an entity into the node chosen during a previous validation run
-- instead of creating a duplicate again.
CREATE TABLE IF NOT EXISTS public.oef_merge_decisions (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    model uuid NOT NULL REFERENCES public.models(id) ON DELETE CASCADE,
    oef_entity_id text NOT NULL,
    target_node uuid NOT NULL REFERENCES public.nodes(id) ON DELETE CASCADE,
    signature_type text,
    signature_name text,
    created_by uuid REFERENCES public.users(id) ON DELETE SET NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz,
    CONSTRAINT oef_merge_decisions_model_entity_uq UNIQUE (model, oef_entity_id)
);

CREATE INDEX IF NOT EXISTS oef_merge_decisions_model_idx
    ON public.oef_merge_decisions (model);
CREATE INDEX IF NOT EXISTS oef_merge_decisions_target_node_idx
    ON public.oef_merge_decisions (target_node);

COMMENT ON TABLE public.oef_merge_decisions IS
    'Сохранённые решения слияния дубликатов OEF-импорта: oef_entity_id → целевой узел';
