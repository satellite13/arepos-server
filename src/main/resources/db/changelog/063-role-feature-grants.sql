-- Migrate legacy role names to the new product role set.
UPDATE public.users SET role = 'admin' WHERE role = 'ADMIN';
UPDATE public.users SET role = 'editor' WHERE role = 'EDITOR';
UPDATE public.users SET role = 'reader' WHERE role IN ('USER', 'user');

-- Default feature grants per role (admin is always full catalog in code; not seeded).
CREATE TABLE IF NOT EXISTS public.role_feature_grants (
    role varchar(32) NOT NULL,
    grant_key varchar(128) NOT NULL,
    PRIMARY KEY (role, grant_key)
);

-- Per-user allow-only overrides on top of role defaults.
CREATE TABLE IF NOT EXISTS public.user_feature_grant_allows (
    user_id uuid NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
    grant_key varchar(128) NOT NULL,
    PRIMARY KEY (user_id, grant_key)
);

COMMENT ON TABLE public.role_feature_grants IS
    'Дефолтные feature-гранты по роли (кроме admin — полный каталог в коде)';
COMMENT ON TABLE public.user_feature_grant_allows IS
    'Allow-only override feature-грантов пользователя поверх роли';

-- architect: all catalog keys
INSERT INTO public.role_feature_grants (role, grant_key) VALUES
    ('architect', 'model.create'),
    ('architect', 'model.importPackage'),
    ('architect', 'model.export'),
    ('architect', 'model.exportDiagramImage'),
    ('architect', 'notation.nav'),
    ('architect', 'notation.create'),
    ('architect', 'notation.import'),
    ('architect', 'type.nav'),
    ('architect', 'type.create'),
    ('architect', 'shape.nav'),
    ('architect', 'shape.create'),
    ('architect', 'validationScript.nav'),
    ('architect', 'validationScript.create'),
    ('architect', 'model.tree.createRoot'),
    ('architect', 'model.tree.createChildFolder'),
    ('architect', 'model.tree.renameDeleteMoveFolder'),
    ('architect', 'model.compareVersions'),
    ('architect', 'model.relationMatrix'),
    ('architect', 'model.wiki.create'),
    ('architect', 'model.importOef'),
    ('architect', 'model.runValidationScripts'),
    ('architect', 'model.inspectJson'),
    ('architect', 'model.createBaseline');

-- editor: catalog minus create/import for notation/type/shape/script and inspectJson
INSERT INTO public.role_feature_grants (role, grant_key) VALUES
    ('editor', 'model.create'),
    ('editor', 'model.importPackage'),
    ('editor', 'model.export'),
    ('editor', 'model.exportDiagramImage'),
    ('editor', 'notation.nav'),
    ('editor', 'type.nav'),
    ('editor', 'shape.nav'),
    ('editor', 'validationScript.nav'),
    ('editor', 'model.tree.createRoot'),
    ('editor', 'model.tree.createChildFolder'),
    ('editor', 'model.tree.renameDeleteMoveFolder'),
    ('editor', 'model.compareVersions'),
    ('editor', 'model.relationMatrix'),
    ('editor', 'model.wiki.create'),
    ('editor', 'model.importOef'),
    ('editor', 'model.runValidationScripts'),
    ('editor', 'model.createBaseline');

-- reader
INSERT INTO public.role_feature_grants (role, grant_key) VALUES
    ('reader', 'model.export'),
    ('reader', 'model.exportDiagramImage'),
    ('reader', 'notation.nav'),
    ('reader', 'type.nav'),
    ('reader', 'shape.nav'),
    ('reader', 'validationScript.nav'),
    ('reader', 'model.compareVersions'),
    ('reader', 'model.relationMatrix'),
    ('reader', 'model.runValidationScripts');

-- viewer
INSERT INTO public.role_feature_grants (role, grant_key) VALUES
    ('viewer', 'model.exportDiagramImage'),
    ('viewer', 'model.compareVersions'),
    ('viewer', 'model.relationMatrix');
