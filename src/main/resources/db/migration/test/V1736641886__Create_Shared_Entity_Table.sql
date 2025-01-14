CREATE TABLE entities (
    id INTEGER PRIMARY KEY
);

INSERT INTO entities (id) SELECT id FROM wow_hardcore_entities;
INSERT INTO entities (id) SELECT id FROM wow_entities;
INSERT INTO entities (id) SELECT id FROM lol_entities;

ALTER TABLE wow_hardcore_entities ADD CONSTRAINT fk_entities FOREIGN KEY (id) REFERENCES entities (id) ON DELETE CASCADE;
ALTER TABLE wow_entities ADD CONSTRAINT fk_entities FOREIGN KEY (id) REFERENCES entities (id) ON DELETE CASCADE;
ALTER TABLE lol_entities ADD CONSTRAINT fk_entities FOREIGN KEY (id) REFERENCES entities (id) ON DELETE CASCADE;

ALTER TABLE view_entities ADD CONSTRAINT fk_entities FOREIGN KEY (entity_id) REFERENCES entities (id) ON DELETE CASCADE;