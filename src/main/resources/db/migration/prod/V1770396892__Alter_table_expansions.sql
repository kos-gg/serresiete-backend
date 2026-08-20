ALTER TABLE mythic_plus_seasons
DROP CONSTRAINT fk_mythic_plus_seasons;

ALTER TABLE mythic_plus_seasons
    ADD CONSTRAINT fk_mythic_plus_seasons
        FOREIGN KEY (expansion_id)
            REFERENCES wow_expansions(id)
            ON UPDATE CASCADE;

DELETE FROM wow_expansions
WHERE name = 'Classic';

UPDATE wow_expansions SET id = id + 100 WHERE id > 1;
UPDATE wow_expansions SET id = id - 101 WHERE id > 101;
