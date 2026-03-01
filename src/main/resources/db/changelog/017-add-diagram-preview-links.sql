-- Share links for diagram SVG preview (public access by token)
create table public.diagram_preview_links
(
    id           uuid      default gen_random_uuid() not null
        constraint diagram_preview_links_pk primary key,
    token        uuid                                not null
        constraint diagram_preview_links_token_key unique,
    diagram_id   uuid
        constraint diagram_preview_links_diagram_id_fk
            references public.diagrams (id) on delete cascade,
    model_id     uuid
        constraint diagram_preview_links_model_id_fk
            references public.models (id) on delete cascade,
    diagram_name varchar(255),
    created_at   timestamptz default now()          not null,
    created_by   uuid
        constraint diagram_preview_links_created_by_fk
            references public.users (id) on delete set null,
    constraint diagram_preview_links_target_check check (
        (diagram_id is not null and model_id is null and diagram_name is null)
        or (diagram_id is null and model_id is not null and diagram_name is not null)
    )
);

comment on table public.diagram_preview_links is 'Публичные ссылки на превью диаграммы (SVG по токену)';
comment on column public.diagram_preview_links.token is 'Токен в URL (svg/public/{token})';
comment on column public.diagram_preview_links.diagram_id is 'Конкретная версия диаграммы';
comment on column public.diagram_preview_links.model_id is 'Для «всегда последняя»: модель';
comment on column public.diagram_preview_links.diagram_name is 'Для «всегда последняя»: имя диаграммы';

create index diagram_preview_links_token_idx on public.diagram_preview_links (token);
create index diagram_preview_links_diagram_id_idx on public.diagram_preview_links (diagram_id);
create index diagram_preview_links_model_name_idx on public.diagram_preview_links (model_id, diagram_name);

create trigger diagram_preview_links_audit_trigger
    after insert or update or delete
    on public.diagram_preview_links
    for each row
execute function audit_trigger();
