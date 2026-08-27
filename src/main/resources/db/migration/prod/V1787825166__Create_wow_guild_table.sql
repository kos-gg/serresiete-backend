create table wow_guilds(
    blizzard_id bigint not null,
    name text not null,
    realm text not null,
    region text not null,
    view_id text not null REFERENCES views(id),
    game text not null,
    primary key (blizzard_id, game)
);
