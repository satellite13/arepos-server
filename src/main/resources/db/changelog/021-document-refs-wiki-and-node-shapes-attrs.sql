-- document_refs: add diagram_id, relation_id, node_shape_id for wiki and full context
alter table public.document_refs
    add column diagram_id uuid
        constraint document_refs_diagrams_id_fk references public.diagrams ON DELETE SET NULL;

alter table public.document_refs
    add column relation_id uuid
        constraint document_refs_relations_id_fk references public.relations ON DELETE SET NULL;

alter table public.document_refs
    add column node_shape_id uuid
        constraint document_refs_node_shapes_id_fk references public.node_shapes ON DELETE SET NULL;

alter table public.document_refs
    drop constraint document_refs_context_check;

alter table public.document_refs
    add constraint document_refs_context_check check (
        node_type_id is not null or
        link_type_id is not null or
        notation_id is not null or
        component_id is not null or
        model_id is not null or
        node_id is not null or
        diagram_id is not null or
        relation_id is not null or
        node_shape_id is not null
        );

comment on column public.document_refs.diagram_id is 'Диаграмма (контекст модели)';
comment on column public.document_refs.relation_id is 'Отношение нотации';
comment on column public.document_refs.node_shape_id is 'Форма узла';

create index document_refs_diagram_id_idx on public.document_refs (diagram_id);
create index document_refs_relation_id_idx on public.document_refs (relation_id);
create index document_refs_node_shape_id_idx on public.document_refs (node_shape_id);

-- node_shapes: add attrs for documentFileId and other optional data
alter table public.node_shapes
    add column attrs jsonb;

comment on column public.node_shapes.attrs is 'Расширенные атрибуты (например documentFileId для документации)';
