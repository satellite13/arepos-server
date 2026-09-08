-- 060: автор последнего изменения диаграммы (для актуальности комментариев)
ALTER TABLE public.diagrams
    ADD COLUMN IF NOT EXISTS updated_by uuid REFERENCES public.users (id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS diagrams_updated_by_idx ON public.diagrams (updated_by);

-- Бэкфил: последний автор изменения из audit_log (best effort, где журнал ещё хранится)
UPDATE public.diagrams d
SET updated_by = last_edit.changed_by
FROM (
    SELECT DISTINCT ON (al.row_id) al.row_id, al.changed_by
    FROM public.audit_log al
    WHERE al.table_name = 'diagrams'
      AND al.operation IN ('INSERT', 'UPDATE')
      AND al.changed_by IS NOT NULL
    ORDER BY al.row_id, al.changed_at DESC
) last_edit
WHERE d.id = last_edit.row_id
  AND d.updated_by IS NULL;
