ALTER TABLE public.package_import_jobs
    ADD COLUMN IF NOT EXISTS overrides_json jsonb;
