DROP TYPE IF EXISTS version_type;
CREATE DOMAIN version_type AS TEXT CHECK (VALUE ~ '^\d+\.\d+\.\d+(-[a-zA-Z0-9]+(\.[a-zA-Z0-9]+)*)?$');
DROP TYPE IF EXISTS email_type;
CREATE DOMAIN email_type AS TEXT CHECK (VALUE ~ '^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$');
DROP TYPE IF EXISTS url_type;
CREATE DOMAIN url_type AS TEXT CHECK (VALUE ~ '^https?://[^\s/$.?#].[^\s]*$');
DROP TYPE IF EXISTS phone_type;
CREATE DOMAIN phone_type AS TEXT CHECK (VALUE ~ '^\+?[1-9]\d{1,14}$');
DROP TYPE IF EXISTS date_type;
CREATE DOMAIN date_type AS DATE CHECK (VALUE >= '1900-01-01' AND VALUE <= '2100-12-31');
DROP TYPE IF EXISTS time_type;
CREATE DOMAIN time_type AS TIME CHECK (VALUE >= '00:00:00' AND VALUE <= '23:59:59');

create table public.users
(
    id         uuid      default gen_random_uuid() not null
        constraint users_pk
            primary key,
    email      email_type                          not null
        unique,
    attrs      jsonb,
    created_at timestamp default now()             not null,
    updated_at timestamp -- Улучшение: Добавлено поле времени обновления
);

comment on table public.users is 'Пользователи системы';
comment on column public.users.id is 'Уникальный идентификатор пользователя';
comment on column public.users.email is 'Электронная почта пользователя';
comment on column public.users.attrs is 'Дополнительные атрибуты пользователя в формате JSON';
comment on column public.users.created_at is 'Дата и время создания записи о пользователе';
comment on column public.users.updated_at is 'Дата и время последнего обновления записи о пользователе';
create index users_attrs_idx on public.users using gin (attrs);

create table public.audit_log
(
    id         uuid      default gen_random_uuid() not null
        constraint audit_log_pk
            primary key,
    table_name varchar(100)                        not null,
    operation  varchar(10)                         not null, -- INSERT, UPDATE, DELETE
    row_id     uuid                                not null,
    old_values jsonb,
    new_values jsonb,
    changed_by uuid
        constraint audit_log_users_id_fk
            references public.users
            ON DELETE SET NULL,                              -- Улучшение: если пользователь удален, поле обнуляется
    changed_at timestamp default now()             not null
);

comment on table public.audit_log is 'Журнал аудита для отслеживания изменений во всех таблицах';
comment on column public.audit_log.id is 'Уникальный идентификатор записи в журнале аудита';
comment on column public.audit_log.table_name is 'Название таблицы, в которой произошли изменения';
comment on column public.audit_log.operation is 'Тип операции (INSERT, UPDATE, DELETE)';
comment on column public.audit_log.row_id is 'Идентификатор строки, в которой произошли изменения';
comment on column public.audit_log.old_values is 'JSON-представление старых значений';
comment on column public.audit_log.new_values is 'JSON-представление новых значений';
comment on column public.audit_log.changed_by is 'Пользователь, внесший изменения (ссылка на users.id)';
comment on column public.audit_log.changed_at is 'Временная метка момента внесения изменений';
comment on constraint audit_log_users_id_fk on public.audit_log is 'Внешний ключ, связывающий запись в журнале аудита с пользователем, внесшим изменения';

create index audit_log_table_name_idx on public.audit_log (table_name);
create index audit_log_operation_idx on public.audit_log (operation);
create index audit_log_row_id_idx on public.audit_log (row_id);
create index audit_log_changed_by_idx on public.audit_log (changed_by);
create index audit_log_changed_at_idx on public.audit_log (changed_at);
CREATE INDEX audit_log_entity_history_idx ON public.audit_log (table_name, row_id, changed_at DESC);

