-- Hidden «Diagram only» nodes stay in the model tree after their diagram is deleted.
-- Remove those that are not placed on any live (deleted = false) diagram.

WITH live_canvas_nodes AS (
    SELECT DISTINCT (elem ->> 'modelNodeId')::uuid AS node_id
    FROM public.diagrams d
    CROSS JOIN LATERAL jsonb_array_elements(
        CASE
            WHEN jsonb_typeof(d.attrs -> 'instances' -> 'nodes') = 'array'
                THEN d.attrs -> 'instances' -> 'nodes'
            ELSE '[]'::jsonb
        END
        ||
        CASE
            WHEN jsonb_typeof(d.attrs -> 'nodes') = 'array'
                THEN d.attrs -> 'nodes'
            ELSE '[]'::jsonb
        END
    ) AS elem
    WHERE d.deleted = false
      AND (elem ->> 'modelNodeId') ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
),
orphans AS (
    SELECT n.id
    FROM public.nodes n
    JOIN public.node_types nt ON nt.id = n.node_type
    WHERE lower(nt.name) = 'diagram only'
      AND NOT EXISTS (
          SELECT 1
          FROM public.nodes child
          WHERE child.parent_node = n.id
      )
      AND NOT EXISTS (
          SELECT 1
          FROM live_canvas_nodes live
          WHERE live.node_id = n.id
      )
)
DELETE FROM public.nodes n
WHERE n.id IN (SELECT id FROM orphans);
