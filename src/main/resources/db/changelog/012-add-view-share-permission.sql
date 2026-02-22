ALTER TABLE public.resource_shares
    DROP CONSTRAINT IF EXISTS resource_shares_permission_check;

ALTER TABLE public.resource_shares
    ADD CONSTRAINT resource_shares_permission_check
        CHECK (permission IN ('VIEW', 'EDIT'));
