create table wow_forever_entities
(
    id          integer primary key references entities (id) on delete cascade,
    name        text   not null,
    surname     text   not null,
    region      text   not null,
    blizzard_id bigint not null unique
);

insert into subscriptions (name) values ('sync-wow-forever');
