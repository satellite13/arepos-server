-- Уникальность (name, version) только среди неудалённых: можно повторно использовать версию удалённой модели/нотации
ALTER TABLE public.models
    DROP CONSTRAINT IF EXISTS models_name_version_key;

CREATE UNIQUE INDEX models_name_version_undeleted_key
    ON public.models (name, version)
    WHERE deleted = false;

COMMENT ON INDEX public.models_name_version_undeleted_key IS 'Один активный (deleted=false) экземпляр на пару (name, version). Удалённые могут иметь те же name+version.';

ALTER TABLE public.notations
    DROP CONSTRAINT IF EXISTS notations_name_version_key;

CREATE UNIQUE INDEX notations_name_version_undeleted_key
    ON public.notations (name, version)
    WHERE deleted = false;

COMMENT ON INDEX public.notations_name_version_undeleted_key IS 'Один активный (deleted=false) экземпляр на пару (name, version). Удалённые могут иметь те же name+version.';
