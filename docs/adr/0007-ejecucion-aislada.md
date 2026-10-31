# ADR 0007: Ejecución aislada de código (fase 7)

Estado: aceptado para desarrollo · **no apto para producción sin los pasos de la sección «Antes de producción»** ·
Fecha: 2026-10-23

## Contexto

142 de los 412 ejercicios piden escribir código. Corregirlos significa ejecutar código escrito por desconocidos, que
puede ser un bucle infinito, una fork bomb, un intento de leer secretos o de atacar la red interna. La API tiene la base
de datos y los secretos: ese código no puede ejecutarse nunca en su proceso.

## Decisiones

1. **Servicio aparte (`apps/runner`) y un contenedor por envío.** La API solo habla HTTP con el runner; el runner lanza
   un contenedor sandbox con `docker run`, le pasa el código por stdin (sin volúmenes, así no hay rutas del host de por
   medio) y lo destruye al terminar. Se descartó Judge0/Piston: traen su propio modelo de datos y de seguridad y
   habría que revisarlos igual; el contrato propio es pequeño (un endpoint) y auditable.
2. **El runner no corrige.** Devuelve salida, error, código de salida y si hubo timeout; la API compara con las salidas
   esperadas, que nunca salen del servidor. Las comprobaciones estáticas (`requires_function`, `requires_assignment`) se
   evalúan dentro del sandbox con el módulo `ast` de Python, así que no hace falta un intérprete de Python en la API.
3. **Un contenedor por envío, no por test.** Arrancar un contenedor cuesta ~0,3–1 s; un arnés dentro (`harness.py`)
   ejecuta cada test en un subproceso con su carpeta temporal, límites de CPU/memoria/archivo/descriptores y su propia
   tubería (el programa no puede escribir en el canal de resultados). El arnés es el PID 1 del contenedor: el kernel
   ignora un `SIGKILL` enviado desde dentro de su propio espacio de procesos.
4. **Falla cerrado.** Si el sandbox no arranca, se cae o devuelve algo ilegible, el runner responde `502`, la API
   responde `503 RUNNER_UNAVAILABLE` y **no cuenta el intento**. Nunca se interpreta un fallo como acierto ni como error
   del estudiante. Si está saturado: `503 RUNNER_BUSY` con `Retry-After: 5`.
5. **Autenticación y superficie.** El runner exige `Authorization: Bearer` (comparación en tiempo constante), no publica
   puertos en Compose, valida cada campo (lista cerrada de lenguajes y comprobaciones; ignora campos desconocidos como
   `image` o `privileged`), limita el cuerpo a 64 KB y no registra nunca el código. Se niega a arrancar sin token de 16+
   caracteres.
6. **Módulo `execution` en la API** con puerto `CodeExecutor`; `learning` solo ve «código + entradas → salidas» y ArchUnit
   impide usar `execution.internal`. Sin `app.runner.url` la API arranca igual y estos ejercicios quedan no evaluables.
7. **La API no retiene una conexión de base de datos mientras corre el código** (`submit` ya no es transaccional; solo
   el registro del intento lo es). Límite propio de 10 ejecuciones por minuto y usuario, además del general.
8. **Qué ve el estudiante.** Tests visibles: entrada, salida esperada y la suya. Tests ocultos: solo si pasaron. El
   error de su propio programa (sin rutas: `File "<string>"`) solo para tests visibles.

## Riesgos y límites conocidos

- **El runner monta el socket de Docker, que equivale a ser root en esa máquina.** Aceptable para desarrollo; en
  producción, jamás en el mismo host que la base de datos o la API (ver abajo). El código del estudiante no corre en el
  runner, pero un fallo del runner sí podría escalar.
- **Aislamiento de contenedores normales (runc).** Comparte kernel con el host: un fallo del kernel o de runc podría
  permitir escapar. Docker Desktop no ofrece gVisor, así que **no he podido probar gVisor** ni Firecracker; el runner
  admite `SANDBOX_RUNTIME=runsc` pero eso está sin verificar.
- El programa puede leer las entradas (no las salidas esperadas) de los demás tests del mismo envío, porque comparte
  contenedor con el arnés. Efecto: alguien podría ajustar su código a esas entradas; en una plataforma de aprendizaje sin
  ranking compite solo contra sí mismo. Si hubiera competición, habría que usar un contenedor por test.
- `--cpus 1` y 256 MB por contenedor, 4 a la vez, son valores de partida sin medir con carga real.
- Sin límite de ancho de banda ni de lectura de disco (la red está desactivada; el disco es el del contenedor).
- Solo Python. C++ (fase posterior) necesita compilar: otra imagen, mismos controles y atención a la memoria del compilador.
- Las pruebas de aislamiento cubren los ataques listados en `apps/runner/tests/test_sandbox.py`; **no sustituyen** una
  revisión de seguridad por alguien especializado.

## Antes de producción (pendiente)

1. Runner en una máquina o VM **dedicada**, sin acceso a la red interna, base de datos ni secretos; Docker sin root
   (rootless) o gVisor (`runsc`); token largo y aleatorio en un gestor de secretos, comunicación por red privada con TLS.
2. Perfil seccomp/AppArmor propio y estrecho (hoy se usa el de Docker por defecto).
3. Cuota de disco y de tiempo de CPU por usuario; alertas de saturación y de errores `502`.
4. Revisión de seguridad externa y una prueba de carga.

## Verificación (2026-10-23, en local)

| Qué | Resultado |
|---|---|
| Runner: protocolo, servidor y aislamiento (`python -m unittest`) | 27 pruebas correctas: bucle infinito, fork bomb, 2 GB de memoria, red y DNS, escritura en `/etc`, `/opt` y `/usr`, salida infinita, disco lleno, falsificar el resultado, matar al arnés, usuario 65534 sin capacidades, sin socket de Docker ni variables del runner, sin contenedores huérfanos |
| API | 158 pruebas correctas en total (`mvnw clean test`), entre ellas `CodeEvaluation` (10), `RunnerClient` contra un runner simulado (7), 9 casos HTTP de escribir código y reglas de arquitectura (12) |
| Web | 86 pruebas correctas; `npm run build` correcto |
| **Contenido real por el runner real** (`scripts/verify_runner_content.py`) | 142 ejercicios: todas las soluciones de referencia pasan y los 200 ejemplos erróneos fallan |
| Navegador (Compose completo) | en `py.u01.l01.e05`: respuesta errónea → «no superada» con salida esperada y obtenida; bucle infinito → «tu programa tardó demasiado»; `print(x)` → traceback propio; solución correcta → «¡Correcto!»; contador 1 de 6 |
| Runner parado | la API responde `503 RUNNER_UNAVAILABLE` y no registra el intento |

El CI de GitHub se ejecutó después (2026-10-24) y pasó. No verificado: gVisor, carga concurrente real, lectores de pantalla, ni cómo se
comporta en Linux nativo con cgroups v1 (solo he probado Docker Desktop).
