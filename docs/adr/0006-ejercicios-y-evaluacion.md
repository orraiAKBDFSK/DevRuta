# ADR 0006: Ejercicios y evaluación automática (fase 6)

Estado: aceptado · Fecha: 2026-10-23

## Contexto

Las lecciones ya se importan al catálogo (ADR 0005). Faltaba que el estudiante pueda practicar y recibir corrección inmediata, sin ejecutar todavía código arbitrario (eso es la fase 7).

## Decisiones

1. **Ejercicios en base de datos, importados desde el YAML.** Tablas `exercise`, `exercise_translation`, `exercise_skill` (migración V8). `config` (lo que ve el estudiante) y `solution` (solo servidor) son JSONB. El hash de contenido de la lección incluye los ejercicios, así que el importador sigue siendo idempotente y archiva en vez de borrar.
2. **Intentos append-only + estado por usuario.** `exercise_attempt` guarda cada intento; `exercise_state` guarda intentos, pistas usadas y `solved_at` (migración V9, borrado en cascada con la cuenta).
3. **Evaluación estática en el servidor para cuatro tipos:**
   - `MULTIPLE_CHOICE`: índice de opción.
   - `PREDICT_OUTPUT`: texto normalizado (CRLF→LF, espacios finales por línea y líneas vacías de los extremos fuera; distingue mayúsculas).
   - `ORDER_LINES`: orden exacto, con sangría. Las líneas se sirven mezcladas con una semilla determinista y nunca ya resueltas.
   - `FILL_CODE`: cada hueco acepta cualquiera de las alternativas, recortadas.
   - `WRITE_CODE` responde `422 NOT_EVALUABLE_YET` hasta la fase 7.
4. **La solución y la explicación no se envían hasta resolver.** La explicación solo se revela al acertar. Pistas progresivas con `POST .../hints`.
5. **Límite de 60 peticiones/min por usuario** (`AttemptLimiter`, movido a `platform` para compartirlo) y límites de tamaño de respuesta (`INVALID_ANSWER`, 400).
6. **Frontera entre módulos.** `learning` solo usa `catalog` a través de su API pública (`ExerciseCatalog`); ArchUnit lo vigila.
7. **Frontend.** Componentes `ExerciseCard`/`ExerciseList` con señales, OnPush y accesibilidad (radiogroup, etiquetas por hueco, botones subir/bajar con nombre accesible en lugar de arrastrar, región `aria-live` para el resultado). Markdown con `marked` + sanitizador de Angular.

## Riesgos y límites conocidos

- **Los 142 ejercicios de escribir código no se pueden comprobar** hasta el motor aislado (fase 7).
- La comparación de `PREDICT_OUTPUT` es estricta: respuestas válidas con otro formato pueden marcarse mal. Se ajustará con datos reales.
- El limitador es en memoria: con más de una instancia de la API hay que moverlo a base de datos o Redis.
- No hay todavía XP, rachas ni logros: los intentos quedan registrados, pero no se puntúan (fase 8).
- Todo el contenido sigue en borrador y sin revisión humana.

## Verificación (2026-10-23, en local)

| Qué | Resultado |
|---|---|
| Pruebas de la API | 131 correctas, 0 fallos (incluye `RealExercisesTests`: cada ejercicio estático real acepta su solución y rechaza una respuesta errónea) |
| Pruebas de la web | 80 correctas, 0 fallos |
| `npm run build` | correcto |
| Validación de contenido | 48 lecciones, 412 ejercicios (67 opción múltiple, 161 predecir, 13 ordenar, 29 rellenar, 142 escribir), 0 errores |
| Compose completo | levanta; la respuesta de `GET /v1/lessons/{id}/exercises` no contiene `solution`; acierto, error (400), pista y `WRITE_CODE` (422) se comportan como se espera |
| Navegador | la lección `py.u01.l01` muestra los 6 ejercicios; resolver uno muestra «¡Correcto!», la explicación y el contador pasa a 1 de 5 |

No verificado: el CI de GitHub (nunca ejecutado), lectores de pantalla reales, y el resto de tipos de ejercicio probados a mano en el navegador (cubiertos solo por pruebas automáticas).
