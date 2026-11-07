# Nearby API · Endpoints

Todas las rutas usan JSON y JWT `Authorization: Bearer <accessToken>` salvo las marcadas como públicas.
Los errores son ProblemDetail (RFC 9457) con `code` y, en validación, `errors`. Swagger UI: `/swagger-ui.html`.
Generado desde `/v3/api-docs` (86 operaciones).

## Acceso

| Método | Ruta | Rol | Descripción |
|---|---|---|---|
| POST | `/api/v1/auth/logout` | Autenticado | Cierra todas las sesiones del usuario |
| POST | `/api/v1/auth/otp/request` | Público | Envía un código SMS al celular (en dev devuelve devCode) |
| POST | `/api/v1/auth/otp/verify` | Público | Verifica el código y entrega los tokens. Crea la cuenta si el celular es nuevo |
| POST | `/api/v1/auth/refresh` | Público | Rota el token de refresco y entrega un token de acceso nuevo |

## Mi perfil

| Método | Ruta | Rol | Descripción |
|---|---|---|---|
| GET | `/api/v1/me` | Autenticado | Mi perfil y el modo que la app debe mostrar (effectiveMode) |
| PUT | `/api/v1/me` | Autenticado | Cambiar mi nombre (2 a 60 caracteres) |
| GET | `/api/v1/me/ai-learnings` | Autenticado | Lo que la IA aprendió de mí (Protocolo Lázaro) |
| DELETE | `/api/v1/me/ai-learnings/{id}` | Autenticado | Borrar un aprendizaje de la IA (no se vuelve a crear) |
| PUT | `/api/v1/me/device-token` | Autenticado | Registrar el token de notificaciones del teléfono |
| PUT | `/api/v1/me/location` | Autenticado | Guardar mi ubicación; asigna la zona más cercana (422 si Nearby no llega) |
| GET | `/api/v1/me/medical-card` | Autenticado | Mi ficha médica (solo yo la veo; la brigada solo durante un SOS) |
| PUT | `/api/v1/me/medical-card` | Autenticado | Guardar mi ficha médica |
| GET | `/api/v1/me/prep-checklist` | Autenticado | Lista "Prepara tu casa" (El Niño) |
| PUT | `/api/v1/me/prep-checklist` | Autenticado | Marcar ítems de "Prepara tu casa" |
| PUT | `/api/v1/me/settings` | Autenticado | Consentimiento de IA; si es false se borra lo aprendido |
| GET | `/api/v1/me/trusted-contacts` | Autenticado | Mis contactos de confianza (hasta 3) |
| PUT | `/api/v1/me/trusted-contacts` | Autenticado | Reemplazar mis contactos de confianza (máximo 3) |

## Brigada

| Método | Ruta | Rol | Descripción |
|---|---|---|---|
| GET | `/api/v1/brigade/medical-cards/{userId}` | BRIGADIST, ADMIN | Ficha médica de una persona con SOS activo o reporte abierto (403 si no) |

## Zona y modos

| Método | Ruta | Rol | Descripción |
|---|---|---|---|
| POST | `/api/v1/admin/zones/{id}/mode` | ADMIN, DELEGATE | Municipalidad: cambiar el modo de la zona y avisar a los vecinos |
| POST | `/api/v1/me/mode` | Autenticado | DISMISS (volver a normal en mi app), ACTIVATE_LOCAL_EMERGENCY o CLEAR_LOCAL |
| GET | `/api/v1/zones/current` | Autenticado | Mi zona y su modo actual |

## Servicios

| Método | Ruta | Rol | Descripción |
|---|---|---|---|
| GET | `/api/v1/services` | Autenticado | Buscar oficios cercanos (q sin tildes; sort=endorsements/distance) |
| POST | `/api/v1/services` | Autenticado | Ofrecer mi oficio |
| DELETE | `/api/v1/services/{id}` | Autenticado | Quitar mi oficio (solo el dueño) |
| GET | `/api/v1/services/{id}` | Autenticado | Detalle del oficio |
| PUT | `/api/v1/services/{id}` | Autenticado | Editar mi oficio (solo el dueño) |
| POST | `/api/v1/services/{id}/contact` | Autenticado | Botón Mensaje: abre el chat con el dueño |
| POST | `/api/v1/services/{id}/endorsements` | Autenticado | Dar Visto Bueno Vecinal |
| DELETE | `/api/v1/services/{id}/endorsements/me` | Autenticado | Quitar mi Visto Bueno |
| GET | `/api/v1/services/{id}/reviews` | Autenticado | Reseñas del oficio |
| POST | `/api/v1/services/{id}/reviews` | Autenticado | Escribir una reseña (5 a 500 caracteres) |

