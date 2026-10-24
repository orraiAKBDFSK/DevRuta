# Lista de comprobación para desplegar

Lo que hay hoy funciona en local (Compose). **Nada de esto se ha probado en un servidor real.** Marca cada punto
antes de abrir el registro al público.

## 1. Repositorio y CI
- [ ] Repositorio remoto creado; `git push` de `main`.
- [ ] Primera ejecución del workflow `CI` en verde (jobs: contenido, API, runner, web, imágenes). Si algo falla, arreglarlo antes de seguir.
- [ ] Rama `main` protegida: pull request + CI verde obligatorios.

## 2. Aplicación (API + web + PostgreSQL)
- [ ] Variables obligatorias en producción: `PUBLIC_BASE_URL`, `MAIL_FROM`, credenciales de base de datos (gestor de secretos, no `.env`).
- [ ] `APP_CONTENT_MIN_STATUS=PUBLISHED` (el valor por defecto) y contenido revisado (ver `docs/revision-de-contenido.md`).
- [ ] HTTPS delante de la web y la API (la cookie de sesión debe ir `Secure`); revisar cabeceras de seguridad del proxy.
- [ ] PostgreSQL gestionado con copias de seguridad y una **restauración probada**.
- [ ] Límites de peticiones: hoy son en memoria (un solo nodo). Con varias instancias hay que moverlos a la base de datos.

## 3. Correo
- [ ] Proveedor elegido (ver ADR 0004) con dominio propio verificado: SPF, DKIM y DMARC.
- [ ] `SPRING_MAIL_*` y `MAIL_FROM` configurados; probar verificación de cuenta y recuperación con un buzón real.
- [ ] **Textos legales** (términos y política de privacidad) redactados y enlazados en el registro; revisar con alguien cualificado.

## 4. Runner (ejecución de código)  — la parte más delicada
- [ ] **Máquina dedicada**, sin acceso a la red interna, a la base de datos ni a secretos.
- [ ] Docker sin root (rootless) o gVisor (`SANDBOX_RUNTIME=runsc`); **probar los 19 ataques de `apps/runner/tests/test_sandbox.py` con ese runtime** y `scripts/verify_runner_content.py`.
- [ ] `RUNNER_TOKEN` largo y aleatorio en un gestor de secretos; tráfico API→runner por red privada con TLS.
- [ ] No montar el socket de Docker en un host compartido con otros servicios.
- [ ] Perfil seccomp/AppArmor propio y estrecho; cuotas de CPU, memoria y disco; alertas por errores `502` y saturación `503`.
- [ ] Prueba de carga para fijar `RUNNER_MAX_CONCURRENT` y los límites por contenedor.
- [ ] **Revisión de seguridad externa** del runner antes de aceptar código de desconocidos.

## 5. Observabilidad y operación
- [ ] Logs centralizados (sin código de estudiantes ni contraseñas), métricas y alertas básicas.
- [ ] Plan de actualización de dependencias (Dependabot ya está configurado) y de las imágenes base.
- [ ] Procedimiento para borrar una cuenta a petición (ya existe `DELETE /v1/me`) y para atender solicitudes de datos.

## 6. Calidad antes de abrir
- [ ] Prueba con lector de pantalla real (`docs/prueba-con-lector-de-pantalla.md`) y revisión de contraste.
- [ ] Al menos una unidad revisada y publicada para tener algo que enseñar.
