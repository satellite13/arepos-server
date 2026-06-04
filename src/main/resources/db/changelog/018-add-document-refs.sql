-- Document refs: bindings of files (documents) to context (type, notation, component, model, node) and author
create table public.document_refs
(
    id           uuid      default gen_random_uuid() not null
        constraint document_refs_pk
            primary key,
    file_id      uuid                                not null
        constraint document_refs_files_id_fk
            references public.files
            ON DELETE CASCADE,
    created_by   uuid                                not null
        constraint document_refs_users_id_fk
            references public.users
            ON DELETE RESTRICT,
    created_at   timestamp default now()             not null,
    node_type_id uuid
        constraint document_refs_node_types_id_fk
            references public.node_types
            ON DELETE SET NULL,
    link_type_id uuid
        constraint document_refs_link_types_id_fk
            references public.link_types
            ON DELETE SET NULL,
    notation_id  uuid
        constraint document_refs_notations_id_fk
            references public.notations
            ON DELETE SET NULL,
    component_id uuid
        constraint document_refs_components_id_fk
            references public.components
            ON DELETE SET NULL,
    model_id     uuid
        constraint document_refs_models_id_fk
            references public.models
            ON DELETE SET NULL,
    node_id      uuid
        constraint document_refs_nodes_id_fk
            references public.nodes
            ON DELETE SET NULL,
    constraint document_refs_context_check check (
        node_type_id is not null or
        link_type_id is not null or
        notation_id is not null or
        component_id is not null or
        model_id is not null or
        node_id is not null
        )
);

comment on table public.document_refs is 'Привязки документов (файлов) к контексту: тип, нотация, компонент, модель, нода; для селекта документов в UI';
comment on column public.document_refs.file_id is 'Файл (документ)';
comment on column public.document_refs.created_by is 'Автор привязки (кто создал/привязал документ в этом контексте)';
comment on column public.document_refs.node_type_id is 'Тип узла (контекст типа)';
comment on column public.document_refs.link_type_id is 'Тип связи (контекст типа)';
comment on column public.document_refs.notation_id is 'Нотация';
comment on column public.document_refs.component_id is 'Компонент нотации';
comment on column public.document_refs.model_id is 'Модель';
comment on column public.document_refs.node_id is 'Узел в дереве модели';

create index document_refs_file_id_idx on public.document_refs (file_id);
create index document_refs_created_by_idx on public.document_refs (created_by);
create index document_refs_model_id_idx on public.document_refs (model_id);
create index document_refs_notation_id_idx on public.document_refs (notation_id);
create index document_refs_component_id_idx on public.document_refs (component_id);
create index document_refs_node_id_idx on public.document_refs (node_id);
create index document_refs_node_type_id_idx on public.document_refs (node_type_id);
create index document_refs_link_type_id_idx on public.document_refs (link_type_id);
create index document_refs_created_at_idx on public.document_refs (created_at);

create trigger document_refs_audit_trigger
    after insert or update or delete
    on public.document_refs
    for each row
execute function audit_trigger();
