DO $$
BEGIN
    CREATE EXTENSION IF NOT EXISTS pg_trgm;
EXCEPTION
    WHEN insufficient_privilege THEN
        RAISE NOTICE 'Skipping pg_trgm extension: insufficient privileges';
END
$$;

-- Owner and FK indexes for access-heavy queries.
CREATE INDEX IF NOT EXISTS notations_owner_idx ON public.notations (owner);
CREATE INDEX IF NOT EXISTS node_types_owner_idx ON public.node_types (owner);
CREATE INDEX IF NOT EXISTS link_types_owner_idx ON public.link_types (owner);

CREATE INDEX IF NOT EXISTS nodes_model_idx ON public.nodes (model);
CREATE INDEX IF NOT EXISTS nodes_owner_idx ON public.nodes (owner);
CREATE INDEX IF NOT EXISTS nodes_parent_node_idx ON public.nodes (parent_node);

CREATE INDEX IF NOT EXISTS links_model_idx ON public.links (model);
CREATE INDEX IF NOT EXISTS links_owner_idx ON public.links (owner);
CREATE INDEX IF NOT EXISTS links_source_idx ON public.links (source);
CREATE INDEX IF NOT EXISTS links_target_idx ON public.links (target);
CREATE INDEX IF NOT EXISTS links_link_type_idx ON public.links (link_type);

CREATE INDEX IF NOT EXISTS components_owner_idx ON public.components (owner);
CREATE INDEX IF NOT EXISTS components_node_type_idx ON public.components (node_type);

CREATE INDEX IF NOT EXISTS relations_owner_idx ON public.relations (owner);
CREATE INDEX IF NOT EXISTS relations_link_type_idx ON public.relations (link_type);

CREATE INDEX IF NOT EXISTS relation_rules_from_component_idx ON public.relation_rules (from_component);
CREATE INDEX IF NOT EXISTS relation_rules_to_component_idx ON public.relation_rules (to_component);

-- Recent activity access paths.
CREATE INDEX IF NOT EXISTS models_recent_undeleted_idx
    ON public.models (updated_at DESC, id)
    WHERE deleted = false;

CREATE INDEX IF NOT EXISTS notations_recent_undeleted_idx
    ON public.notations (updated_at DESC, id)
    WHERE deleted = false;

CREATE INDEX IF NOT EXISTS diagrams_recent_undeleted_idx
    ON public.diagrams (updated_at DESC, id)
    WHERE deleted = false;

-- Search acceleration for ILIKE by name.
-- Prefer trigram indexes when pg_trgm is available, otherwise fallback to lower(name) btree indexes.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'pg_trgm') THEN
        EXECUTE 'CREATE INDEX IF NOT EXISTS models_name_trgm_idx ON public.models USING gin (name gin_trgm_ops)';
        EXECUTE 'CREATE INDEX IF NOT EXISTS notations_name_trgm_idx ON public.notations USING gin (name gin_trgm_ops)';
        EXECUTE 'CREATE INDEX IF NOT EXISTS node_types_name_trgm_idx ON public.node_types USING gin (name gin_trgm_ops)';
        EXECUTE 'CREATE INDEX IF NOT EXISTS link_types_name_trgm_idx ON public.link_types USING gin (name gin_trgm_ops)';
        EXECUTE 'CREATE INDEX IF NOT EXISTS components_name_trgm_idx ON public.components USING gin (name gin_trgm_ops)';
        EXECUTE 'CREATE INDEX IF NOT EXISTS relations_name_trgm_idx ON public.relations USING gin (name gin_trgm_ops)';
        EXECUTE 'CREATE INDEX IF NOT EXISTS nodes_name_trgm_idx ON public.nodes USING gin (name gin_trgm_ops)';
        EXECUTE 'CREATE INDEX IF NOT EXISTS diagrams_name_trgm_idx ON public.diagrams USING gin (name gin_trgm_ops)';
    ELSE
        CREATE INDEX IF NOT EXISTS models_name_lower_idx ON public.models (lower(name));
        CREATE INDEX IF NOT EXISTS notations_name_lower_idx ON public.notations (lower(name));
        CREATE INDEX IF NOT EXISTS node_types_name_lower_idx ON public.node_types (lower(name));
        CREATE INDEX IF NOT EXISTS link_types_name_lower_idx ON public.link_types (lower(name));
        CREATE INDEX IF NOT EXISTS components_name_lower_idx ON public.components (lower(name));
        CREATE INDEX IF NOT EXISTS relations_name_lower_idx ON public.relations (lower(name));
        CREATE INDEX IF NOT EXISTS nodes_name_lower_idx ON public.nodes (lower(name));
        CREATE INDEX IF NOT EXISTS diagrams_name_lower_idx ON public.diagrams (lower(name));
    END IF;
END
$$;

-- jsonb filters and ordering helpers.
CREATE INDEX IF NOT EXISTS components_tags_gin_idx
    ON public.components USING gin ((COALESCE(attrs -> 'tags', '[]'::jsonb)));

CREATE INDEX IF NOT EXISTS relations_tags_gin_idx
    ON public.relations USING gin ((COALESCE(attrs -> 'tags', '[]'::jsonb)));

CREATE INDEX IF NOT EXISTS nodes_model_parent_treeorder_idx
    ON public.nodes (model, parent_node, (COALESCE((attrs ->> 'treeOrder')::int, 0)), id);
