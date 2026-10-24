# Wireframes (texto) — MVP

Navegación principal / Main nav: **Ruta · Practicar · Progreso · Perfil**
(en móvil: barra inferior / on mobile: bottom bar).

## 1. Onboarding (3 pasos / 3 steps)

```
Paso 1/3  ¿Qué quieres aprender?        Step 1/3  What do you want to learn?
 ( ) Python (recomendado para empezar)
 ( ) C++ — próximamente / coming soon
Paso 2/3  ¿Cuánta experiencia tienes?   Step 2/3  How much experience?
 ( ) Ninguna  ( ) Algo  ( ) Ya programo
Paso 3/3  Meta diaria: [5 min] [10 min] [15 min]      Idioma / Language: [ES|EN]
                                         [Continuar]
```
"Ya programo" ofrece un test corto opcional para saltar unidades.

## 2. Mapa de ruta

```
┌ Python ────────────────  Racha 🔥 3   XP 240 ┐
│  Unidad 1 · Primeros pasos          ▓▓▓▓░ 2/4 │
│    (●) Mostrar texto        ✔                 │
│    (●) Variables            ✔                 │
│    (◐) Tipos básicos        ← continuar       │
│    (○) Repaso de unidad     🔒                │
│  Unidad 2 · Operaciones y entrada   🔒        │
└───────────────────────────────────────────────┘
```
Un nodo bloqueado explica *por qué* (qué falta) al tocarlo.

## 3. Lección y ejercicio

```
┌ ✕   ▓▓▓▓▓░░░ 3/6 ──────────────────────────┐
│ Predice la salida                           │
│ ┌ código ───────────┐                       │
│ │ x = 5             │   Tu respuesta:       │
│ │ print(x)          │   [ ____________ ]    │
│ └───────────────────┘                       │
│ [Pista 1/2]                      [Comprobar]│
└─────────────────────────────────────────────┘
```
Ejercicio de código: panel del editor + panel de resultados por test
(✔/✘, esperado vs obtenido en los tests visibles; los ocultos solo dicen "falló").

## 4. Resultado

```
 ✔ ¡Correcto!        +10 XP (sin pistas)
 Por qué: <explicación>              [Continuar]

 ✘ Todavía no.   <mensaje pedagógico según error_tag>
 [Ver pista]  [Reintentar]
```
Nunca se revela la solución antes de resolver o agotar las pistas.

## 5. Fin de lección

```
 Lección completada  ·  Precisión 5/6  ·  +45 XP
 Habilidad "Variables": ▓▓▓▓▓▓░░ 75 %
 Racha: 4 días 🔥          [Siguiente lección] [Volver al mapa]
```

## 6. Progreso

Habilidades con barra de dominio, próximas revisiones ("Repasa: variables"),
errores frecuentes ("Olvidas cerrar comillas") con botón *Practicar esto*,
calendario de actividad.

## Accesibilidad

Todo operable con teclado; `Esc` sale del editor; resultados en región `aria-live`;
estado nunca solo por color (✔/✘ + texto); modo claro/oscuro; objetivos táctiles ≥ 44 px.
