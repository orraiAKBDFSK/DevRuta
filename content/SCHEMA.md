# Formato de contenido

```
content/<lenguaje>/
  course.yaml            el curso (títulos y descripción en es/en)
  skills.yaml            habilidades y sus prerrequisitos (grafo sin ciclos)
  <unidad>/unit.yaml     la unidad (id, posición, título y resumen en es/en)
  <unidad>/<NN>-<slug>.yaml   una lección con sus ejercicios
```

## Curso, habilidades y unidades

```yaml
# course.yaml
id: py.course
language: python          # código del lenguaje (único)
language_name: Python
position: 1
title: {es: ..., en: ...}
description: {es: ..., en: ...}

# skills.yaml
skills:
  - id: py.variables
    name: {es: Variables, en: Variables}
    requires: [py.output]   # prerrequisitos; no puede haber ciclos

# <unidad>/unit.yaml
id: py.u01
position: 1               # 1..n sin huecos
title: {es: ..., en: ...}
summary: {es: ..., en: ...}
```

Cada lección declara su unidad (`unit: py.u01`) y su posición dentro de ella (1..n sin huecos). Toda habilidad usada
en una lección o en un ejercicio debe existir en `skills.yaml`. El validador (`scripts/validate_content.py`) y el
importador de la API aplican estas reglas.

## Lecciones

Un archivo YAML por lección: `content/<lenguaje>/<unidad>/<NN>-<slug>.yaml`.

```yaml
id: py.u01.l01            # estable; nunca se reutiliza
language: python
unit: py.u01
position: 1
skills: [py.output]
status: draft             # draft | reviewed | published
reviewed_by: []           # humanos que revisaron; vacío = no publicable
title: {es: ..., en: ...}
explanation: {es: |, en: |}   # Markdown
exercises:
  - id: py.u01.l01.e01    # estable
    type: multiple_choice # ver tipos abajo
    difficulty: 1         # 1..5
    skills: [py.output]
    prompt: {es: ..., en: ...}
    # campos según tipo (ver abajo)
    hints: [{es: ..., en: ...}]      # de menos a más reveladora
    explanation: {es: ..., en: ...}  # se muestra tras resolver
```

## Tipos y campos

| type | campos |
|---|---|
| `multiple_choice` | `options: [{es,en}]`, `answer: <índice 0-based>`, opcional `distractor_tags: {<índice>: <error_tag>}` |
| `predict_output` | `code: |`, `answer: "<stdout exacto>"` (se compara tras recortar espacios finales) |
| `order_lines` | `lines: [...]` en el **orden correcto**; la plataforma las baraja |
| `fill_code` | `code: |` con `___` como huecos, `blanks: [["print"], ...]` (alternativas aceptadas por hueco) |
| `write_code` | `starter: |`, `reference_solution: |`, `tests: [{stdin, expected_stdout, visible}]`, `wrong_examples: [{code, expect_fail_tag}]` |

`write_code` admite además `static_checks` (comprobaciones sobre el código, no sobre la salida),
p. ej. `{kind: requires_assignment, count_min: 1}` para exigir que se use una variable.

Otra comprobación estática: `{kind: requires_function, name: es_par, returns: true}` exige que el
código defina esa función y, con `returns: true`, que contenga un `return` con valor.

## Convenciones

- Los textos que el programa del estudiante debe producir (p. ej. `Hola, mundo`) **no se traducen**: se citan igual en ambos idiomas, porque los tests comparan la salida exacta.
- Entrecomilla (`'...'`) todo texto en línea que contenga `:`, `,` o `#`; un YAML mal formado rompe la carga de la lección.

## Reglas de validación (CI)

- IDs únicos y estables. Ambos idiomas presentes en todo texto visible.
- `write_code`: la `reference_solution` pasa **todos** los tests y cada `wrong_examples` falla.
- `predict_output`, `order_lines` y `fill_code`: el CI ejecuta el código resultante y comprueba que coincide con la respuesta declarada.
- `status: published` exige `reviewed_by` no vacío.
