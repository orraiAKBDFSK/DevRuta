-- Módulo profile: perfil y preferencias del estudiante. Se borra junto con la cuenta.
create table user_profile (
    user_id            uuid primary key references app_user (id) on delete cascade,
    display_name       varchar(50) not null,
    locale             varchar(5)  not null default 'es',
    experience_level   varchar(20) not null default 'BEGINNER',
    goal               varchar(30),
    daily_goal_minutes smallint    not null default 10,
    timezone           varchar(64) not null default 'UTC',
    created_at         timestamptz not null default now(),
    updated_at         timestamptz not null default now(),
    constraint user_profile_locale_chk check (locale in ('es', 'en')),
    constraint user_profile_level_chk check (experience_level in ('BEGINNER', 'SOME', 'EXPERIENCED')),
    constraint user_profile_goal_chk check (goal is null or goal in ('CAREER', 'STUDIES', 'HOBBY', 'PROJECT')),
    constraint user_profile_daily_goal_chk check (daily_goal_minutes between 5 and 120)
);
