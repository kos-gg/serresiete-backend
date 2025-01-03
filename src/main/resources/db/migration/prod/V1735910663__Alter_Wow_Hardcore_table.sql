alter table wow_hardcore_characters drop constraint uchc;
alter table wow_hardcore_characters add column blizzard_id varchar(64) not null;