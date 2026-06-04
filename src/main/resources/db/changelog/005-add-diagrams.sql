-- Создание таблицы diagrams для хранения диаграмм в рамках модели и нотации
create table public.diagrams
(
    id          uuid      default gen_random_uuid() not null
        constraint diagrams_pk
            primary key,
    name        varchar(256)                        not null,
    created_at  timestamp default now()             not null,
    updated_at  timestamp,
    attrs       jsonb,
    version     version_type                        not null,
    owner       uuid                                not null
        constraint diagrams_users_id_fk
            references public.users
            on delete restrict,
    deleted     boolean   default false             not null,
    model       uuid                                not null
        constraint diagrams_models_id_fk
            references public.models
            on delete cascade,
    notation_id uuid                                not null
        constraint diagrams_notations_id_fk
            references public.notations
            on delete restrict,
    constraint diagrams_model_name_version_key
        unique (model, name, version)
);

comment on table public.diagrams is 'Диаграммы модели, созданные в рамках выбранной нотации';
comment on column public.diagrams.id is 'Уникальный идентификатор диаграммы';
comment on column public.diagrams.name is 'Название диаграммы';
comment on column public.diagrams.created_at is 'Дата и время создания диаграммы';
comment on column public.diagrams.updated_at is 'Дата и время последнего обновления диаграммы';
comment on column public.diagrams.attrs is 'Дополнительные атрибуты диаграммы в формате JSON';
comment on column public.diagrams.version is 'Версия диаграммы в формате semver';
comment on column public.diagrams.owner is 'Владелец диаграммы (users.id)';
comment on column public.diagrams.deleted is 'Флаг мягкого удаления диаграммы';
comment on column public.diagrams.model is 'Модель, в рамках которой создана диаграмма (models.id)';
comment on column public.diagrams.notation_id is 'Нотация диаграммы (notations.id)';

create index if not exists diagrams_deleted_idx on public.diagrams (deleted) where deleted = false;
create index if not exists diagrams_owner_idx on public.diagrams (owner);
create index if not exists diagrams_model_idx on public.diagrams (model);
create index if not exists diagrams_notation_id_idx on public.diagrams (notation_id);

create trigger set_diagrams_updated_at_timestamp
    before update
    on public.diagrams
    for each row
execute function update_updated_at_column();

create trigger set_diagrams_updated_at_on_insert
    before insert
    on public.diagrams
    for each row
execute function set_updated_at_on_insert();

create trigger diagrams_audit_trigger
    after insert or update or delete
    on public.diagrams
    for each row
execute function audit_trigger();
