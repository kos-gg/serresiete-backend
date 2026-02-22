DELETE FROM wow_expansions;

INSERT INTO wow_expansions (id, name, is_current_expansion)
VALUES
    (1, 'The Burning Crusade', false),
    (2, 'Wrath of the Lich King', false),
    (3, 'Cataclysm', false),
    (4, 'Mists of Pandaria', false),
    (5, 'Warlords of Draenor', false),
    (6, 'Legion', false),
    (7, 'Battle for Azeroth', false),
    (8, 'Shadowlands', false),
    (9, 'Dragonflight', false),
    (10, 'The War Within', false),
    (11, 'Midnight', true),
    (12, 'The Last Titan', false);
