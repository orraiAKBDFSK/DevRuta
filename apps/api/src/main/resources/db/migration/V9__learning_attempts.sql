-- Módulo learning: lo que hace cada estudiante con los ejercicios.
-- exercise_attempt es solo de inserción: guarda cada intento tal cual, sin perder información. El progreso y la XP
-- (fase 8) se calcularán a partir de estos intentos. exercise_state resume el estado actual por usuario y ejercicio
-- para consultarlo sin recorrer todos los intentos. Se borran junto con la cuenta.
create table exercise_attempt (
    id          uuid primary key default uuidv7(),
    user_id     uuid        not null references app_user (id) on delete cascade,
    exercise_id uuid        not null references exercise (id),
    answer      jsonb       not null,
    correct     boolean     not null,
    -- Etiqueta del error cometido (p. ej. una opción incorrecta típica), para detectar fallos frecuentes.
    error_tag   varchar(80),
    hints_used  smallint    not null default 0,
    created_at  timestamptz not null default now()
);

create index exercise_attempt_user_idx on exercise_attempt (user_id, exercise_id, created_at desc);
create index exercise_attempt_exercise_idx on exercise_attempt (exercise_id);

create table exercise_state (
    user_id     uuid        not null references app_user (id) on delete cascade,
    exercise_id uuid        not null references exercise (id),
    attempts    int         not null default 0,
    hints_used  smallint    not null default 0,
    solved_at   timestamptz,
    updated_at  timestamptz not null default now(),
    primary key (user_id, exercise_id)
);
