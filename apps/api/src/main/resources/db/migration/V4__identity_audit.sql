-- Auditoría de acciones sensibles de identidad. Guarda el mínimo de datos: tipo de evento, fecha y usuario.
-- No guarda IP, correo ni contraseñas. user_id no tiene clave foránea: al borrar una cuenta los eventos
-- se anonimizan (user_id = null) en lugar de borrarse.
create table audit_event (
    id         bigint generated always as identity primary key,
    user_id    uuid,
    event_type varchar(40) not null,
    created_at timestamptz not null default now()
);

create index audit_event_user_idx on audit_event (user_id);
create index audit_event_created_idx on audit_event (created_at);
