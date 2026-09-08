-- Diagram comments: threads on diagrams and canvas element instances (nodes/edges)
-- Idempotent (runOnChange: true) — safe to re-run on schema evolution during review.

ALTER TABLE public.diagrams ADD COLUMN IF NOT EXISTS deleted_at timestamptz;

CREATE TABLE IF NOT EXISTS public.comments
(
    id              uuid        default gen_random_uuid() not null
        constraint comments_pk primary key,
    model_id        uuid                                  not null
        constraint comments_model_fk
            references public.models
            on delete cascade,
    diagram_id      uuid                                  not null
        constraint comments_diagram_fk
            references public.diagrams
            on delete cascade,
    target_type     text                                  not null
        constraint comments_target_type_ck check (target_type in ('diagram', 'node', 'edge')),
    instance_id     text,
    element_name    text,
    thread_id       uuid
        constraint comments_thread_fk
            references public.comments
            on delete cascade,
    author_id       uuid                                  not null
        constraint comments_author_fk
            references public.users,
    body_md         text                                  not null
        constraint comments_body_len_ck check (length(body_md) <= 10000),
    mentions        jsonb                 default '[]'::jsonb not null,
    is_resolved     boolean               default false   not null,
    resolved_by     uuid
        constraint comments_resolved_by_fk
            references public.users,
    resolved_at     timestamptz,
    resolved_reason text
        constraint comments_resolved_reason_ck check (resolved_reason in ('manual', 'element_removed')),
    deleted_at      timestamptz,
    edited_at       timestamptz,
    created_at      timestamptz           default now()   not null,
    updated_at      timestamptz           default now()   not null,
    constraint comments_thread_root_ck check (thread_id is null or id <> thread_id)
);

CREATE INDEX IF NOT EXISTS comments_diagram_created_idx ON public.comments (diagram_id, created_at);
CREATE INDEX IF NOT EXISTS comments_diagram_target_idx ON public.comments (diagram_id, target_type, instance_id);
CREATE INDEX IF NOT EXISTS comments_thread_idx ON public.comments (thread_id);
CREATE INDEX IF NOT EXISTS comments_mentions_gin_idx ON public.comments USING gin (mentions jsonb_path_ops);
CREATE INDEX IF NOT EXISTS comments_live_idx ON public.comments (diagram_id) WHERE deleted_at IS NULL;

-- comment_attachments: surrogate id added on re-run (fresh installs create it directly)
DO $$
DECLARE
  v_table_exists boolean;
  v_pk_exists boolean;
  v_id_column_exists boolean;
BEGIN
  SELECT EXISTS (
      SELECT 1 FROM information_schema.tables
      WHERE table_schema = 'public' AND table_name = 'comment_attachments'
  ) INTO v_table_exists;
  IF v_table_exists THEN
    SELECT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'comment_attachments_pk' AND conrelid = 'public.comment_attachments'::regclass
    ) INTO v_pk_exists;
    SELECT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'comment_attachments' AND column_name = 'id'
    ) INTO v_id_column_exists;
    IF v_pk_exists AND NOT v_id_column_exists THEN
      ALTER TABLE public.comment_attachments DROP CONSTRAINT comment_attachments_pk;
    END IF;
  END IF;
END $$;

CREATE TABLE IF NOT EXISTS public.comment_attachments
(
    id         uuid    default gen_random_uuid(),
    comment_id uuid not null
        constraint comment_attachments_comment_fk
            references public.comments
            on delete cascade,
    file_id    uuid not null
        constraint comment_attachments_file_fk
            references public.files
            on delete cascade,
    position   integer default 0 not null
);

ALTER TABLE public.comment_attachments ADD COLUMN IF NOT EXISTS id uuid;
UPDATE public.comment_attachments SET id = gen_random_uuid() WHERE id IS NULL;
ALTER TABLE public.comment_attachments ALTER COLUMN id SET DEFAULT gen_random_uuid();
ALTER TABLE public.comment_attachments ALTER COLUMN id SET NOT NULL;

DO $$
BEGIN
  IF NOT EXISTS (
      SELECT 1 FROM pg_constraint
      WHERE conname = 'comment_attachments_pk' AND conrelid = 'public.comment_attachments'::regclass
  ) THEN
    ALTER TABLE public.comment_attachments ADD CONSTRAINT comment_attachments_pk PRIMARY KEY (id);
  END IF;
  IF NOT EXISTS (
      SELECT 1 FROM pg_constraint
      WHERE conname = 'comment_attachments_comment_file_uq' AND conrelid = 'public.comment_attachments'::regclass
  ) THEN
    ALTER TABLE public.comment_attachments ADD CONSTRAINT comment_attachments_comment_file_uq UNIQUE (comment_id, file_id);
  END IF;
END $$;

CREATE TABLE IF NOT EXISTS public.comment_reactions
(
    comment_id uuid                    not null
        constraint comment_reactions_comment_fk
            references public.comments
            on delete cascade,
    user_id    uuid                    not null
        constraint comment_reactions_user_fk
            references public.users
            on delete cascade,
    emoji      text                    not null,
    created_at timestamptz default now() not null,
    constraint comment_reactions_pk primary key (comment_id, user_id, emoji)
);

CREATE TABLE IF NOT EXISTS public.comment_read_state
(
    user_id      uuid not null
        constraint comment_read_state_user_fk
            references public.users
            on delete cascade,
    diagram_id   uuid not null
        constraint comment_read_state_diagram_fk
            references public.diagrams
            on delete cascade,
    last_read_at timestamptz default now() not null,
    constraint comment_read_state_pk primary key (user_id, diagram_id)
);

CREATE TABLE IF NOT EXISTS public.notifications
(
    id         uuid        default gen_random_uuid() not null
        constraint notifications_pk primary key,
    user_id    uuid                                  not null
        constraint notifications_user_fk
            references public.users
            on delete cascade,
    type       text                                  not null,
    payload    jsonb                                 not null,
    read_at    timestamptz,
    created_at timestamptz default now()             not null
);

CREATE INDEX IF NOT EXISTS notifications_user_idx ON public.notifications (user_id, created_at DESC);

CREATE TABLE IF NOT EXISTS public.comment_link_previews
(
    url           text        not null
        constraint comment_link_previews_pk primary key,
    title         text,
    description   text,
    site_name     text,
    image_file_id uuid
        constraint comment_link_previews_image_fk
            references public.files
            on delete set null,
    fetched_at    timestamptz default now() not null
);

DROP TRIGGER IF EXISTS comments_audit_trigger ON public.comments;
CREATE TRIGGER comments_audit_trigger
    AFTER INSERT OR UPDATE OR DELETE
    ON public.comments
    FOR EACH ROW
EXECUTE FUNCTION audit_trigger();

DROP TRIGGER IF EXISTS notifications_audit_trigger ON public.notifications;
CREATE TRIGGER notifications_audit_trigger
    AFTER INSERT OR UPDATE OR DELETE
    ON public.notifications
    FOR EACH ROW
EXECUTE FUNCTION audit_trigger();
