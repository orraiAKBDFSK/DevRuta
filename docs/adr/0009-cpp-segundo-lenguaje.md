# ADR 0009: C++ como segundo lenguaje

Estado: aceptado (solo una unidad de muestra) · Fecha: 2026-10-30

## Contexto

El plan original es Python primero y C++ después. El contrato del runner ya era «lenguaje + código + entradas», así que C++
se añade sin cambiar la API pública.

## Decisiones

1. **Mismo contrato, otra imagen.** El runner acepta `language: "cpp"` y lanza `plataforma-sandbox-cpp` (Python 3.13 + `g++`,
   unos 540 MB). Reutiliza el mismo arnés: compila una vez y ejecuta cada test del ejecutable resultante.
2. **Compilación dentro del sandbox**, con las mismas barreras (sin red, usuario sin privilegios, sistema de archivos de solo
   lectura) y límites propios: 15 s de CPU y 700 MB para el compilador, y un contenedor de 768 MB con un `/tmp` de 64 MB
   que **sí permite ejecutar** (el binario vive ahí; en Python `/tmp` no es ejecutable).
3. **Errores de compilación** se devuelven para cada test con `compile_error: true`; la API los etiqueta
   `code.compile_error` y muestra el mensaje del compilador **aunque el test sea oculto** (es un error del propio código).
   Las rutas internas se eliminan del mensaje.
4. **El lenguaje lo decide el curso.** `ExerciseDefinition` lleva el código de lenguaje del curso (`python`, `cpp`) y se envía tal
   cual al runner; un código desconocido lo rechaza el runner (400), que la API muestra como «no disponible».
5. **Sin comprobaciones estáticas en C++** (`requires_function`, `requires_assignment` usan `ast` de Python); el protocolo las
   descarta y el contenido de C++ se apoya en los tests de entrada y salida.
6. **El verificador de contenido es consciente del lenguaje.** `validate_content.py` solo ejecuta Python en local;
   `verify_runner_content.py` pasa por el runner real los ejercicios de código de todos los lenguajes y, en C++, también los
   de «predecir la salida» y «completar código» (el código del enunciado debe imprimir la respuesta declarada y el completado debe compilar).
7. **Endurecimiento descubierto al probar:** una fork bomb de C++ agotaba los procesos del contenedor y mataba al propio arnés
   (fallo cerrado, pero molesto). Ahora el programa tiene `RLIMIT_NPROC=40` (por debajo de los 64 del contenedor) y el arnés
   trata un fallo al crear hilos como fallo del programa. También afecta a Python.

## Contenido

Solo hay una unidad de muestra en **borrador** (`content/cpp/`): «Primeros pasos», 2 lecciones y 11 ejercicios (mostrar texto,
variables y tipos, leer datos). No es un curso completo ni está revisado.

## Riesgos y límites conocidos

- Todo lo dicho en el ADR 0007 sigue valiendo (sin gVisor probado, runc comparte kernel...), con más superficie: **un compilador
  procesando código hostil** es una superficie mayor que un intérprete. Hay pruebas de bomba de compilación (`constexpr`),
  `#include` de archivos del sistema y bomba de memoria, pero no sustituyen una revisión de seguridad.
- Las imágenes son grandes (C++ ~540 MB) y el primer arranque de cada envío es más lento (compilar: del orden de 1 s).
- Sin sanitizadores (`-fsanitize`), así que un programa con comportamiento indefinido puede dar resultados distintos entre ejecuciones.
- `-O1 -Wall` y `-std=c++20` están fijados; el contenido debe ser compatible.

## Verificación (2026-10-30, en local)

| Qué | Resultado |
|---|---|
| Runner (Python + C++, protocolo, servidor, aislamiento) | 42 pruebas correctas (3 ejecuciones seguidas estables tras corregir la fork bomb) |
| Contenido real por el runner (`scripts/verify_runner_content.py`) | 150 ejercicios y 205 ejemplos erróneos: 0 problemas (142 de Python + 8 de C++) |
| Validación de contenido | 50 lecciones, 423 ejercicios, 0 errores |
| API | 181 pruebas correctas, 0 fallos |
| Navegador (Compose completo) | en `cpp.u01.l02`: error de compilación con su mensaje, salida incorrecta con entrada/esperada/obtenida y solución correcta con +20 XP |
