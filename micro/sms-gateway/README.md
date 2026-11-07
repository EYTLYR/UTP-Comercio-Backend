# Microservicio SMS de Nearby (micro/sms-gateway)

Servicio aparte que envía y recibe los SMS de Nearby con [SMS Gateway for Android](https://docs.sms-gate.app): un celular Android con chip peruano hace de pasarela y este servicio lo controla por la nube (`api.sms-gate.app`). El backend y la app no hablan con el celular: solo consumen este servicio.

```text
Backend Nearby ── POST /v1/messages (X-Api-Key) ──► este servicio ──► sms-gate.app ──► celular ──► SMS
SMS entrante ──► celular ──► sms-gate.app ── POST /webhook (firmado) ──► este servicio ──► backend /api/v1/sms/inbound
```

Sin dependencias: solo Node 18 o superior.

## Cómo correrlo

1. En el celular: app SMS Gateway con **Cloud server** activo y el servicio encendido («STOP SERVICE» visible). En Xiaomi: ahorro de batería «Sin restricciones», inicio automático y permisos de SMS.
2. Copia `.env.example` a `.env` y llena los datos (abajo).
3. `npm start` → http://localhost:3000 (página de prueba).

## Variables (.env)

| Variable | Para qué |
|---|---|
| `SMSGATE_USERNAME`, `SMSGATE_PASSWORD` | Datos de la pestaña Home de la app (Cloud server) |
| `SMSGATE_SIGNING_KEY` | Clave de firma de los webhooks (app → Settings → Webhooks). Opcional, recomendada |
| `SERVICE_API_KEY` | Clave que manda el backend en `X-Api-Key`. Igual a `NEARBY_SMS_GATEWAY_API_KEY` del backend |
| `FORWARD_URL` | A dónde se reenvían los SMS recibidos: `http://localhost:8080/api/v1/sms/inbound` |
| `FORWARD_TOKEN` | Igual a `NEARBY_SMS_INBOUND_TOKEN` del backend |
| `DEMO_UI` | `true`: página de prueba en `/`. Ponlo en `false` si el servicio queda público |
| `PORT` | Puerto (3000) |

## API para el backend

Todas con el header `X-Api-Key: <SERVICE_API_KEY>`.

| Método | Ruta | Cuerpo / respuesta |
|---|---|---|
| POST | `/v1/messages` | `{"to": "987654321", "text": "..."}` → `202 {"id", "state"}`. Celular de 9 dígitos (se envía como +51) o con `+código`; máximo 160 caracteres |
| GET | `/v1/messages/{id}` | `{"id", "state", "recipients": [{"state", "error"}]}`. Estados: Pending, Processed, Sent, Delivered, Failed |
| GET | `/health` | `{"status": "UP"}` (sin clave) |

Errores: `401` clave inválida, `400` datos inválidos, `502` sms-gate no respondió.

## Recibir SMS (SOS sin internet)

sms-gate avisa por webhook, así que necesita llegar al servicio desde internet: `ngrok http 3000` o un túnel de VS Code (puerto 3000 público). Pega la URL `https://…` en «Recibir SMS» de la página y toca **Registrar**. Cada SMS recibido se muestra en la página y se reenvía al backend; si trae un código `NB#SOS!…`, el backend crea el reporte y avisa a la brigada.

## Notas

- Los SMS se cobran según el plan del chip del celular.
- La nube descarta mensajes pendientes de más de 24 horas: el celular tiene que estar encendido y con internet.
- Enviados y recibidos se guardan solo en memoria (máximo 200 de cada uno).
