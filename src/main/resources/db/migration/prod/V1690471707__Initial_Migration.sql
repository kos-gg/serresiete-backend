create table users
(
    user_name varchar(48) not null primary key,
    password varchar(48) not null
);

create table authorizations
(
    user_name varchar(48) not null,
    token varchar(128) not null primary key,
    last_used text not null,
    valid_until text not null
);

insert into users values('eric','1234')