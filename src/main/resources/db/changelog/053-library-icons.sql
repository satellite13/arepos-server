-- Instance-wide SVG icon library (admin-managed)

create table public.library_icons
(
    id            uuid      default gen_random_uuid() not null
        constraint library_icons_pk
            primary key,
    name          varchar(255)                        not null,
    svg           text                                not null,
    content_hash  varchar(64)                         not null,
    created_by    uuid
        constraint library_icons_users_id_fk
            references public.users
            on delete set null,
    created_at    timestamp default now()             not null,
    updated_at    timestamp
);

comment on table public.library_icons is 'Общая библиотека SVG-иконок инстанса (загружает только админ)';

create unique index library_icons_name_uidx
    on public.library_icons (lower(name));

create index library_icons_content_hash_idx
    on public.library_icons (content_hash);

create trigger library_icons_audit_trigger
    after insert or update or delete
    on public.library_icons
    for each row
    execute function audit_trigger();
