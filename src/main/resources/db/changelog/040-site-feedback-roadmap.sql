-- Public site: feedback board + curated roadmap

create table public.feedback_items
(
    id          uuid         default gen_random_uuid() not null
        constraint feedback_items_pk primary key,
    type        varchar(16)                            not null
        constraint feedback_items_type_chk
            check (type in ('idea', 'bug')),
    title       varchar(200)                           not null,
    body        text                                   not null,
    status      varchar(32)                            not null
        constraint feedback_items_status_chk
            check (status in ('new', 'planned', 'in_progress', 'done', 'declined')),
    author_id   uuid                                   not null
        constraint feedback_items_author_fk
            references public.users
            on delete cascade,
    vote_count  int          default 0                 not null,
    created_at  timestamptz  default now()             not null,
    updated_at  timestamptz  default now()             not null
);

create index feedback_items_author_id_idx on public.feedback_items (author_id);
create index feedback_items_status_idx on public.feedback_items (status);
create index feedback_items_type_idx on public.feedback_items (type);
create index feedback_items_vote_count_idx on public.feedback_items (vote_count desc);
create index feedback_items_created_at_idx on public.feedback_items (created_at desc);

create trigger feedback_items_audit_trigger
    after insert or update or delete
    on public.feedback_items
    for each row
execute function audit_trigger();

create table public.feedback_votes
(
    id         uuid        default gen_random_uuid() not null
        constraint feedback_votes_pk primary key,
    item_id    uuid                                  not null
        constraint feedback_votes_item_fk
            references public.feedback_items
            on delete cascade,
    user_id    uuid                                  not null
        constraint feedback_votes_user_fk
            references public.users
            on delete cascade,
    created_at timestamptz default now()             not null,
    constraint feedback_votes_item_user_uq unique (item_id, user_id)
);

create index feedback_votes_user_id_idx on public.feedback_votes (user_id);

create trigger feedback_votes_audit_trigger
    after insert or update or delete
    on public.feedback_votes
    for each row
execute function audit_trigger();

create table public.feedback_comments
(
    id         uuid        default gen_random_uuid() not null
        constraint feedback_comments_pk primary key,
    item_id    uuid                                  not null
        constraint feedback_comments_item_fk
            references public.feedback_items
            on delete cascade,
    author_id  uuid                                  not null
        constraint feedback_comments_author_fk
            references public.users
            on delete cascade,
    body       text                                  not null,
    created_at timestamptz default now()             not null
);

create index feedback_comments_item_id_idx on public.feedback_comments (item_id);
create index feedback_comments_author_id_idx on public.feedback_comments (author_id);

create trigger feedback_comments_audit_trigger
    after insert or update or delete
    on public.feedback_comments
    for each row
execute function audit_trigger();

create table public.roadmap_milestones
(
    id             uuid         default gen_random_uuid() not null
        constraint roadmap_milestones_pk primary key,
    title          varchar(200)                           not null,
    description    text                                   not null default '',
    status         varchar(32)                            not null
        constraint roadmap_milestones_status_chk
            check (status in ('planned', 'in_progress', 'done')),
    sort_order     int          default 0                 not null,
    target_period  varchar(64),
    created_at     timestamptz  default now()             not null,
    updated_at     timestamptz  default now()             not null
);

create index roadmap_milestones_sort_order_idx on public.roadmap_milestones (sort_order);

create trigger roadmap_milestones_audit_trigger
    after insert or update or delete
    on public.roadmap_milestones
    for each row
execute function audit_trigger();

create table public.roadmap_milestone_items
(
    id           uuid default gen_random_uuid() not null
        constraint roadmap_milestone_items_pk primary key,
    milestone_id uuid                           not null
        constraint roadmap_milestone_items_milestone_fk
            references public.roadmap_milestones
            on delete cascade,
    feedback_item_id uuid                       not null
        constraint roadmap_milestone_items_feedback_fk
            references public.feedback_items
            on delete cascade,
    constraint roadmap_milestone_items_uq unique (milestone_id, feedback_item_id)
);

create index roadmap_milestone_items_feedback_idx on public.roadmap_milestone_items (feedback_item_id);

create trigger roadmap_milestone_items_audit_trigger
    after insert or update or delete
    on public.roadmap_milestone_items
    for each row
execute function audit_trigger();
