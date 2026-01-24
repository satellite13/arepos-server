-- Добавление триггеров для установки updated_at при INSERT
-- Это гарантирует, что поле updated_at будет заполнено даже при прямых INSERT в БД

-- Функция для установки updated_at при INSERT (если не установлено)
CREATE OR REPLACE FUNCTION set_updated_at_on_insert()
    RETURNS TRIGGER AS
$$
BEGIN
    IF NEW.updated_at IS NULL THEN
        NEW.updated_at = now();
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Триггеры для всех таблиц с полем updated_at
CREATE TRIGGER set_users_updated_at_on_insert
    BEFORE INSERT
    ON public.users
    FOR EACH ROW
EXECUTE FUNCTION set_updated_at_on_insert();

CREATE TRIGGER set_models_updated_at_on_insert
    BEFORE INSERT
    ON public.models
    FOR EACH ROW
EXECUTE FUNCTION set_updated_at_on_insert();

CREATE TRIGGER set_notations_updated_at_on_insert
    BEFORE INSERT
    ON public.notations
    FOR EACH ROW
EXECUTE FUNCTION set_updated_at_on_insert();

CREATE TRIGGER set_node_types_updated_at_on_insert
    BEFORE INSERT
    ON public.node_types
    FOR EACH ROW
EXECUTE FUNCTION set_updated_at_on_insert();

CREATE TRIGGER set_nodes_updated_at_on_insert
    BEFORE INSERT
    ON public.nodes
    FOR EACH ROW
EXECUTE FUNCTION set_updated_at_on_insert();

CREATE TRIGGER set_components_updated_at_on_insert
    BEFORE INSERT
    ON public.components
    FOR EACH ROW
EXECUTE FUNCTION set_updated_at_on_insert();

CREATE TRIGGER set_link_types_updated_at_on_insert
    BEFORE INSERT
    ON public.link_types
    FOR EACH ROW
EXECUTE FUNCTION set_updated_at_on_insert();

CREATE TRIGGER set_links_updated_at_on_insert
    BEFORE INSERT
    ON public.links
    FOR EACH ROW
EXECUTE FUNCTION set_updated_at_on_insert();

CREATE TRIGGER set_relations_updated_at_on_insert
    BEFORE INSERT
    ON public.relations
    FOR EACH ROW
EXECUTE FUNCTION set_updated_at_on_insert();

CREATE TRIGGER set_relation_rules_updated_at_on_insert
    BEFORE INSERT
    ON public.relation_rules
    FOR EACH ROW
EXECUTE FUNCTION set_updated_at_on_insert();

COMMENT ON FUNCTION set_updated_at_on_insert() IS 'Устанавливает updated_at при INSERT, если поле не было установлено явно';
