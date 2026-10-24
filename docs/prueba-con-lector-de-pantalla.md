# Guía de prueba con lector de pantalla

Lo automático (`axe-core` en `apps/web/src/app/shared/accessibility.spec.ts`) comprueba etiquetas, nombres, roles y
estructura, **pero no el contraste de color ni cómo suena**. Esta guía es para una persona con **NVDA + Firefox/Chrome**
(Windows) o **VoiceOver + Safari** (macOS). Anota cada fallo con la pantalla, el paso y lo que se oyó.

## Antes de empezar

Levanta la app (`docker compose -f infra/compose/docker-compose.yml --profile full up -d --build`), abre
`http://127.0.0.1:8081`, crea una cuenta de prueba y abre la lección `py.u01.l01`. Prueba **solo con teclado** (Tab,
Mayús+Tab, flechas, Espacio, Intro): si no se puede con teclado, tampoco con lector.

## Qué comprobar

| Pantalla | Debe cumplirse |
|---|---|
| Todas | «Saltar al contenido» aparece con Tab y funciona; el idioma de la página es el correcto; cambiar de ES a EN cambia lo que lee |
| Registro / entrada | cada campo se anuncia con su etiqueta; los errores se anuncian al enviar sin mover el foco a un sitio inesperado |
| Lección | los encabezados permiten navegar (H2 título, H3 secciones); la tabla de la lección se lee por celdas |
| Opción múltiple | se anuncia como grupo de opciones con la pregunta; las flechas cambian de opción |
| Predecir salida | el campo se anuncia como «Escribe lo que muestra el programa» |
| Ordenar líneas | cada botón se anuncia como «Subir la línea 2»; tras mover una línea, ¿se entiende dónde quedó? (**posible punto débil**: no hay aviso del nuevo orden) |
| Rellenar huecos | cada hueco se anuncia como «Hueco 1», «Hueco 2» dentro del código |
| Escribir código | el editor se anuncia como «Tu código»; el resultado de cada prueba se lee («Prueba 1: ✗ no superada», salida esperada y obtenida) |
| Comprobar | al pulsar, se anuncia «¡Correcto!» o «Todavía no» sin perder el foco; también +XP y logros |
| Progreso | la barra anuncia su valor; los logros bloqueados se distinguen de los desbloqueados **sin depender del color** |

## Además (sin lector)

- **Contraste:** inspeccionar con las herramientas del navegador o con axe DevTools, en tema claro y oscuro (mínimo 4,5:1).
- **Zoom al 200 % y 400 %** sin perder contenido ni barras de desplazamiento horizontales.
- **Reducir movimiento / alto contraste** del sistema.
- Ningún elemento depende solo del color (los fallos llevan texto «✗» además del borde rojo).

Cuando haya resultados, abre un issue por fallo con la etiqueta `accesibilidad`.
