# ADR 0004 — Correo: verificación de cuenta y recuperación de contraseña

Estado: aceptada (revisable) · Fecha: 2026-10-23 · Complementa el ADR 0003

## Decisiones

| Tema | Decisión | Motivo |
|---|---|---|
| Proveedor | Ninguno fijado en el código: SMTP estándar (`spring.mail.*`). Recomendación inicial: Resend; alternativas SES o Scaleway (comparación del 2026-10-23, ver más abajo) | Cambiar de proveedor es solo configuración |
| Puerto | `EmailSender` (paquete `platform.email`) con una implementación `SmtpEmailSender` | Los módulos no conocen SMTP |
| Desarrollo y pruebas | Mailpit (SMTP falso con web en `localhost:8025`) en el Compose y en las pruebas de integración | Se ven los correos sin enviar nada a nadie |
| Módulos | `identity` solo publica eventos (`EmailVerificationRequested`, `PasswordResetRequested`); el módulo nuevo `notifications` los escucha, consulta el idioma a `profile` y redacta el correo | `identity` no conoce el correo ni el perfil; ArchUnit lo hace cumplir |
| Envío | Después de confirmar la transacción y en un hilo aparte | Si la transacción se deshace no sale correo; el tiempo de respuesta no delata si el correo existe |
| Tokens | 256 bits aleatorios; en la base de datos solo el hash SHA-256; de un solo uso (actualización condicional atómica); un propósito por token | Quien lea la base de datos no puede usarlos; dos peticiones simultáneas no gastan el mismo token |
| Caducidad | Verificación 24 h; recuperación 30 min | Configurables (`app.security.tokens.*`) |
| Errores de token | Mismo error (`INVALID_TOKEN`) para inexistente, caducado, usado o de otro propósito | No revela qué ocurrió |
| Recuperación | `POST /v1/auth/forgot-password` responde siempre 202. Límite de 3 correos por hora y por dirección, que se aplica sin avisar | No revela qué correos existen |
| Reenvío de verificación | Límite de 3 envíos por hora y por cuenta (el registro cuenta como uno) | Evita usar la plataforma para enviar correo en masa |
| Al restablecer | Comprueba la política antes de gastar el token; marca el correo como verificado; cierra todas las sesiones | Recibir el enlace demuestra que el correo es suyo |
| Al cambiar la contraseña | Invalida los enlaces de recuperación pendientes | Un enlace pedido antes del cambio no debe servir |
| Enlaces | `app.public-base-url` + `/verificar-correo?token=…` y `/restablecer?token=…`. La web quita el token de la dirección al abrirla y se envía `Referrer-Policy: no-referrer` | El token no queda en el historial ni sale en la cabecera Referer |
| Verificado | Pueden iniciar sesión y usar la plataforma sin verificar; la web muestra un aviso. Funciones futuras sensibles (clasificaciones públicas, certificados) deberán exigirlo | No frenar a nadie al principio |
| Limpieza | Una tarea diaria borra tokens caducados o usados hace más de 7 días | La tabla no crece sin límite |

## Hallazgos durante la implementación

- **El envío tardaba 5 segundos** por correo. El SMTP en sí era rápido (menos de 0,4 s con una conversación manual); JavaMail averiguaba el nombre del equipo por DNS al generar el `Message-ID`. Se corrigió fijando `mail.from` y `mail.smtp.localhost`: el envío bajó de 5,2 s a 0,24 s y las pruebas de correo de 66 s a 6 s. Sin esa corrección, cada correo real habría tardado segundos en salir.
- **Hibernate detectó un error de esquema** (`char(64)` frente a `varchar`): la migración V6 aún no se había aplicado en ningún entorno compartido y se corrigió allí.
- En Windows, `localhost` hacia puertos publicados por Docker Desktop tarda ~2 s por petición; en las pruebas se usa `127.0.0.1`.

## Riesgos y límites conocidos

- **Sin bandeja de salida persistente**: si el proveedor falla en ese momento, el correo se pierde (solo se registra el error) y el usuario debe pedir otro. Con más volumen hace falta una tabla de salida con reintentos.
- **Tiempos de la recuperación**: para una cuenta existente se hace trabajo extra en la base de datos (emitir el token). Se pierde por completo la diferencia de red con el envío asíncrono, pero una medición estadística muy fina podría notar milisegundos de diferencia.
- **Rebotes y quejas**: aún no se procesan (hay que dejar de escribir a direcciones que rebotan). Dependen del proveedor elegido.
- **Con varias instancias del API** los límites en memoria no se comparten.
- **No probado con un proveedor real**: solo con Mailpit. Antes de producción hace falta un dominio, registros SPF, DKIM y DMARC, y enviar correos de prueba a Gmail, Outlook y Yahoo.

## Comparación de proveedores (2026-10-23, páginas oficiales)

| Proveedor | Gratis | Primer plan de pago |
|---|---|---|
| Resend | 3.000/mes y 100/día | Pro $20/mes por 50.000 |
| Amazon SES | Créditos de hasta $200; plan gratis de 6 meses | $0,10 por 1.000 |
| Scaleway TEM | 300 incluidos | €0,25 por 1.000 |
| Postmark | 100/mes | Basic $15/mes por 10.000 |
| Mailgun | 100/día | Basic $15/mes por 10.000 |
| Brevo | no verificado en página oficial | — |

## Verificación (2026-10-23)

| Comprobación | Resultado |
|---|---|
| API: `mvnw verify` | Pasa: 44 pruebas contra PostgreSQL 18 y Mailpit reales (envío SMTP real, lectura del correo por la API de Mailpit, un solo uso, caducidad, propósito, hash en base, idioma del correo, mismo resultado exista o no el correo, límites, sesiones cerradas al restablecer) |
| Web: `npm test`, `npm run build` | Pasan: 40 pruebas; compila |
| Flujo en navegador por nginx con el stack completo | Funciona: registro, aviso de correo sin verificar, correo recibido en Mailpit (en el idioma del perfil), verificación por el enlace (el token desaparece de la dirección), recuperación con el mismo mensaje para correo existente e inexistente (y un solo correo enviado), restablecimiento, error de la contraseña vieja, entrada con la nueva |
| CI de GitHub Actions | **No ejecutado** (no hay repositorio remoto) |
| Proveedor real (Resend u otro) | **No probado** |
