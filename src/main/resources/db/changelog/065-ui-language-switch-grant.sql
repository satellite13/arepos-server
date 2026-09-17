-- Add UI language switch grant (RU default when missing).
-- Seed for architect + editor; reader/viewer stay without it (admin = full catalog in code).

INSERT INTO public.role_feature_grants (role, grant_key) VALUES
    ('architect', 'ui.languageSwitch'),
    ('editor', 'ui.languageSwitch')
ON CONFLICT DO NOTHING;
