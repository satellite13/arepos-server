ALTER TABLE public.feedback_items
    ADD COLUMN merged_into_id UUID,
    ADD COLUMN merged_at TIMESTAMPTZ,
    ADD CONSTRAINT feedback_items_merged_into_id_fkey
        FOREIGN KEY (merged_into_id) REFERENCES public.feedback_items (id),
    ADD CONSTRAINT feedback_items_merged_into_id_check
        CHECK (id <> merged_into_id);

CREATE INDEX feedback_items_merged_into_id_idx
    ON public.feedback_items (merged_into_id)
    WHERE merged_into_id IS NOT NULL;
