ALTER TABLE public.resource_shares
    DROP CONSTRAINT IF EXISTS resource_shares_resource_type_check;

ALTER TABLE public.resource_shares
    ADD CONSTRAINT resource_shares_resource_type_check
        CHECK (resource_type IN ('MODEL', 'NOTATION', 'NODE_TYPE', 'LINK_TYPE', 'NODE_SHAPE'));