-- Функция для автоматического обновления поля updated_at
create or replace function update_updated_at_column()
    returns trigger as
$$
begin
    NEW.updated_at = now();
    RETURN NEW;
end;
$$ language plpgsql;

-- Универсальная функция аудита (модифицирована для чтения user_id из контекста сессии)
create or replace function audit_trigger()
    returns trigger as
$$
declare
    audit_row public.audit_log;
    user_id   uuid;
begin
    -- Улучшение: Получение user_id из контекста сессии
    begin
        -- Считываем переменную 'app.current_user_id'.
        -- 'true' означает, что не будет выброшено исключение, если переменная не установлена (вернет NULL)
        user_id := current_setting('app.current_user_id', true)::uuid;
    exception
        when invalid_text_representation or undefined_object then
            -- Ловим ошибки, если переменная не установлена или имеет неверный формат UUID
            user_id := null;
    end;

    -- Create audit record
    if (TG_OP = 'DELETE') then
        audit_row = ROW (
            gen_random_uuid(),
            TG_TABLE_NAME,
            TG_OP,
            OLD.id,
            row_to_json(OLD),
            null,
            user_id,
            now()
            );
    elsif (TG_OP = 'INSERT') then
        audit_row = ROW (
            gen_random_uuid(),
            TG_TABLE_NAME,
            TG_OP,
            NEW.id,
            null,
            row_to_json(NEW),
            user_id,
            now()
            );
    elsif (TG_OP = 'UPDATE') then
        audit_row = ROW (
            gen_random_uuid(),
            TG_TABLE_NAME,
            TG_OP,
            NEW.id,
            row_to_json(OLD),
            row_to_json(NEW),
            user_id,
            now()
            );
    end if;

    -- Insert audit record
    insert into public.audit_log
    values (audit_row.id, audit_row.table_name, audit_row.operation, audit_row.row_id, audit_row.old_values,
            audit_row.new_values, audit_row.changed_by, audit_row.changed_at);

    -- Return appropriate record
    if (TG_OP = 'DELETE') then
        return OLD;
    else
        return NEW;
    end if;
end;
$$ language plpgsql;

create table public.models
(
    id         uuid      default gen_random_uuid() not null
        constraint models_pk
            primary key,
    name       varchar(256)                        not null,
    created_at timestamp default now()             not null,
    updated_at timestamp,       -- Улучшение: Добавлено поле времени обновления
    attrs      jsonb,
    version    version_type                        not null,
    owner      uuid                                not null
        constraint models_users_id_fk
            references public.users
            ON DELETE RESTRICT, -- Улучшение: Запрет удаления пользователя, пока есть его модели
    unique (name, version)
);

create table public.notations
(
    id         uuid      default gen_random_uuid() not null
        constraint notations_pk
            primary key,
    owner      uuid                                not null
        constraint notations_users_id_fk
            references public.users
            ON DELETE RESTRICT, -- Улучшение: Запрет удаления пользователя, пока есть его нотации
    attrs      jsonb,
    created_at timestamp default now()             not null,
    updated_at timestamp,       -- Улучшение: Добавлено поле времени обновления
    name       varchar(100)                        not null,
    version    version_type                        not null,
    unique (name, version)
);

create table public.node_types
(
    id         uuid      default gen_random_uuid() not null
        constraint node_types_pk
            primary key,
    created_at timestamp default now()             not null,
    updated_at timestamp,      -- Улучшение: Добавлено поле времени обновления
    name       varchar(100)                        not null
        unique,
    attrs      jsonb,
    owner      uuid                                not null
        constraint node_types_owner_id_fk
            references public.users
            ON DELETE RESTRICT -- Улучшение: Запрет удаления пользователя, пока есть его типы узлов
);

