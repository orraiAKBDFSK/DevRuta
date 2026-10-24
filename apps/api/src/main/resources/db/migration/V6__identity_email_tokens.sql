-- Verificación de correo y recuperación de contraseña.
alter table app_user add column email_verified_at timestamptz;

-- Tokens de un solo uso enviados por correo. Solo se guarda el hash SHA-256: quien lea la base de datos
-- no puede usarlos. purpose distingue el uso para que un token de verificación no sirva para recuperar la contraseña.
create table email_token (
    id         uuid primary key default uuidv7(),
    user_id    uuid         not null references app_user (id) on delete cascade,
    purpose    varchar(20)  not null,
    token_hash varchar(64)  not null,
    expires_at timestamptz  not null,
    used_at    timestamptz,
    created_at timestamptz  not null default now(),
    constraint email_token_purpose_chk check (purpose in ('VERIFY_EMAIL', 'RESET_PASSWORD')),
    constraint email_token_hash_len_chk check (char_length(token_hash) = 64)
);

create unique index email_token_hash_uk on email_token (token_hash);
create index email_token_user_idx on email_token (user_id, purpose);