## Muro de favores

| Método | Ruta | Rol | Descripción |
|---|---|---|---|
| GET | `/api/v1/neighborhood/stats` | Autenticado | Favores de la semana en mi barrio |
| GET | `/api/v1/posts` | Autenticado | Publicaciones abiertas del barrio, la más reciente primero |
| POST | `/api/v1/posts` | Autenticado | Publicar (idempotente por clientId: 201 nueva, 200 si ya existía) |
| DELETE | `/api/v1/posts/{id}` | Autenticado | Quitar mi publicación (queda CANCELLED) |
| GET | `/api/v1/posts/{id}` | Autenticado |  |
| POST | `/api/v1/posts/{id}/responses` | Autenticado | Yo te ayudo / Me interesa: abre el chat con el autor |
| PATCH | `/api/v1/posts/{id}/status` | Autenticado | Cambiar el estado (solo el autor) |

## Chat

| Método | Ruta | Rol | Descripción |
|---|---|---|---|
| GET | `/api/v1/conversations` | Autenticado | Mis conversaciones, la más reciente primero |
| GET | `/api/v1/conversations/{id}/messages` | Autenticado | Mensajes (polling con after=ISO-8601). Solo participantes |
| POST | `/api/v1/conversations/{id}/messages` | Autenticado | Enviar un mensaje (idempotente por clientId) |
| GET | `/api/v1/conversations/{id}/stream` | Autenticado | Mensajes nuevos en tiempo real (Server-Sent Events, opcional) |

## SOS

| Método | Ruta | Rol | Descripción |
|---|---|---|---|
| POST | `/api/v1/check-ins` | Autenticado | Estoy bien / Necesito ayuda (SAFE avisa a mis contactos) |
| POST | `/api/v1/sos` | Autenticado | Enviar SOS (idempotente por clientId). Avisa a vecinos a 500 m, brigada y contactos |
| GET | `/api/v1/sos/nearby` | Autenticado | SOS activos cerca de mí (ubicación aproximada) |
| GET | `/api/v1/sos/{id}` | Autenticado | Detalle del SOS con los vecinos que responden |
| PATCH | `/api/v1/sos/{id}` | Autenticado | Elegir el tipo de emergencia (recalcula el código SMS) |
| POST | `/api/v1/sos/{id}/cancel` | Autenticado | Cancelar el SOS (no envía nada más) |
| POST | `/api/v1/sos/{id}/responses` | Autenticado | Responder: GOING (voy), DECLINED (no puedo) o ARRIVED (llegué) |
| POST | `/api/v1/sos/{id}/safe` | Autenticado | Estoy a salvo: cierra el SOS y avisa a mis contactos |

## Emergencia

| Método | Ruta | Rol | Descripción |
|---|---|---|---|
| GET | `/api/v1/resources` | Autenticado | Listados de recursos críticos |
| POST | `/api/v1/resources` | Autenticado | Publicar que tengo o necesito un recurso |
| GET | `/api/v1/resources/summary` | Autenticado | Tengo / necesito por tipo de recurso crítico |
| GET | `/api/v1/safe-points` | Autenticado | Puntos de reunión y refugios |
| GET | `/api/v1/support-groups` | Autenticado | Grupos de apoyo de la zona |
| POST | `/api/v1/support-groups/{id}/members` | Autenticado | Unirme al grupo |
| DELETE | `/api/v1/support-groups/{id}/members/me` | Autenticado | Salir del grupo |
| GET | `/api/v1/vulnerable-reports` | Autenticado | Reportes de vecinos vulnerables (dirección exacta solo para la brigada) |
| POST | `/api/v1/vulnerable-reports` | Autenticado | Reportar a un vecino vulnerable (idempotente por clientId) |
| POST | `/api/v1/vulnerable-reports/{id}/assign` | Autenticado | Voy yo: queda en camino y asignado a mí |
| PATCH | `/api/v1/vulnerable-reports/{id}/status` | Autenticado | Cambiar el estado (quien está asignado o la brigada) |
| GET | `/api/v1/zones/{id}/dashboard` | Autenticado | Resumen del tablero humanitario de la zona |

