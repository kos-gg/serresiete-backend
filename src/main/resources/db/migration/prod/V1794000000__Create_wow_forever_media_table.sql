create table wow_forever_media
(
    kind     text    not null,
    id       bigint  not null,
    region   text    not null,
    icon     text,
    status   text    not null,
    attempts integer not null default 0,
    primary key (kind, id)
);

create index wow_forever_media_pending_idx on wow_forever_media (kind, id) where status = 'PENDING';
