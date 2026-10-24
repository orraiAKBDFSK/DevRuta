-- Migración inicial: marca el punto de partida del esquema.
-- Las tablas de identidad llegan en la fase 4; cada cambio posterior será una nueva migración V<n>__<descripcion>.sql.
-- Regla: una migración ya aplicada en cualquier entorno compartido nunca se edita.
select 1;
