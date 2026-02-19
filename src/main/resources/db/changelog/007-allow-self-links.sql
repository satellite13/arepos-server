alter table public.links
    drop constraint if exists links_no_self_link_check;
