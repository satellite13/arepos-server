-- Content area for custom node shapes: rectangle in normalized 0-1 for icon/label placement
alter table public.node_shapes
    add column if not exists content_area jsonb;

comment on column public.node_shapes.content_area is 'Inner rectangle (x, y, width, height in 0-1) for icon and label placement';
