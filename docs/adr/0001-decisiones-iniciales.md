# ADR 0001 — Decisiones iniciales

Estado: aceptada (revisable) · Fecha: 2026-10-23

## Decisiones

| Tema | Decisión |
|---|---|
| Plataforma | Solo web (Angular + TypeScript) |
| Backend | Monolito modular, Java + Spring Boot |
| Base de datos | PostgreSQL, migraciones con Flyway |
| Lenguajes de la plataforma | Python en el MVP; C++ en la primera versión estable |
| Idiomas de interfaz y contenido | Español e inglés desde el inicio |
| Público | Cualquier persona, desde cero (edad mínima: pendiente) |
| Contenido | Generado con IA, revisado por humanos, vive en el repo como archivos (`/content`) |
| Panel de administración | Fuera del MVP; el contenido se edita por pull request |
| Ejecución de código | Servicio aislado y separado; cada lenguaje es configuración, no código |
| Autenticación | Sesión de servidor con cookie HttpOnly + CSRF |
| Equipo | 2 ingenieros de software |

## Pendientes

- Hosting y presupuesto mensual.
- Runner: gVisor propio vs Judge0/Piston.
- Edad mínima y mercados objetivo (requiere asesoría legal).
- Nombre y dominio.
- Licencia del código y del contenido.

## Regla de contenido

Ningún ejercicio se publica si:
1. Su solución de referencia no pasa todos sus tests en el runner.
2. Sus respuestas incorrectas típicas (`wrong_examples`) no fallan.
3. No tiene revisión humana registrada (`reviewed_by`).
