create table wow_forever_media
(
    kind text   not null,
    id   bigint not null,
    icon text   not null,
    primary key (kind, id)
);