create table public.nodes
(
    id          uuid      default gen_random_uuid() not null
        constraint nodes_pk
            primary key,
    name        varchar(256)                        not null,
    created_at  timestamp default now()             not null,
    updated_at  timestamp,     -- Улучшение: Добавлено поле времени обновления
    attrs       jsonb,
    parent_node uuid
        constraint nodes_nodes_id_fk
            references public.nodes
            ON DELETE CASCADE, -- Улучшение: Удаление родителя каскадно удаляет детей
    model       uuid                                not null
        constraint nodes_models_id_fk
            references public.models
            ON DELETE CASCADE, -- Улучшение: Удаление модели каскадно удаляет узлы
    owner       uuid                                not null
        constraint nodes_users_id_fk
            references public.users
            ON DELETE RESTRICT,
    node_type   uuid                                not null
        constraint nodes_node_types_id_fk
            references public.node_types
            ON DELETE RESTRICT,
    constraint nodes_no_self_parent_check
        check (parent_node is null or parent_node != id)
);

create table public.components
(
    id         uuid      default gen_random_uuid() not null
        constraint components_pk
            primary key,
    name       varchar(100)                        not null,
    attrs      jsonb,
    created_at timestamp default now()             not null,
    updated_at timestamp,      -- Улучшение: Добавлено поле времени обновления
    version    version_type                        not null,
    notation   uuid                                not null
        constraint components_notations_id_fk
            references public.notations
            ON DELETE CASCADE, -- Улучшение: Удаление нотации каскадно удаляет компоненты
    owner      uuid                                not null
        constraint components_users_id_fk
            references public.users
            ON DELETE RESTRICT,
    node_type  uuid                                not null
        constraint components_node_types_id_fk
            references public.node_types
            ON DELETE RESTRICT,
    unique (notation, name, version)
);

create table public.link_types
(
    id         uuid      default gen_random_uuid() not null
        constraint link_types_pk
            primary key,
    created_at timestamp default now()             not null,
    updated_at timestamp, -- Улучшение: Добавлено поле времени обновления
    name       varchar(100)                        not null
        unique,
    attrs      jsonb,
    owner      uuid                                not null
        constraint link_types_users_id_fk
            references public.users
            ON DELETE RESTRICT
);

create table public.links
(
    id         uuid      default gen_random_uuid() not null
        constraint links_pk
            primary key,
    source     uuid                                not null
        constraint links_source_nodes_id_fk
            references public.nodes
            ON DELETE CASCADE, -- Улучшение: Удаление узла каскадно удаляет связи, исходящие из него
    target     uuid                                not null
        constraint links_target_nodes_id_fk
            references public.nodes
            ON DELETE CASCADE, -- Улучшение: Удаление узла каскадно удаляет связи, входящие в него
    attrs      jsonb,
    created_at timestamp default now()             not null,
    updated_at timestamp,      -- Улучшение: Добавлено поле времени обновления
    owner      uuid                                not null
        constraint links_users_id_fk
            references public.users
            ON DELETE RESTRICT,
    link_type  uuid                                not null
        constraint links_link_types_id_fk
            references public.link_types
            ON DELETE RESTRICT,
    model      uuid                                not null
        constraint links_models_id_fk
            references public.models
            ON DELETE CASCADE, -- Улучшение: Удаление модели каскадно удаляет связи
    constraint links_no_self_link_check
        check (source != target)
);

create table public.relations
(
    id         uuid      default gen_random_uuid() not null
        constraint relations_pk
            primary key,
    attrs      jsonb,
    created_at timestamp default now()             not null,
    updated_at timestamp,      -- Улучшение: Добавлено поле времени обновления
    version    version_type                        not null,
    owner      uuid                                not null
        constraint relations_users_id_fk
            references public.users
            ON DELETE RESTRICT,
    notation   uuid                                not null
        constraint relations_notations_id_fk
            references public.notations
            ON DELETE CASCADE, -- Улучшение: Удаление нотации каскадно удаляет отношения
    name       varchar(100)                        not null,
    link_type  uuid                                not null
        constraint relations_link_types_id_fk
            references public.link_types
            ON DELETE RESTRICT,
    unique (notation, name, version)
);

