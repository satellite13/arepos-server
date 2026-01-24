-- Добавление колонки deleted в таблицу models для реализации soft delete
ALTER TABLE public.models
    ADD COLUMN IF NOT EXISTS deleted BOOLEAN NOT NULL DEFAULT false;

COMMENT ON COLUMN public.models.deleted IS 'Флаг мягкого удаления модели. Если true, модель считается удаленной и не возвращается через API';

-- Создание индекса для оптимизации запросов с фильтром по deleted
CREATE INDEX IF NOT EXISTS models_deleted_idx ON public.models (deleted) WHERE deleted = false;
