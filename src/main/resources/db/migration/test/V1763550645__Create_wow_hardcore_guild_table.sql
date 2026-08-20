create table wow_hardcore_guilds(
    blizzard_id integer not null primary key,
    name text not null,
    realm text not null,
    region text not null,
    view_id text not null REFERENCES views(id)
);