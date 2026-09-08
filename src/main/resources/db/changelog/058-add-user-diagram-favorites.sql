-- User diagram favorites: per-user bookmarks of diagrams

create table public.user_diagram_favorites
(
    id         uuid        default gen_random_uuid() not null
        constraint user_diagram_favorites_pk primary key,
    user_id    uuid                                  not null
        constraint user_diagram_favorites_user_fk
            references public.users
            on delete cascade,
    diagram_id uuid                                  not null
        constraint user_diagram_favorites_diagram_fk
            references public.diagrams
            on delete cascade,
    created_at timestamptz default now()             not null,
    constraint user_diagram_favorites_user_diagram_uq unique (user_id, diagram_id)
);

create index user_diagram_favorites_user_id_idx on public.user_diagram_favorites (user_id);
create index user_diagram_favorites_diagram_id_idx on public.user_diagram_favorites (diagram_id);

create trigger user_diagram_favorites_audit_trigger
    after insert or update or delete
    on public.user_diagram_favorites
    for each row
execute function audit_trigger();
