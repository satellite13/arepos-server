-- Stable diagram series for «always latest» SVG share links (survives rename + baseline)
ALTER TABLE public.diagrams
    ADD COLUMN IF NOT EXISTS series_id uuid;

UPDATE public.diagrams d
SET series_id = g.series_id
FROM (
    SELECT
        model,
        name,
        (array_agg(id ORDER BY created_at ASC NULLS LAST, id ASC))[1] AS series_id
    FROM public.diagrams
    GROUP BY model, name
) g
WHERE d.model = g.model
  AND d.name = g.name
  AND d.series_id IS NULL;

UPDATE public.diagrams
SET series_id = id
WHERE series_id IS NULL;

ALTER TABLE public.diagrams
    ALTER COLUMN series_id SET NOT NULL;

CREATE INDEX IF NOT EXISTS diagrams_series_id_idx
    ON public.diagrams (series_id);

COMMENT ON COLUMN public.diagrams.series_id IS
    'Стабильный идентификатор линейки версий диаграммы (общий у baseline, не меняется при переименовании)';

ALTER TABLE public.diagram_preview_links
    ADD COLUMN IF NOT EXISTS latest boolean NOT NULL DEFAULT false;

COMMENT ON COLUMN public.diagram_preview_links.latest IS
    'true — резолвить текущую голову series_id; false — зафиксированная версия diagram_id';

UPDATE public.diagram_preview_links
SET latest = true
WHERE diagram_id IS NULL
  AND model_id IS NOT NULL
  AND diagram_name IS NOT NULL;

WITH heads AS (
    SELECT DISTINCT ON (d.series_id)
        d.id,
        d.model,
        d.name,
        d.series_id
    FROM public.diagrams d
    WHERE d.deleted = false
    ORDER BY d.series_id, d.updated_at DESC NULLS LAST, d.created_at DESC NULLS LAST, d.id DESC
)
UPDATE public.diagram_preview_links l
SET
    diagram_id = heads.id,
    model_id = NULL,
    diagram_name = NULL,
    latest = true
FROM heads
WHERE l.diagram_id IS NULL
  AND l.model_id = heads.model
  AND l.diagram_name = heads.name;
