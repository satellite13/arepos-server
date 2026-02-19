create or replace function check_links_same_model() returns trigger
    language plpgsql
as
$$
declare
    model_count          integer;
    node_exists_count    integer;
    common_model         uuid;
    required_nodes_count integer;
begin
    required_nodes_count := case
                                when new.source = new.target then 1
                                else 2
        end;

    select count(distinct n.model),
           count(n.id),
           (select n2.model from public.nodes n2 where n2.id in (new.source, new.target) limit 1)
    into
        model_count,
        node_exists_count,
        common_model
    from public.nodes n
    where n.id in (new.source, new.target);

    if node_exists_count < required_nodes_count then
        raise exception 'Исходный (%) или целевой (%) узел не существует.', new.source, new.target;
    end if;

    if model_count != 1 then
        raise exception 'Исходный и целевой узлы должны принадлежать одной и той же модели (найдено % уникальных моделей).', model_count;
    end if;

    if new.model is distinct from common_model then
        raise exception 'Модель связи (%) должна совпадать с моделью исходного/целевого узлов (%).', new.model, common_model;
    end if;

    return new;
end;
$$;
