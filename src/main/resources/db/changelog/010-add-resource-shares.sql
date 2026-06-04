CREATE TABLE IF NOT EXISTS public.resource_shares
(
    id                 uuid      DEFAULT gen_random_uuid() NOT NULL
        CONSTRAINT resource_shares_pk
            PRIMARY KEY,
    resource_type      VARCHAR(40)                         NOT NULL,
    resource_id        uuid                                NOT NULL,
    grantee_user_id    uuid                                NOT NULL
        CONSTRAINT resource_shares_grantee_user_fk
            REFERENCES public.users (id)
            ON DELETE CASCADE,
    granted_by_user_id uuid                                NOT NULL
        CONSTRAINT resource_shares_granted_by_user_fk
            REFERENCES public.users (id)
            ON DELETE CASCADE,
    permission         VARCHAR(20)                         NOT NULL DEFAULT 'EDIT',
    created_at         timestamp DEFAULT now()             NOT NULL,
    updated_at         timestamp
);

ALTER TABLE public.resource_shares
    ADD CONSTRAINT resource_shares_permission_check
        CHECK (permission IN ('EDIT'));

ALTER TABLE public.resource_shares
    ADD CONSTRAINT resource_shares_resource_type_check
        CHECK (resource_type IN ('MODEL', 'NOTATION', 'NODE_TYPE', 'LINK_TYPE'));

ALTER TABLE public.resource_shares
    ADD CONSTRAINT resource_shares_unique_share
        UNIQUE (resource_type, resource_id, grantee_user_id, permission);

CREATE INDEX IF NOT EXISTS resource_shares_resource_lookup_idx
    ON public.resource_shares (resource_type, resource_id, grantee_user_id, permission);

CREATE INDEX IF NOT EXISTS resource_shares_grantee_idx
    ON public.resource_shares (grantee_user_id);

CREATE INDEX IF NOT EXISTS resource_shares_granted_by_idx
    ON public.resource_shares (granted_by_user_id);

DROP TRIGGER IF EXISTS resource_shares_audit_trigger ON public.resource_shares;
CREATE TRIGGER resource_shares_audit_trigger
    AFTER INSERT OR UPDATE OR DELETE
    ON public.resource_shares
    FOR EACH ROW
EXECUTE FUNCTION audit_trigger();

DROP TRIGGER IF EXISTS set_resource_shares_updated_at_timestamp ON public.resource_shares;
CREATE TRIGGER set_resource_shares_updated_at_timestamp
    BEFORE UPDATE
    ON public.resource_shares
    FOR EACH ROW
EXECUTE FUNCTION update_updated_at_column();
