ALTER TABLE public.diagram_edit_locks
    ADD COLUMN IF NOT EXISTS version bigint NOT NULL DEFAULT 0;
