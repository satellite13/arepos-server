-- Добавление колонки deleted в таблицу notations для реализации soft delete
ALTER TABLE public.notations
    ADD COLUMN IF NOT EXISTS deleted BOOLEAN NOT NULL DEFAULT false;

COMMENT ON COLUMN public.notations.deleted IS 'Флаг мягкого удаления нотации. Если true, нотация считается удаленной и не возвращается через API';

-- Создание индекса для оптимизации запросов с фильтром по deleted
CREATE INDEX IF NOT EXISTS notations_deleted_idx ON public.notations (deleted) WHERE deleted = false;
