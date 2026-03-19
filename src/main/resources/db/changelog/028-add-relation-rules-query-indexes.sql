CREATE INDEX IF NOT EXISTS relation_rules_owner_idx
    ON public.relation_rules (owner);

CREATE INDEX IF NOT EXISTS models_owner_idx
    ON public.models (owner);
