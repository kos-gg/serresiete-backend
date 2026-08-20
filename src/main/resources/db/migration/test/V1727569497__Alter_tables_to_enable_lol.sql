alter table characters rename to wow_characters;

create table lol_characters
(
    id integer default nextval('characters_ids') primary key,
    name varchar(48) not null,
    tag varchar(3) not null,
    puuid varchar(128) not null,
    summoner_icon integer not null,
    summoner_id varchar(128) not null,
    summoner_level integer not null,
    constraint up UNIQUE (puuid),
    constraint usi UNIQUE (summoner_id)
);