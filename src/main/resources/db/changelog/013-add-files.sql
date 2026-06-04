-- Files table for md-editor-v3 image uploads (stored in MinIO/S3)
create table public.files
(
    id           uuid      default gen_random_uuid() not null
        constraint files_pk
            primary key,
    owner_id     uuid                                not null
        constraint files_users_id_fk
            references public.users
            ON DELETE CASCADE,
    filename     varchar(255)                        not null,
    content_type varchar(100)                        not null,
    size         bigint                              not null,
    object_key   varchar(512)                        not null,
    created_at   timestamp default now()             not null
);

comment on table public.files is 'Метаданные загруженных файлов (тело в MinIO/S3)';
comment on column public.files.object_key is 'Ключ объекта в S3/MinIO (bucket уже задан в конфиге)';

create index files_owner_id_idx on public.files (owner_id);
create index files_created_at_idx on public.files (created_at);

create trigger files_audit_trigger
    after insert or update or delete
    on public.files
    for each row
execute function audit_trigger();
