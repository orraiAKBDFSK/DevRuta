# Cómo revisar y publicar el contenido

Las 48 lecciones y 412 ejercicios están en **borrador**: los escribió una IA y **nadie los ha revisado**. La API en
producción solo importa lecciones `published` con `reviewed_by` no vacío, así que hasta que una persona las revise el
catálogo de producción estará vacío. Es intencional.

## Qué revisar (por lección)

1. **Corrección técnica.** ¿Todo lo que dice el texto es verdad en Python 3.13? Fíjate en las afirmaciones absolutas
   («siempre», «nunca») y en los ejemplos con salida.
2. **Nivel y orden.** ¿Usa algo que aún no se ha enseñado? Las habilidades (`skills.yaml`, campo `requires`) deben
   respetar el orden de las lecciones.
3. **Ejercicios.** ¿La pregunta tiene una única respuesta correcta? En opción múltiple, ¿algún distractor también es
   válido? En «predecir la salida», ¿el enunciado no es ambiguo? En los de escribir código, ¿los tests aceptan otras
   soluciones correctas y rechazan las incorrectas? (`wrong_examples` lo comprueba solo en parte.)
4. **Pistas y explicaciones.** De menos a más reveladora; la explicación no debe contradecir la respuesta.
5. **Idiomas.** El texto en español y en inglés dicen lo mismo. Las cadenas que el programa debe imprimir **no** se traducen.
6. **Tono y accesibilidad del texto.** Sin jerga innecesaria, sin referencias solo visuales («como se ve arriba»).

## Cómo ver qué falta

```bash
python scripts/review_report.py            # lecciones pendientes, con sus tipos de ejercicio
python scripts/validate_content.py         # 0 errores, siempre, antes de publicar
python scripts/verify_runner_content.py --url http://127.0.0.1:8090 --token <RUNNER_TOKEN>   # ejercicios de código, con el runner real
```

## Cómo publicar una lección

En el YAML de la lección, quien la revisó pone su nombre y cambia el estado:

```yaml
status: published
reviewed_by: [nombre-del-revisor]
```

Reglas: el revisor **no** debe ser quien escribió o modificó la lección (cuatro ojos); un cambio posterior en la
lección vuelve a pedir revisión. `validate_content.py` rechaza `published` sin `reviewed_by`.

## Orden sugerido

Empezar por la unidad 1 y avanzar en orden: cada unidad se apoya en las anteriores, y es donde un error confunde más.
Dentro de una lección, revisar primero los ejercicios de escribir código (los que más fallan si el test es estricto).
