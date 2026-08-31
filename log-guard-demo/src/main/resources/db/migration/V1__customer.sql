create table customer (
    id            bigserial primary key,
    email         varchar(255) not null unique,
    phone_number  varchar(32)  not null,
    national_id   varchar(32)  not null,
    date_of_birth date         not null,
    city          varchar(128)
);
