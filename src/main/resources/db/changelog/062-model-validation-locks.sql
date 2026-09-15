-- Model-scoped locks for validation operations (auto-merge).
-- One row per model: prevents two users from running the validation auto-merge at
-- the same time. Stale locks (older than the service TTL) may be taken over.
CREATE TABLE IF NOT EXISTS public.model_validation_locks (
    model uuid PRIMARY KEY REFERENCES public.models(id) ON DELETE CASCADE,
    locked_by uuid NOT NULL REFERENCES public.users(id),
    locked_at timestamptz NOT NULL DEFAULT now()
);

COMMENT ON TABLE public.model_validation_locks IS
    'Блокировки операций валидации модели (например, автослияния дубликатов): один владелец на модель';
