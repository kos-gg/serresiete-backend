create sequence characters_ids;

create table characters
(
    id integer default nextval('characters_ids') primary key,
    name varchar(48) not null,
    region varchar(48) not null,
    realm varchar(48) not null,
    constraint uc UNIQUE (name,region,realm)
);