create table public.relation_rules
(
    id             uuid      default gen_random_uuid() not null
        constraint relation_rules_pk
            primary key,
    created_at     timestamp default now()             not null,
    updated_at     timestamp,  -- Улучшение: Добавлено поле времени обновления
    owner          uuid                                not null
        constraint relation_rules_users_id_fk
            references public.users
            ON DELETE RESTRICT,
    attrs          jsonb,
    relation       uuid                                not null
        constraint relation_rules_relations_id_fk
            references public.relations
            ON DELETE CASCADE, -- Улучшение: Удаление отношения каскадно удаляет его правила
    from_component uuid                                not null
        constraint relation_rules_from_components_id_fk
            references public.components
            ON DELETE CASCADE, -- Улучшение: Удаление компонента каскадно удаляет правила, где он источник
    to_component   uuid                                not null
        constraint relation_rules_to_components_id_fk
            references public.components
            ON DELETE CASCADE, -- Улучшение: Удаление компонента каскадно удаляет правила, где он цель
    constraint relation_rules_relation_from_to_key
        unique (relation, from_component, to_component)
);

create or replace function check_node_circular_reference() returns trigger
    language plpgsql
as
$$
DECLARE
    is_ancestor BOOLEAN;
BEGIN
    -- 1. Проверка: пропускаем, если нет родителя
    IF NEW.parent_node IS NULL THEN
        RETURN NEW;
    END IF;

    -- 2. Проверка: предотвращение рекурсивной ссылки (уже есть в CONSTRAINT, но тут для надежности)
    IF NEW.parent_node = NEW.id THEN
        RAISE EXCEPTION 'Circular reference detected: node % cannot be its own parent.', NEW.id;
    END IF;

    -- 3. Рекурсивный поиск: проверка, является ли новый узел предком своего родителя
    -- (то есть, проверка на циклы A -> B -> A)
    WITH RECURSIVE ancestor_path AS (
        -- Якорь (Anchor Member): начинаем с непосредственного родителя
        SELECT n.id AS ancestor_id,
               n.parent_node
        FROM public.nodes n
        WHERE n.id = NEW.parent_node

        UNION ALL

        -- Рекурсивный шаг (Recursive Member): вверх по иерархии
        SELECT n.id AS ancestor_id,
               n.parent_node
        FROM public.nodes n
                 JOIN
             ancestor_path a ON n.id = a.parent_node
        -- Ограничение рекурсии: не искать дальше, если parent_node не существует
        WHERE a.parent_node IS NOT NULL)
    -- Проверка: если NEW.id находится в списке предков (ancestor_path), то цикл существует.
    SELECT EXISTS(SELECT 1
                  FROM ancestor_path
                  WHERE ancestor_id = NEW.id)
    INTO is_ancestor;

    IF is_ancestor THEN
        RAISE EXCEPTION 'Circular reference detected: node % is already an ancestor of its parent.', NEW.id;
    END IF;

    RETURN NEW;
END;
$$;

create or replace function check_links_same_model() returns trigger
    language plpgsql
as
$$
DECLARE
    model_count       INTEGER;
    node_exists_count INTEGER;
    common_model      UUID;
