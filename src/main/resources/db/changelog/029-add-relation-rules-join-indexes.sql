CREATE INDEX IF NOT EXISTS relation_rules_relation_idx
    ON public.relation_rules (relation);

CREATE INDEX IF NOT EXISTS relations_notation_idx
    ON public.relations (notation);

CREATE INDEX IF NOT EXISTS components_notation_idx
    ON public.components (notation);
