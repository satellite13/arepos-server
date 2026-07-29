-- Validation scripts: shareable JS validation artifacts (browser sandbox execution)

create table public.validation_scripts
(
    id          uuid      default gen_random_uuid() not null
        constraint validation_scripts_pk
            primary key,
    name        varchar(255)                        not null,
    description text,
    source      text                                not null,
    owner       uuid                                not null
        constraint validation_scripts_users_id_fk
            references public.users
            on delete cascade,
    created_at  timestamp default now()             not null,
    updated_at  timestamp,
    attrs       jsonb
);

comment on table public.validation_scripts is 'Каталог пользовательских JS-скриптов валидации моделей';

create unique index validation_scripts_owner_name_uidx
    on public.validation_scripts (owner, lower(name));

create index validation_scripts_owner_idx on public.validation_scripts (owner);

create trigger validation_scripts_audit_trigger
    after insert or update or delete
    on public.validation_scripts
    for each row
execute function audit_trigger();

ALTER TABLE public.resource_shares
    DROP CONSTRAINT IF EXISTS resource_shares_resource_type_check;

ALTER TABLE public.resource_shares
    ADD CONSTRAINT resource_shares_resource_type_check
        CHECK (resource_type IN (
            'MODEL',
            'NOTATION',
            'NODE_TYPE',
            'LINK_TYPE',
            'NODE_SHAPE',
            'VALIDATION_SCRIPT'
        ));
