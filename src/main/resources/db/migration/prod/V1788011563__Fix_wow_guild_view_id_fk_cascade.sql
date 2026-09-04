alter table wow_guilds drop constraint wow_guilds_view_id_fkey;
alter table wow_guilds add constraint wow_guilds_view_id_fkey foreign key (view_id) references views(id) on delete cascade;
