-- Exclusive edit lock per diagram row (see docs in warchi diagram-edit-locks plan)
CREATE TABLE public.diagram_edit_locks
(
    id                uuid PRIMARY KEY     DEFAULT gen_random_uuid(),
    diagram_id        uuid        NOT NULL REFERENCES public.diagrams (id) ON DELETE CASCADE,
    locked_by_user_id uuid        NOT NULL REFERENCES public.users (id) ON DELETE CASCADE,
    locked_at         timestamptz NOT NULL DEFAULT now(),
    last_heartbeat_at timestamptz NOT NULL DEFAULT now(),
    expires_at        timestamptz NOT NULL,
    CONSTRAINT diagram_edit_locks_diagram_id_key UNIQUE (diagram_id)
);

CREATE INDEX diagram_edit_locks_expires_at_idx ON public.diagram_edit_locks (expires_at);
CREATE INDEX diagram_edit_locks_locked_by_user_id_idx ON public.diagram_edit_locks (locked_by_user_id);
