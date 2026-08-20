create table tasks
(
    id varchar(64) not null primary key,
    type varchar(48) not null,
    status text not null,
    inserted text not null
);