-- Ejercicios del catálogo (contenido). Se importan de los YAML de las lecciones igual que el resto del catálogo.
-- config y solution son JSONB porque su forma depende del tipo de ejercicio (selección múltiple, ordenar líneas...) y
-- solo se leen enteros, nunca se consultan por dentro. Lo que se consulta (lección, orden, tipo, habilidades) va en
-- columnas relacionales. solution contiene las respuestas correctas: el API nunca la envía al navegador.
create table exercise (
    id         uuid primary key default uuidv7(),
    content_id varchar(80)  not null unique,
    lesson_id  uuid         not null references lesson (id),
    position   smallint     not null,
    type       varchar(20)  not null,
    difficulty smallint     not null,
    config     jsonb        not null default '{}'::jsonb,
    solution   jsonb        not null,
    archived   boolean      not null default false,
    created_at timestamptz  not null default now(),
    updated_at timestamptz  not null default now(),
    constraint exercise_type_chk check (type in
        ('MULTIPLE_CHOICE', 'PREDICT_OUTPUT', 'ORDER_LINES', 'FILL_CODE', 'WRITE_CODE')),
    constraint exercise_difficulty_chk check (difficulty between 1 and 5)
);

create index exercise_lesson_idx on exercise (lesson_id, position);

create table exercise_translation (
    exercise_id uuid       not null references exercise (id) on delete cascade,
    locale      varchar(5) not null,
    prompt      text       not null,
    explanation text       not null,
    -- Opciones de un ejercicio de selección múltiple (arreglo de textos) y pistas de menos a más reveladora.
    options     jsonb,
    hints       jsonb      not null default '[]'::jsonb,
    primary key (exercise_id, locale),
    constraint exercise_translation_locale_chk check (locale in ('es', 'en'))
);

create table exercise_skill (
    exercise_id uuid not null references exercise (id) on delete cascade,
    skill_id    uuid not null references skill (id) on delete cascade,
    primary key (exercise_id, skill_id)
);

create index exercise_skill_skill_idx on exercise_skill (skill_id);
