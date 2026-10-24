-- Módulo identity: cuentas de usuario.
-- uuidv7() está disponible desde PostgreSQL 18: identificadores únicos y ordenables por fecha de creación.
create table app_user (
    id                uuid primary key default uuidv7(),
    email             varchar(254) not null,
    password_hash     varchar(255) not null,
    status            varchar(20)  not null default 'ACTIVE',
    terms_version     varchar(20)  not null,
    terms_accepted_at timestamptz  not null,
    created_at        timestamptz  not null default now(),
    updated_at        timestamptz  not null default now(),
    constraint app_user_status_chk check (status in ('ACTIVE', 'DISABLED'))
);

-- El correo se guarda en minúsculas; el índice sobre lower(email) impide duplicados aunque algo lo evite.
create unique index app_user_email_uk on app_user (lower(email));
