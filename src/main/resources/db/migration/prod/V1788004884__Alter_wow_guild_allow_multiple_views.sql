alter table wow_guilds drop constraint wow_guilds_pkey;
alter table wow_guilds add primary key (blizzard_id, game, view_id);

update wow_guilds set name = lower(name), realm = lower(realm), region = lower(region);

create index if not exists wow_guilds_identity_idx on wow_guilds (game, region, realm, name);
