create table legacy_customer (
    id            bigserial primary key,
    email         varchar(255) not null,
    phone_number  varchar(32)  not null,
    city          varchar(128)
);
