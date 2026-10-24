# ADR 0003 — Autenticación, perfiles y base de datos de usuarios

Estado: aceptada (revisable) · Fecha: 2026-10-23

## Alcance de la fase 4

Registro con correo y contraseña, inicio y cierre de sesión, perfil y preferencias, cambio de contraseña y borrado de cuenta.
Pantallas de Angular: entrar, registrarse, inicio (provisional) y perfil.

**Fuera de alcance (pendiente de decisión):** doble factor (2FA), inicio de sesión con terceros (Google, GitHub), roles de
administración. La verificación del correo y la recuperación de contraseña se hicieron después: ver el ADR 0004.
Antes de abrir el registro al público faltan además el proveedor de correo real con dominio autenticado y los textos legales.

## Decisiones

| Tema | Decisión | Motivo |
|---|---|---|
| Sesión | Sesión de servidor en PostgreSQL (Spring Session JDBC), cookie `SESSION` `HttpOnly`, `SameSite=Lax`, `Secure` en producción | Se puede revocar al instante, no hay tokens en el navegador que robar con XSS, no hace falta Redis |
| CSRF | Doble envío de cookie: `XSRF-TOKEN` (legible) → cabecera `X-XSRF-TOKEN`. Angular lo hace sin código. Token nuevo tras iniciar sesión | Protege las peticiones que cambian datos, incluido el login |
| Contraseñas | Argon2id con 19 MiB de memoria, 2 iteraciones, paralelismo 1 (mínimos de la hoja de OWASP, consultada el 2026-10-23) | Resistente a hardware especializado; los parámetros viajan en el hash y se pueden subir |
| Política | Entre 10 y 128 caracteres y distinta del correo | Prioriza la longitud sobre reglas de composición |
| Fuerza bruta | 5 fallos por correo en 15 minutos → 429 con `Retry-After`; mismo límite para las acciones que piden la contraseña | Frena el abuso sin bloquear cuentas |
| Enumeración de cuentas | Login con mensaje y tiempo iguales para correo inexistente y contraseña errónea (compara contra un hash de relleno) | Evita revelar qué correos existen |
| Auditoría | Tabla `audit_event` (tipo, fecha, usuario). Sin IP, sin correo, sin contraseñas. Al borrar la cuenta se anonimiza | Mínimo de datos |
| Borrado de cuenta | Pide la contraseña; borra usuario y perfil (`on delete cascade`), cierra todas sus sesiones y anonimiza la auditoría | Derecho de supresión |
| Identificadores | UUID versión 7 | Únicos, no adivinables en secuencia y ordenables por fecha |
| Errores | RFC 9457 (`application/problem+json`) con `code` estable; el frontend traduce el código | Mensajes en español e inglés sin tocar el backend |
| Límites de módulo | `identity` y `profile` solo exponen su paquete raíz; `internal` es privado. ArchUnit lo comprueba en cada compilación. `profile` crea el perfil al recibir el evento `UserRegistered` (misma transacción) | Mantiene el monolito modular |
| Todo bajo `/api` | El API y la web comparten origen | Permite cookies `SameSite` y evita CORS |
| Textos de la interfaz | Diccionarios `es`/`en` propios y un servicio `t(clave)` (sin biblioteca) | Pocas cadenas; se reevalúa Transloco cuando crezca |

## Riesgos y límites conocidos

- **Registro revela correos existentes** (`409 EMAIL_ALREADY_REGISTERED`). Es la contrapartida habitual sin verificación por correo.
- **Sin verificación de correo**: cualquiera puede registrar un correo ajeno. Tampoco hay recuperación de contraseña.
- **Limitador en memoria**: solo vale con una instancia del API; con varias hay que moverlo a la base de datos o a un proxy.
- **Sin límite absoluto de sesión**: solo caduca por inactividad (7 días).
- **Atributos de sesión serializados con Java** y guardados en la base de datos: solo es seguro mientras nadie ajeno pueda escribir en esa tabla.
- **Contraseñas filtradas**: no se comprueban contra listas conocidas (por ejemplo Have I Been Pwned).
- **`Secure` de la cookie**: está apagado por defecto para poder desarrollar con `http`. En producción debe fijarse
  `SESSION_COOKIE_SECURE=true`, y si hay un proxy delante, que reenvíe `X-Forwarded-Proto`.
- **Términos, política de privacidad y edad mínima** son un marcador (`2026-10-borrador`): falta el texto definitivo y su revisión legal.

## Verificación (2026-10-23)

| Comprobación | Resultado |
|---|---|
| API: `mvnw verify` | Pasa: 25 pruebas contra PostgreSQL 18 real (registro, sesión, CSRF, límite de intentos, perfil, cambio de contraseña, borrado de cuenta, límites de módulo con ArchUnit, política de contraseñas, limitador) |
| Web: `npm test` y `npm run build` | Pasan: 24 pruebas; compila en producción |
| Migraciones V2 a V5 sobre una base que ya tenía la V1 | Se aplican sin error (comprobado en el arranque del contenedor) |
| Flujo en navegador a través de nginx (puerto 8081): registrarse, recargar, editar perfil, cambiar idioma, cerrar sesión, error de login, borrar cuenta | Funciona. La cookie `SESSION` no es visible para JavaScript; el token CSRF se rota al entrar; tras borrar la cuenta quedan 0 usuarios, 0 perfiles y los eventos de auditoría con `user_id` nulo |
| CI de GitHub Actions | **No ejecutado** (no hay repositorio remoto) |

La prueba en navegador se hizo con una cuenta de prueba creada y borrada en la base de desarrollo local; el navegador del
panel no expone el árbol de accesibilidad, así que los formularios se manejaron con JavaScript y no con clics reales.
No se ha hecho una revisión de accesibilidad con lector de pantalla ni pruebas con Playwright (previstas para la fase 9).

### Hallazgo corregido durante la verificación

Las peticiones anónimas rechazadas con 401 guardaban la petición original en una sesión (`SPRING_SECURITY_SAVED_REQUEST`), lo que creaba
una fila en `spring_session` por visitante. Se desactivó la caché de peticiones y hay una prueba que lo impide
(`anonymousVisitorsDoNotCreateSessions`). Comprobado también en el stack de contenedores: 5 peticiones anónimas no crean filas.
Hasta la corrección existía un riesgo de crecimiento de la tabla por cualquiera que hiciera peticiones sin sesión.
