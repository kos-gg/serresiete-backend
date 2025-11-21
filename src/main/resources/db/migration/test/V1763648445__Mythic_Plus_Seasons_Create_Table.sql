CREATE TABLE mythic_plus_seasons
(
    id           integer,
    name         varchar(48) not null,
    expansion_id integer,
    data         text        not null,
    PRIMARY KEY (id, expansion_id)
)