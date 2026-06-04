-- File versions table for tracking markdown file history (MinIO/S3 versioning)
create table public.file_versions
(
    id             uuid      default gen_random_uuid() not null
        constraint file_versions_pk
            primary key,
    file_id        uuid                                not null
        constraint file_versions_files_id_fk
            references public.files
            ON DELETE CASCADE,
    version_id     varchar(255)                        not null, -- MinIO/S3 version ID
    version_number integer                             not null, -- sequential version number (1, 2, 3...)
    created_at     timestamp default now()             not null,
    created_by     uuid                                not null
        constraint file_versions_users_id_fk
            references public.users
            ON DELETE RESTRICT,
    size           bigint                              not null,
    unique (file_id, version_number)
);

comment on table public.file_versions is 'История версий файлов (версии в MinIO/S3)';
comment on column public.file_versions.version_id is 'ID версии в MinIO/S3 storage';
comment on column public.file_versions.version_number is 'Порядковый номер версии (1, 2, 3...)';
comment on column public.file_versions.created_by is 'Пользователь, создавший эту версию';

create index file_versions_file_id_idx on public.file_versions (file_id);
create index file_versions_created_at_idx on public.file_versions (created_at);
create index file_versions_created_by_idx on public.file_versions (created_by);

create trigger file_versions_audit_trigger
    after insert or update or delete
    on public.file_versions
    for each row
execute function audit_trigger();
