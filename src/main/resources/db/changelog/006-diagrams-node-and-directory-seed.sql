alter table public.diagrams
    add column if not exists node_id uuid null;

do
$$
    begin
        if not exists (select 1
                       from pg_constraint
                       where conname = 'diagrams_nodes_id_fk') then
            alter table public.diagrams
                add constraint diagrams_nodes_id_fk
                    foreign key (node_id)
                        references public.nodes
                        on delete set null;
        end if;
    end
$$;

create index if not exists diagrams_node_id_idx on public.diagrams (node_id);

comment on column public.diagrams.node_id is 'Узел-контейнер диаграммы (nodes.id)';

insert into public.users (email, attrs)
select 'system@arepos.local',
       '{
         "system": true,
         "seed": "liquibase"
       }'::jsonb
where not exists (select 1
                  from public.users
                  where email = 'system@arepos.local');

insert into public.node_types (name, attrs, owner)
select 'Directory',
       '{
         "system": true,
         "kind": "directory"
       }'::jsonb,
       u.id
from public.users u
where u.email = 'system@arepos.local'
  and not exists (select 1
                  from public.node_types nt
                  where lower(nt.name) = 'directory');