## Mapa

| Método | Ruta | Rol | Descripción |
|---|---|---|---|
| GET | `/api/v1/map/pins` | Autenticado | Pines del mapa: layer=daily/emergency/prevention |

## IA · Protocolo Lázaro

| Método | Ruta | Rol | Descripción |
|---|---|---|---|
| POST | `/api/v1/admin/ai/reindex` | ADMIN | Reprocesar todo el contenido de la zona con la IA |
| POST | `/api/v1/ai/classify` | Autenticado | Micrófono inteligente: texto dictado → tipo, categoría, prioridad y ubicación |
| GET | `/api/v1/ai/inventory` | Autenticado | Inventario de la zona (vulnerabilidades anónimas para vecinos) |
| GET | `/api/v1/ai/inventory/snapshot` | Autenticado | Inventario compacto para el teléfono (ETag; 304 si no cambió) |
| GET | `/api/v1/ai/inventory/stats` | Autenticado | Cuántos recursos, habilidades y alertas preventivas conoce la IA |
| POST | `/api/v1/ai/missions/{reportId}/notify` | BRIGADIST, DELEGATE, ADMIN | Avisar a los vecinos asignados y dejar el reporte en camino |
| POST | `/api/v1/ai/rescue-plans` | BRIGADIST, DELEGATE, ADMIN | Generar el plan de rescate: reportes preventivos y misiones automáticas |
| GET | `/api/v1/ai/rescue-plans/latest` | BRIGADIST, DELEGATE, ADMIN | Último plan de rescate de mi zona |

## SOS por SMS

| Método | Ruta | Rol | Descripción |
|---|---|---|---|
| POST | `/api/v1/sms/decode` | Autenticado | Leer un código (vista del brigadista). 422 SMS_CODE_INVALID si no es válido |
| POST | `/api/v1/sms/encode` | Autenticado | Comprimir un reporte en un código de 25 caracteres o menos |
| POST | `/api/v1/sms/inbound` | Público (X-Nearby-Token) | Webhook de la pasarela SMS (header X-Nearby-Token) |

## El Niño · Radar preventivo

| Método | Ruta | Rol | Descripción |
|---|---|---|---|
| GET | `/api/v1/map/heat` | Autenticado | Mapa de calor: manchas RED (220 m) y AMBER (150 m) |
| GET | `/api/v1/mitigation` | Autenticado | Listados de mitigación (sacos, motobombas, calaminas...) |
| POST | `/api/v1/mitigation` | Autenticado | Publicar Necesito / Ofrezco de mitigación |
| GET | `/api/v1/mitigation/summary` | Autenticado | Tengo / necesito por recurso de mitigación |
| GET | `/api/v1/risk-reports` | Autenticado | Reportes de riesgo recientes de mi zona |
| POST | `/api/v1/risk-reports` | Autenticado | Reportar un riesgo sin foto (JSON). escalated=true si la zona subió a RED |
| GET | `/api/v1/risk-zones` | Autenticado | Zonas de riesgo activas con patrón, alerta y mitigación sugerida |
| POST | `/api/v1/risk-zones/analyze` | Autenticado | Analizar con IA: recalcula las zonas de riesgo |
| POST | `/api/v1/risk-zones/{id}/coordinate` | Autenticado | Sí, coordinar ayuda: abre el chat con quien tiene los recursos y avisa a la manzana |

## Sin conexión

| Método | Ruta | Rol | Descripción |
|---|---|---|---|
| GET | `/api/v1/sync` | Autenticado | Datos de la zona para guardar en el teléfono (since = solo lo cambiado) |
| POST | `/api/v1/sync/outbox` | Autenticado | Enviar la cola: POST, SOS, VULNERABLE_REPORT, RISK_REPORT, MESSAGE o CHECK_IN (en orden) |

## Archivos

| Método | Ruta | Rol | Descripción |
|---|---|---|---|
| GET | `/files/{name}` | Público | Foto subida (reportes de riesgo) |

## Salud

| Método | Ruta | Rol | Descripción |
|---|---|---|---|
| GET | `/actuator/health` | Público | Estado de la app y de la base de datos |
