CREATE TABLE wow_expansions
(
    id                   integer NOT NULL,
    name                 varchar(48) NOT NULL,
    is_current_expansion boolean NOT NULL,
    PRIMARY KEY (id, name),
    UNIQUE (id)
);