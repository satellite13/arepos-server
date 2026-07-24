-- Feedback public tracker numbers (FB-{n})

create sequence public.feedback_items_public_number_seq as integer start with 1 increment by 1;

alter table public.feedback_items
    add column public_number integer;

with numbered as (
    select id, row_number() over (order by created_at asc, id asc) as n
    from public.feedback_items
)
update public.feedback_items fi
set public_number = numbered.n
from numbered
where fi.id = numbered.id;

alter table public.feedback_items
    alter column public_number set not null;

alter table public.feedback_items
    add constraint feedback_items_public_number_uq unique (public_number);

alter table public.feedback_items
    alter column public_number set default nextval('public.feedback_items_public_number_seq');

select setval(
    'public.feedback_items_public_number_seq',
    coalesce((select max(public_number) from public.feedback_items), 0) + 1,
    false
);
