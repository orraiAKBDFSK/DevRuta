# ADR 0008: Progreso, XP, rachas y logros (fase 8)

Estado: aceptado · Fecha: 2026-10-23

## Contexto

Los intentos ya se guardaban (ADR 0006), pero el estudiante no veía avance. La fase pide una gamificación sencilla y
justa, sin patrones que presionen (sin vidas ni castigos por fallar).

## Decisiones

1. **XP solo al resolver por primera vez.** `xp_event` tiene clave primaria `(user_id, exercise_id)`: un ejercicio da XP
   una sola vez, y esa garantía la da la base de datos, no el código (dos envíos correctos simultáneos dan XP una vez;
   hay una prueba con tres peticiones paralelas).
2. **Fórmula:** dificultad × 10, un 20 % menos por cada pista usada, mínimo el 50 %. Pedir pistas no se castiga con
   fallo, solo reduce algo la recompensa. Todas las reglas están en `ProgressService`.
3. **Nivel** = 1 + √(XP / 50): cada nivel cuesta más que el anterior. Se calcula al leer, no se guarda.
4. **Racha** = días seguidos (en la zona horaria del usuario) con al menos un ejercicio resuelto por primera vez. La web envía
   la zona del navegador en la cabecera `X-Time-Zone`; si falta o no es válida, se usa UTC. Se actualiza con la fila bloqueada
   (`for update`) para que dos resoluciones simultáneas no la calculen con datos viejos. Al leer, una racha de más de un
   día de antigüedad se muestra como 0 aunque la fila guarde el número viejo, así que no hace falta ningún proceso nocturno.
5. **Logros** (7, definidos en código): primer ejercicio, 10 y 50 resueltos, rachas de 3 y 7 días, lección completa y
   5 sin pistas. Se desbloquean con `insert … on conflict do nothing`: solo se anuncian la primera vez. La web traduce
   nombre y descripción; el servidor envía solo códigos.
6. **Una lección está completa** cuando todos sus ejercicios no archivados están resueltos (incluidos los de escribir código).
7. **`user_progress` es un resumen reconstruible** a partir de `xp_event`; todo se borra con la cuenta (`on delete cascade`).
8. **Respuesta del intento** incluye `xpEarned` y `newAchievements` solo cuando son nuevos. `GET /v1/progress` resume todo.
9. **Web:** página `/progreso` (nivel, barra `<progress>` nativa, racha, totales, logros) y aviso de XP/logros en la tarjeta del ejercicio.

## Riesgos y límites conocidos

- **La zona horaria viene del navegador en cada petición, no se guarda en el perfil.** Si alguien practica desde dos zonas
  distintas (viaje, VPN que cambia la zona del sistema), su día puede contarse distinto y la racha romperse o alargarse.
  Guardarla en el perfil sería más estable; queda como mejora.
- **No hay protección contra «granjas» de XP más allá del límite de peticiones**: el XP depende del contenido, que es finito
  (412 ejercicios), así que el máximo total es fijo.
- Cambiar las fórmulas no recalcula el XP ya concedido (queda guardado en `xp_event`).
- Si se archiva un ejercicio, una lección completada puede dejar de contar como completa hasta que se resuelva el nuevo.
- No hay ranking, repaso espaciado ni recordatorios: fuera de esta fase.
- Los logros no se retroalimentan si se añaden nuevos: solo se otorgan al resolver un ejercicio.

## Verificación (2026-10-23, en local)

| Qué | Resultado |
|---|---|
| Reglas puras (XP, nivel, racha) y `ZoneSupport` | 6 pruebas |
| API con PostgreSQL real | 12 pruebas: sin sesión 401, usuario nuevo, XP por dificultad, respuestas erróneas, repetir, pistas, concurrencia, racha (crece, sobrevive un día, se rompe, conserva el récord), logro de 3 días, lección completa con escribir código, independencia entre usuarios, borrado en cascada y la zona horaria (el mismo instante es otro día en UTC y en Los Ángeles; una zona no válida cae a UTC) |
| API completa | 178 pruebas correctas, 0 fallos (`mvnw clean test`) |
| Web | 108 pruebas correctas (página de progreso, servicio, aviso en la tarjeta, cabecera de zona horaria y comprobación automática de accesibilidad con axe-core); `npm run build` correcto |
| Navegador (Compose completo) | primer acierto muestra «+10 XP» y «Logro desbloqueado: Primer paso»; `/progreso` muestra nivel 1, barra, racha y logros; al borrar la cuenta quedan 0 filas de XP, progreso y logros |

Corregido durante la verificación: «1 días seguidos» (ahora usa el singular).

El CI de GitHub se ejecutó después (2026-10-24) y pasó. No verificado: lectores de pantalla reales.
