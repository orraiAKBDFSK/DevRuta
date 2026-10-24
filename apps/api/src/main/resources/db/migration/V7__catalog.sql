-- Módulo catalog: lenguajes, cursos, unidades, lecciones y habilidades.
-- El contenido vive en archivos YAML del repositorio (content/) y un importador lo carga aquí. Cada elemento tiene un
-- content_id estable (por ejemplo py.u01.l01) con el que se hace el "upsert". Los textos traducibles van en tablas
-- *_translation (una fila por idioma). Nada se borra al importar: lo que desaparece del contenido se archiva, porque
-- el progreso de los estudiantes (fase 8) apuntará a estas filas.

create table programming_language (
    id   uuid primary key default uuidv7(),
    code varchar(40) not null unique,
    name varchar(80) not null
);

create table course (
    id          uuid primary key default uuidv7(),
    content_id  varchar(80) not null unique,
    language_id uuid        not null references programming_language (id),
    position    smallint    not null,
    archived    boolean     not null default false,
    created_at  timestamptz not null default now(),
    updated_at  timestamptz not null default now()
);

create table course_translation (
    course_id   uuid         not null references course (id) on delete cascade,
    locale      varchar(5)   not null,
    title       varchar(200) not null,
    description text         not null,
    primary key (course_id, locale),
    constraint course_translation_locale_chk check (locale in ('es', 'en'))
);

create table unit (
    id         uuid primary key default uuidv7(),
    content_id varchar(80) not null unique,
    course_id  uuid        not null references course (id),
    position   smallint    not null,
    archived   boolean     not null default false,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index unit_course_idx on unit (course_id, position);

create table unit_translation (
    unit_id uuid         not null references unit (id) on delete cascade,
    locale  varchar(5)   not null,
    title   varchar(200) not null,
    summary text         not null,
    primary key (unit_id, locale),
    constraint unit_translation_locale_chk check (locale in ('es', 'en'))
);

create table lesson (
    id           uuid primary key default uuidv7(),
    content_id   varchar(80) not null unique,
    unit_id      uuid        not null references unit (id),
    position     smallint    not null,
    status       varchar(20) not null,
    -- Huella SHA-256 del contenido importado: si no cambia, la lección no se vuelve a escribir.
    content_hash varchar(64) not null,
    created_at   timestamptz not null default now(),
    updated_at   timestamptz not null default now(),
    constraint lesson_status_chk check (status in ('DRAFT', 'REVIEWED', 'PUBLISHED', 'ARCHIVED')),
    constraint lesson_hash_len_chk check (char_length(content_hash) = 64)
);

create index lesson_unit_idx on lesson (unit_id, position);

create table lesson_translation (
    lesson_id uuid         not null references lesson (id) on delete cascade,
    locale    varchar(5)   not null,
    title     varchar(200) not null,
    body_md   text         not null,
    primary key (lesson_id, locale),
    constraint lesson_translation_locale_chk check (locale in ('es', 'en'))
);

create table skill (
    id          uuid primary key default uuidv7(),
    content_id  varchar(80) not null unique,
    language_id uuid        not null references programming_language (id),
    position    smallint    not null default 0
);

create table skill_translation (
    skill_id uuid         not null references skill (id) on delete cascade,
    locale   varchar(5)   not null,
    name     varchar(120) not null,
    primary key (skill_id, locale),
    constraint skill_translation_locale_chk check (locale in ('es', 'en'))
);

-- Grafo de habilidades: skill_id requiere dominar prerequisite_id. El importador comprueba que no haya ciclos.
create table skill_prerequisite (
    skill_id        uuid not null references skill (id) on delete cascade,
    prerequisite_id uuid not null references skill (id) on delete cascade,
    primary key (skill_id, prerequisite_id),
    constraint skill_prerequisite_distinct_chk check (skill_id <> prerequisite_id)
);

create table lesson_skill (
    lesson_id uuid not null references lesson (id) on delete cascade,
    skill_id  uuid not null references skill (id) on delete cascade,
    primary key (lesson_id, skill_id)
);

create index lesson_skill_skill_idx on lesson_skill (skill_id);
