-- UI grants: diagram comments panel + profile API keys section (Phase 1 client gate).
-- Seed: architect, editor, reader. viewer has neither by default.

INSERT INTO public.role_feature_grants (role, grant_key) VALUES
    ('architect', 'model.comments'),
    ('editor', 'model.comments'),
    ('reader', 'model.comments'),
    ('architect', 'profile.apiKeys'),
    ('editor', 'profile.apiKeys'),
    ('reader', 'profile.apiKeys')
ON CONFLICT DO NOTHING;
