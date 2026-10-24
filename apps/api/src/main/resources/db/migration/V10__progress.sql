-- Progreso (fase 8): XP, rachas y logros. Todo se borra junto con la cuenta.
-- xp_event tiene una fila por usuario y ejercicio (primary key): un ejercicio da XP una sola vez, aunque se reenvíe
-- la respuesta correcta o dos peticiones lleguen a la vez. user_progress es un resumen (se puede reconstruir a
-- partir de xp_event); la racha cuenta días UTC con al menos un ejercicio resuelto por primera vez.
create table xp_event (
    user_id     uuid        not null references app_user (id) on delete cascade,
    exercise_id uuid        not null references exercise (id),
    xp          smallint    not null check (xp > 0),
    hints_used  smallint    not null default 0,
    created_at  timestamptz not null default now(),
    primary key (user_id, exercise_id)
);

create table user_progress (
    user_id         uuid primary key references app_user (id) on delete cascade,
    xp              int         not null default 0 check (xp >= 0),
    current_streak  int         not null default 0 check (current_streak >= 0),
    longest_streak  int         not null default 0 check (longest_streak >= 0),
    last_active_day date,
    updated_at      timestamptz not null default now()
);

create table user_achievement (
    user_id     uuid        not null references app_user (id) on delete cascade,
    code        varchar(40) not null,
    unlocked_at timestamptz not null default now(),
    primary key (user_id, code)
);
