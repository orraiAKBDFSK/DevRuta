# ADR 0005 — Catálogo de contenido e importador

Estado: aceptada (revisable) · Fecha: 2026-10-23

## Alcance de la fase 5

Rutas (cursos), unidades, lecciones y grafo de habilidades: modelo de datos, importador desde `content/`, API de lectura y
pantallas (cursos, mapa de aprendizaje, lección). **Fuera de alcance:** ejercicios (fase 6), progreso, desbloqueo y XP
(fase 8). Las lecciones muestran un aviso de que los ejercicios llegan pronto.

## Decisiones

| Tema | Decisión | Motivo |
|---|---|---|
| Fuente de verdad | Archivos YAML del repositorio (`content/`), revisados por *pull request* | El contenido se versiona y revisa como el código; sin panel de administración por ahora |
| Estructura | `course.yaml`, `skills.yaml`, `unit.yaml` por unidad y un archivo por lección (ver `content/SCHEMA.md`) | Cada pieza tiene identificador estable (`py.u01.l01`) |
| Importación | Idempotente y por *upsert* sobre `content_id`; una huella SHA-256 evita reescribir lecciones sin cambios | Se puede ejecutar cuantas veces haga falta |
| Atomicidad | Se valida todo antes de escribir y se acumulan todos los problemas; si hay alguno no se escribe nada y la importación falla | Nunca queda un catálogo a medias |
| Nada se borra | Lo que desaparece del contenido, o ya no alcanza el estado mínimo, se **archiva** (`ARCHIVED` / `archived`) | El progreso de los estudiantes (fase 8) apuntará a estas filas |
| Estado mínimo | `app.content.min-status`: `PUBLISHED` por defecto (producción) y `DRAFT` en desarrollo. Una lección `published` exige `reviewed_by` | Garantiza que nada sin revisión humana llegue a los estudiantes. Hoy todo es borrador: en producción el catálogo estaría vacío |
| Traducciones | Tablas `*_translation` (una fila por idioma, `es` y `en` obligatorios). La API responde con `Accept-Language` y cae a español si falta un texto; las respuestas llevan `Vary: Accept-Language` | Mismo modelo para todo el contenido traducible |
| Habilidades | Grafo de prerrequisitos que el importador comprueba que no tenga ciclos | Base del desbloqueo de la fase 8 |
| Identificadores públicos | La API usa los `content_id`, no los UUID internos | Legibles, estables entre entornos |
| Acceso | Todo el catálogo exige sesión | Evita el rastreo masivo del contenido; se puede abrir más adelante |
| Texto de las lecciones | Markdown. La web lo convierte con `marked` (versión fija) y Angular **sanea** el HTML al insertarlo | Aunque el contenido es del repositorio, se protege de HTML peligroso |
| Lecturas y escrituras | `JdbcClient` con SQL explícito en vez de entidades JPA | Upserts y consultas con idioma y respaldo son más claros en SQL |
| Entrega del contenido a producción | **Sin decidir.** En desarrollo y en el Compose completo se monta `content/` como volumen de solo lectura | Opciones: copiarlo a la imagen, un volumen o un comando de importación en el despliegue |

## Validación en dos capas

- `scripts/validate_content.py` (CI): estructura **y ejecuta** las soluciones de los ejercicios.
- El importador de la API: estructura del catálogo (el mismo conjunto de reglas, salvo los ejercicios).
- Una prueba de integración importa el contenido real del repositorio en cada ejecución del CI.

## Riesgos y límites conocidos

- **Los ejercicios todavía no se importan**: siguen en los YAML y se validan con Python.
- **Cambios de posición o de unidad** de una lección cuentan como actualización de la misma fila (conserva su id).
- **Contenido muy grande**: la importación escribe lección por lección dentro de una sola transacción. Para cientos de
  lecciones es suficiente; con miles habría que revisarla.
- **Sin caché HTTP**: cada visita consulta la base de datos. El catálogo cambia solo al importar, así que se puede cachear después.
- **Dependencias de desarrollo con avisos** (`npm audit`): ver la sección de verificación.

## Verificación (2026-10-23)

| Comprobación | Resultado |
|---|---|
| `python scripts/validate_content.py` con las reglas nuevas (curso, habilidades, unidades, posiciones, ciclos) | Pasa: 48 lecciones, 412 ejercicios. El detector de ciclos se probó con casos positivos y negativos |
| API: `mvnw verify` | Pasa: 84 pruebas. Incluye 16 del cargador (cada regla de validación), 11 del importador contra PostgreSQL real (idempotencia, edición, archivado, restauración, estado mínimo, atomicidad, mover de unidad, prerrequisitos), 7 de la API HTTP y 2 que importan el **contenido real** del repositorio |
| Web: `npm test`, `npm run build` | Pasan: 57 pruebas (incluye saneado de HTML peligroso en Markdown y el cambio de lección sin recargar) |
| Stack completo en contenedores | Al arrancar importa: 1 curso, 10 unidades, 31 habilidades, 48 lecciones nuevas |
| Navegador (con la cuenta de prueba, ya borrada) | Funciona: cursos con contadores, mapa de 10 unidades y 48 lecciones con insignia de borrador y habilidades, lección con tabla y bloque de código, anterior/siguiente sin recargar, cambio de idioma que traduce título, texto, migas y habilidades, mensaje de lección inexistente |
| `npm audit --omit=dev` | 0 vulnerabilidades en lo que se publica |
| `npm audit` completo | 2 avisos en dependencias **de desarrollo** (`vite` dentro de `@angular/build` y `launch-editor`): afectan al servidor de desarrollo, no al código publicado. Pendiente de actualizar cuando Angular publique una versión que los corrija |
| CI de GitHub Actions | **No ejecutado** (no hay repositorio remoto) |
