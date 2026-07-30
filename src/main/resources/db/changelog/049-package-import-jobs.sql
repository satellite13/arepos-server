CREATE TABLE public.package_import_jobs
(
    id          uuid PRIMARY KEY      DEFAULT gen_random_uuid(),
    owner_id    uuid         NOT NULL REFERENCES public.users (id) ON DELETE CASCADE,
    status      varchar(32)  NOT NULL,
    stage       varchar(64)  NOT NULL,
    progress    integer      NOT NULL DEFAULT 0,
    message     text,
    result_json jsonb,
    error_json  jsonb,
    temp_path   text,
    created_at  timestamptz  NOT NULL DEFAULT now(),
    updated_at  timestamptz  NOT NULL DEFAULT now(),
    finished_at timestamptz,
    CONSTRAINT package_import_jobs_progress_chk CHECK (progress >= 0 AND progress <= 100),
    CONSTRAINT package_import_jobs_status_chk CHECK (status IN ('QUEUED', 'RUNNING', 'SUCCEEDED', 'FAILED'))
);

CREATE INDEX package_import_jobs_owner_id_idx ON public.package_import_jobs (owner_id);
CREATE INDEX package_import_jobs_status_updated_at_idx ON public.package_import_jobs (status, updated_at);
CREATE INDEX package_import_jobs_finished_at_idx ON public.package_import_jobs (finished_at)
    WHERE finished_at IS NOT NULL;
