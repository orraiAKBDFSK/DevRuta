# runner (ejecución aislada de código)

Servicio que ejecuta el código de los estudiantes y devuelve lo que imprimió. **No corrige nada**: la API compara las
salidas con las esperadas, que nunca salen del servidor. Decisiones y riesgos: `docs/adr/0007-ejecucion-aislada.md`.

```
API ──HTTP + Bearer──▶ runner ──docker run──▶ contenedor sandbox (uno por envío, se destruye al terminar)
```

## Qué garantiza cada contenedor sandbox

Red desactivada · sistema de archivos de solo lectura (salvo un `/tmp` de 32 MB en memoria) · usuario 65534 sin
capacidades (`--cap-drop ALL`, `no-new-privileges`) · 256 MB de memoria sin swap · 1 CPU · máximo 64 procesos ·
sin volúmenes ni socket de Docker · cada test corre en un subproceso con límite de CPU, memoria, tamaño de archivo y
descriptores, y con su propia carpeta temporal · salida limitada a 64 KB · tiempo por test y total acotados.

## Contrato

`POST /v1/run` con `Authorization: Bearer <RUNNER_TOKEN>`:

```json
{ "language": "python", "code": "print(input())",
  "cases": [{"stdin": "hola\n"}],
  "checks": [{"kind": "requires_function", "name": "es_par", "returns": true}],
  "case_timeout_ms": 3000, "total_timeout_ms": 10000 }
```

Responde `200` con `{status, checks:[{kind, ok}], cases:[{stdout, stderr, exit_code, timed_out, truncated, duration_ms}]}`;
`400` petición no válida, `401` token, `413` cuerpo grande, `503` ocupado (reintentar), `502` fallo del sandbox.
Límites: código 20 000 caracteres, 10 casos, 10 000 caracteres de stdin por caso. `GET /healthz` no necesita token.

## Variables de entorno

| Variable | Por defecto | Para qué |
|---|---|---|
| `RUNNER_TOKEN` | — (obligatoria, ≥ 16 caracteres) | secreto compartido con la API |
| `SANDBOX_IMAGE_PYTHON` | `plataforma-sandbox-python:local` | imagen del sandbox |
| `SANDBOX_RUNTIME` | (el de Docker) | p. ej. `runsc` para gVisor en producción |
| `RUNNER_MAX_CONCURRENT` | `4` | contenedores a la vez; el resto recibe `503` |
| `RUNNER_BIND` / `RUNNER_PORT` | `0.0.0.0` / `8090` | dónde escucha |

## Pruebas

```bash
docker build -t plataforma-sandbox-python:local -f sandbox/Dockerfile.python sandbox
python -m unittest discover -s tests -t . -v
```

`tests/test_sandbox.py` lanza código hostil de verdad (bucle infinito, fork bomb, bomba de memoria, red, escritura en
el sistema de archivos, salida infinita, disco lleno, falsificar el resultado...). Sin Docker o sin la imagen se omite.
Para pasar **todo el contenido** por el runner real: `scripts/verify_runner_content.py`.

## Límites conocidos

- Solo Python. C++ será otra imagen y otro valor de `language` con el mismo contrato.
- Sin gVisor en Docker Desktop: el aislamiento local es el de contenedores normales (runc). Ver el ADR.
- Mientras corre, el código del estudiante ve las entradas (no las salidas esperadas) de los otros tests del mismo envío.
