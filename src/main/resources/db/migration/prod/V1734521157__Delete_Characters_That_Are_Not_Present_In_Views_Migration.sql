insert into subscriptions (name) values ('characters');

ALTER TABLE characters_view ADD CONSTRAINT fk_view
        FOREIGN KEY (view_id) REFERENCES views (id)
        ON DELETE CASCADE;