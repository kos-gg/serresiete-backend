alter table wow_hardcore_characters drop constraint uchc;
alter table wow_hardcore_characters add column blizzard_id integer default -1;