# ADR 0002 — Estructura del monorepo y versiones

Estado: aceptada (revisable) · Fecha: 2026-10-23

## Contexto

Fase 3: crear el esqueleto del proyecto. Dos personas, un solo repositorio, poco presupuesto.
Las versiones se consultaron en fuentes oficiales el 2026-10-23 y pueden cambiar; se revisan con Dependabot.

## Hechos comprobados (con su fuente)

| Dato | Valor | Fuente |
|---|---|---|
| Spring Boot por defecto en Spring Initializr | 4.1.2 (también ofrece 4.0.9 y 4.2.0 RC1) | `start.spring.io/metadata/client` |
| Versiones de Java ofrecidas por Initializr | 17, 21, 25, 27 | `start.spring.io/metadata/client` |
| JDK instalado en la máquina de desarrollo | Temurin 25.0.2 (LTS) | `java -version` |
| Angular (`latest` en npm) | 22.2.4 (hay líneas LTS 21 y 20) | `npm view @angular/cli dist-tags` |
| Node requerido por Angular CLI 22.2 | `^22.22.3 \|\| ^24.15.0 \|\| >=26.0.0` | `npm view @angular/cli engines` |
| PostgreSQL: mayor más reciente | 18 (soportadas 14 a 18; 18 hasta 2030-11-14) | `postgresql.org/support/versioning` |
| Acciones de GitHub (última versión mayor) | checkout v7, setup-java v6, setup-node v7, setup-python v7 | API de releases de GitHub |

## Decisiones

1. **Monorepo** con `apps/{api,web,runner}`, `content`, `docs`, `infra`, `scripts`. Cambios atómicos entre API y web,
   un solo CI y una sola documentación. Se reconsidera si el equipo o los ciclos de despliegue se separan.
2. **API**: Spring Boot 4.1.2, Java 25, Maven con *wrapper*. Maven por sencillez y convención; el wrapper evita instalar nada.
3. **Web**: Angular 22.2 con TypeScript, SCSS y pruebas con el ejecutor integrado (Vitest).
4. **Base de datos**: PostgreSQL 18 en Docker Compose, en Testcontainers y en producción (misma versión mayor en todas partes).
5. **Todo bajo `/api`**: el servidor usa `context-path: /api`. En desarrollo, el proxy de Angular reenvía `/api`; en
   contenedores, nginx hace lo mismo. Frontend y API comparten origen, lo que habilita cookies `SameSite` y evita CORS.
6. **Credenciales**: ninguna en `application.yml`. Producción las recibe por variables de entorno
   (`SPRING_DATASOURCE_*`); el perfil `dev` trae valores solo válidos para la base local de Compose.
7. **Esquema**: solo Flyway (`ddl-auto: validate`). Migraciones inmutables.
8. **CI**: validar contenido, API y web en paralelo; luego comprobar que las imágenes Docker compilan.
9. **`apps/runner` solo reserva el lugar**: no se implementa nada hasta la fase 7.

## Verificación del esqueleto (2026-10-23)

| Comprobación | Resultado |
|---|---|
| `python scripts/validate_content.py` | Pasa: 48 lecciones, 412 ejercicios |
| Web: `npm test -- --no-watch` | Pasa: 3 pruebas |
| Web: `npm run build` | Compila (producción) |
| API: `mvnw verify` (compila y 3 pruebas de integración con Testcontainers + PostgreSQL 18) | Pasa: BUILD SUCCESS |
| API: arranque con perfil `dev` contra la base de Compose | Arranca; Flyway aplica `V1 baseline`; `/api/actuator/health` responde `UP` |
| Docker Compose: sintaxis (`docker compose config`) | Válida (perfil por defecto y `full`) |
| Docker Compose: `up -d db` | PostgreSQL 18.6 en estado `healthy` |
| Imágenes Docker de API (581 MB) y web (93,9 MB) | Se construyen |
| Perfil `full` (db + api + web) | Funciona: por nginx (8081) `/api/actuator/health` = `UP`, `/` sirve la SPA y una ruta de cliente inexistente cae en `index.html`; la API corre como `appuser` (no root) |
| Flujo de CI en GitHub Actions | **No ejecutado**: aún no hay repositorio remoto |

Hallazgo: en la máquina de desarrollo ya había un PostgreSQL local en el puerto 5432, que impedía publicar el de Docker.
Por eso el puerto del host es configurable (`POSTGRES_PORT`, en `infra/compose/.env`; la verificación usó 5433).

Aún no se ha probado: el CI en GitHub, ni las imágenes con escaneo de vulnerabilidades.

## Consecuencias

- Nombre del producto y *package* (`app.plataforma`) son provisionales. Renombrar ahora es barato; después, no.
- Spring Boot 4.x es una versión mayor reciente: algunos tutoriales y bibliotecas antiguas mencionan APIs de la 3.x.
  Se prefiere la documentación oficial.
- Angular 22 es la última versión mayor; si se prefiere una línea LTS, se puede fijar la 21 sin cambiar la arquitectura.
- Las imágenes de producción (`eclipse-temurin:25-jre`, `nginx:alpine`) se fijarán por *digest* antes de desplegar.
