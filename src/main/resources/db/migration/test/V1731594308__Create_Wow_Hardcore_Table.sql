create table wow_hardcore_characters
(
    id     integer default nextval('characters_ids') primary key,
    name   varchar(48) not null,
    region varchar(48) not null,
    realm  varchar(48) not null,
    constraint uchc UNIQUE (name, region, realm)
);

insert into subscriptions (name) values ('sync-wow');
insert into subscriptions (name) values ('sync-wow-hc');