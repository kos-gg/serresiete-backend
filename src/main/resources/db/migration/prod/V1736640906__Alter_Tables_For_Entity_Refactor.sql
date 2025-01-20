alter table characters_view rename to view_entities;
alter table lol_characters rename to lol_entities;
alter table wow_characters rename to wow_entities;
alter table wow_hardcore_characters rename to wow_hardcore_entities;
alter sequence characters_ids rename TO entities_ids;

alter table data_cache rename column character_id to entity_id;
alter table view_entities rename column character_id to entity_id;