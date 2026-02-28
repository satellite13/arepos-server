-- Node shapes: global catalog of custom node outline definitions (normalized 0-1 coordinates)
create table public.node_shapes
(
    id         uuid      default gen_random_uuid() not null
        constraint node_shapes_pk
            primary key,
    name       varchar(255)                        not null,
    owner      uuid                                 not null
        constraint node_shapes_users_id_fk
            references public.users
            on delete cascade,
    outline    jsonb,
    created_at timestamp default now()             not null,
    updated_at timestamp
);

comment on table public.node_shapes is 'Глобальный каталог кастомных форм узлов (контур в нормализованных координатах 0-1)';

create index node_shapes_owner_idx on public.node_shapes (owner);

create trigger node_shapes_audit_trigger
    after insert or update or delete
    on public.node_shapes
    for each row
execute function audit_trigger();
