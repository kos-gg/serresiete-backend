create table views
(
    id varchar(48) not null primary key,
    owner varchar(48) not null
);

create table characters_view
(
    character_id integer not null,
    view_id varchar(48) not null
);