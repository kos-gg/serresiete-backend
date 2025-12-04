CREATE TABLE mythic_plus_seasons
(
    id           integer NOT NULL,
    name         varchar(48) NOT NULL,
    expansion_id integer NOT NULL,
    data         text NOT NULL,
    PRIMARY KEY (id, expansion_id),
    CONSTRAINT fk_mythic_plus_seasons
        FOREIGN KEY (expansion_id)
            REFERENCES wow_expansions(id)
);
