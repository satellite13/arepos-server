-- Public site: video tutorials + curated download assets

create table public.tutorial_videos
(
    id            uuid         default gen_random_uuid() not null
        constraint tutorial_videos_pk primary key,
    title         varchar(200)                           not null,
    description   text                                   not null default '',
    provider      varchar(32)                            not null
        constraint tutorial_videos_provider_chk
            check (provider in ('youtube', 'rutube', 'vk')),
    external_id   varchar(128)                           not null,
    embed_url     varchar(512)                           not null,
    thumbnail_url varchar(512),
    sort_order    int          default 0                 not null,
    published     boolean      default true              not null,
    created_at    timestamptz  default now()             not null,
    updated_at    timestamptz  default now()             not null
);

create index tutorial_videos_sort_order_idx on public.tutorial_videos (sort_order);
create index tutorial_videos_published_idx on public.tutorial_videos (published);

create trigger tutorial_videos_audit_trigger
    after insert or update or delete
    on public.tutorial_videos
    for each row
execute function audit_trigger();

create table public.download_assets
(
    id             uuid         default gen_random_uuid() not null
        constraint download_assets_pk primary key,
    title          varchar(200)                           not null,
    description    text                                   not null default '',
    kind           varchar(32)                            not null
        constraint download_assets_kind_chk
            check (kind in ('notation_export', 'other')),
    file_id        uuid                                   not null
        constraint download_assets_file_fk
            references public.files
            on delete restrict,
    file_name      varchar(255)                           not null,
    content_type   varchar(100)                           not null,
    size_bytes     bigint                                 not null,
    version_label  varchar(64),
    sort_order     int          default 0                 not null,
    published      boolean      default true              not null,
    download_count bigint       default 0                 not null,
    created_at     timestamptz  default now()             not null,
    updated_at     timestamptz  default now()             not null
);

create index download_assets_published_idx on public.download_assets (published);
create index download_assets_sort_order_idx on public.download_assets (sort_order);
create index download_assets_file_id_idx on public.download_assets (file_id);

create trigger download_assets_audit_trigger
    after insert or update or delete
    on public.download_assets
    for each row
execute function audit_trigger();