BEGIN
    -- 1. Оптимизированный запрос:
    -- Подсчитываем количество уникальных моделей (model_count) и общее количество найденных узлов (node_exists_count).
    -- MAX(n.model) извлечет UUID модели, если model_count равен 1.
    SELECT COUNT(DISTINCT n.model),
           COUNT(n.id),
           MAX(n.model)
    INTO
        model_count,
        node_exists_count,
        common_model
    FROM public.nodes n
    WHERE n.id IN (NEW.source, NEW.target);

    -- 2. Проверка существования узлов
    -- Мы ожидаем найти 2 узла (source и target).
    IF node_exists_count < 2 THEN
        RAISE EXCEPTION 'Исходный (%) или целевой (%) узел не существует.', NEW.source, NEW.target;
    END IF;

    -- 3. Проверка: принадлежат ли они одной и той же модели, model_count должен быть равен 1.
    IF model_count != 1 THEN
        RAISE EXCEPTION 'Исходный и целевой узлы должны принадлежать одной и той же модели (найдено % уникальных моделей).', model_count;
    END IF;

    -- 4. Проверка: соответствует ли модель в таблице links модели узлов?
    -- Используем IS DISTINCT FROM для безопасного сравнения, включая NULL (хотя в вашей схеме model NOT NULL)
    IF NEW.model IS DISTINCT FROM common_model THEN
        RAISE EXCEPTION 'Модель связи (%) должна совпадать с моделью исходного/целевого узлов (%).', NEW.model, common_model;
    END IF;

    RETURN NEW;
END;
$$;

-- Триггер для предотвращения циклических ссылок в узлах (без изменений)
create trigger nodes_circular_reference_check
    before insert or update
    on public.nodes
    for each row
    when (new.parent_node is not null)
execute function check_node_circular_reference();

-- Триггер для обеспечения того, что исходный и целевой узлы принадлежат одной и той же модели (без изменений)
create trigger links_same_model_check
    before insert or update
    on public.links
    for each row
execute function check_links_same_model();

-- Триггеры для автоматического обновления updated_at
create trigger set_users_updated_at_timestamp
    before update
    on public.users
    for each row
execute function update_updated_at_column();

create trigger set_models_updated_at_timestamp
    before update
    on public.models
    for each row
execute function update_updated_at_column();

create trigger set_notations_updated_at_timestamp
    before update
    on public.notations
    for each row
execute function update_updated_at_column();

create trigger set_node_types_updated_at_timestamp
    before update
    on public.node_types
    for each row
execute function update_updated_at_column();

create trigger set_nodes_updated_at_timestamp
    before update
    on public.nodes
    for each row
execute function update_updated_at_column();

create trigger set_components_updated_at_timestamp
    before update
    on public.components
    for each row
execute function update_updated_at_column();

create trigger set_link_types_updated_at_timestamp
    before update
    on public.link_types
    for each row
execute function update_updated_at_column();

create trigger set_links_updated_at_timestamp
    before update
    on public.links
    for each row
execute function update_updated_at_column();

create trigger set_relations_updated_at_timestamp
    before update
    on public.relations
    for each row
execute function update_updated_at_column();

create trigger set_relation_rules_updated_at_timestamp
    before update
    on public.relation_rules
    for each row
execute function update_updated_at_column();

-- Триггеры аудита (теперь используют контекст сессии)
create trigger users_audit_trigger
    after insert or update or delete
    on public.users
    for each row
execute function audit_trigger();

create trigger models_audit_trigger
    after insert or update or delete
    on public.models
    for each row
execute function audit_trigger();

create trigger notations_audit_trigger
    after insert or update or delete
    on public.notations
    for each row
execute function audit_trigger();

create trigger node_types_audit_trigger
    after insert or update or delete
    on public.node_types
    for each row
execute function audit_trigger();

create trigger nodes_audit_trigger
    after insert or update or delete
    on public.nodes
    for each row
execute function audit_trigger();

create trigger components_audit_trigger
    after insert or update or delete
    on public.components
    for each row
execute function audit_trigger();

create trigger link_types_audit_trigger
    after insert or update or delete
    on public.link_types
    for each row
execute function audit_trigger();

create trigger links_audit_trigger
    after insert or update or delete
    on public.links
    for each row
execute function audit_trigger();

create trigger relations_audit_trigger
    after insert or update or delete
    on public.relations
    for each row
execute function audit_trigger();

create trigger relation_rules_audit_trigger
    after insert or update or delete
    on public.relation_rules
    for each row
execute function audit_trigger();
