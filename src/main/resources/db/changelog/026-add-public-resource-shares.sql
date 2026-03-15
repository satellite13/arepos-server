ALTER TABLE public.resource_shares
    ALTER COLUMN grantee_user_id DROP NOT NULL;

ALTER TABLE public.resource_shares
    DROP CONSTRAINT IF EXISTS resource_shares_unique_share;

CREATE UNIQUE INDEX IF NOT EXISTS resource_shares_unique_share_user_idx
    ON public.resource_shares (resource_type, resource_id, grantee_user_id, permission)
    WHERE grantee_user_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS resource_shares_unique_share_public_idx
    ON public.resource_shares (resource_type, resource_id, permission)
    WHERE grantee_user_id IS NULL;
