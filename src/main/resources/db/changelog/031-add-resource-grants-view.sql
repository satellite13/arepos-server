CREATE OR REPLACE VIEW public.v_resource_grants AS
SELECT
    rs.resource_type,
    rs.resource_id,
    rs.permission,
    rs.grantee_user_id
FROM public.resource_shares rs;

COMMENT ON VIEW public.v_resource_grants IS
    'Нормализованное представление доступов на ресурсы (включая public grants через grantee_user_id IS NULL).';
