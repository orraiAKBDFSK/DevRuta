# Plataforma de aprendizaje de programación

Plataforma web para aprender programación con lecciones cortas, ejercicios con corrección automática,
pistas y progreso visible. Empieza con **Python** y se amplía a C++ después.

> Nombre del producto: pendiente (`plataforma` es un nombre provisional; ver `docs/adr`).

## Estado

| Parte | Estado |
|---|---|
| Contenido de Python (10 unidades) | Escrito en borrador, **sin revisión humana** |
| Esqueleto del monorepo (API, web, Compose) | Fase 3: creado y verificado en local (ver `docs/adr/0002-estructura-y-versiones.md`) |
| CI de GitHub Actions | **Ejecutado en GitHub y en verde** (2026-10-24, commit `2f948e4`): contenido, runner con los 142 ejercicios de código, web, API e imágenes Docker. Repositorio: https://github.com/orraiAKBDFSK/DevRuta |
| Autenticación, perfiles y base de datos de usuarios | Fase 4: hecha y verificada en local (ver `docs/adr/0003-autenticacion-y-perfiles.md`) |
| Catálogo de rutas, unidades y lecciones + importador de contenido | Fase 5: hecha y verificada en local (ver `docs/adr/0005-catalogo-e-importador-de-contenido.md`). Los ejercicios llegan en la fase 6 |
| Ejercicios básicos y evaluación automática | Fase 6: hecha y verificada en local (ver `docs/adr/0006-ejercicios-y-evaluacion.md`). Se evalúan 270 de 412 ejercicios; los 142 de «escribir código» esperan a la fase 7 |
| Verificación de correo y recuperación de contraseña | Hecha y verificada en local con Mailpit (ver `docs/adr/0004-correo-verificacion-y-recuperacion.md`). **Falta un proveedor real con dominio propio y los textos legales antes de abrir el registro al público** |
| C++ como segundo lenguaje | Unidad de muestra en borrador (2 lecciones) y runner con compilador, verificados en local (ver `docs/adr/0009-cpp-segundo-lenguaje.md`). **Falta escribir el resto del curso** |
| Progreso, XP, rachas y logros | Fase 8: hecha y verificada en local (ver `docs/adr/0008-progreso-xp-rachas-logros.md`). Las rachas usan la zona horaria del navegador |
| Ejecución aislada de código (`apps/runner`) | Fase 7: hecha y verificada en local con los 142 ejercicios de escribir código (ver `docs/adr/0007-ejecucion-aislada.md`). **No apta para producción**: falta máquina dedicada, gVisor/Docker sin root y revisión de seguridad |

## Estructura

```
apps/
  api/        Spring Boot (Java 25, Maven) — monolito modular, expone /api
  web/        Angular 22 (TypeScript) — interfaz web
  runner/     (reservado) servicio de ejecución aislada de código
content/      Lecciones y ejercicios como archivos YAML (ver content/SCHEMA.md)
docs/         Decisiones (ADR), wireframes
infra/        Docker Compose y, más adelante, despliegue
scripts/      Utilidades de desarrollo (validador de contenido)
.github/      CI, Dependabot y plantilla de pull request
```

## Requisitos

Versiones con las que se creó y probó el esqueleto: JDK 25, Node 24, npm 11, Docker 29, Python 3.13.
No hace falta instalar Maven: el proyecto incluye el *wrapper* (`mvnw`).

## Empezar en local

```bash
# 1) Base de datos (PostgreSQL 18) y correo falso (Mailpit: los correos que envía la aplicación,
#    como verificación y recuperación de contraseña, no salen a Internet; se leen en http://localhost:8025).
#    Si ya tienes PostgreSQL en el puerto 5432, copia infra/compose/.env.example a infra/compose/.env,
#    cambia POSTGRES_PORT (p. ej. 5433) y exporta la misma variable antes de arrancar la API.
docker compose -f infra/compose/docker-compose.yml up -d db mailpit

# 2) API en http://localhost:8080/api  (perfil "dev": conecta a la base de datos local e importa al arrancar
#    el contenido de content/, incluidas las lecciones en borrador; en producción solo las "published" con revisor)
cd apps/api
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev      # en Windows: .\mvnw.cmd ...

# 3) Web en http://localhost:4200  (reenvía /api al puerto 8080)
cd apps/web
npm ci
npm start
```

Prueba de integración completa en contenedores (PostgreSQL + API + web en http://localhost:8081):

```bash
docker compose -f infra/compose/docker-compose.yml --profile full up --build
```

## Pruebas

```bash
cd apps/api && ./mvnw verify                 # usa Testcontainers: necesita Docker en marcha
cd apps/web && npm test -- --no-watch
python -m pip install -r scripts/requirements.txt
python scripts/validate_content.py           # valida lecciones y ejercicios
```

## Documentación

- Cómo contribuir: [CONTRIBUTING.md](CONTRIBUTING.md)
- Decisiones técnicas: [docs/adr](docs/adr)
- Formato del contenido: [content/SCHEMA.md](content/SCHEMA.md)

## Guías para lo que falta

- `docs/revision-de-contenido.md`: cómo revisar y publicar lecciones (`scripts/review_report.py` lista lo pendiente).
- `docs/prueba-con-lector-de-pantalla.md`: qué probar con NVDA o VoiceOver.
- `docs/despliegue.md`: lista de comprobación antes de abrir al público (CI, correo, runner, legal).
