# Gemini (Protocolo Lázaro) y SMS por HTTP con reintentos

## IA con Gemini

Con `NEARBY_AI_PROVIDER=gemini` y `GEMINI_API_KEY` en `.env`:

| Qué | Clase | Con reglas (si Gemini falla) |
|---|---|---|
| Foto de riesgo (lluvias / El Niño): la imagen va en Base64 y vuelve la categoría, el nivel de agua, la severidad y un resumen | `prevention/GeminiPhotoRiskAnalyzer` | `RuleBasedPhotoRiskAnalyzer` |
| Micrófono: el texto dictado se convierte en el formulario (tipo, categoría, prioridad y ubicación) | `ai/GeminiTextClassifier` | `TextClassifier` |

Las dos usan `ai/GeminiClient`, que hace la llamada con `RestClient`:

```
POST {base}/v1beta/models/gemini-2.5-flash:generateContent
x-goog-api-key: <GEMINI_API_KEY>
{ "systemInstruction": {...}, "contents": [{ "parts": [ {"inlineData": {"mimeType","data"}}, {"text"} ] }],
  "generationConfig": { "responseMimeType": "application/json", "temperature": 0.2,
                        "thinkingConfig": { "thinkingBudget": 0 } } }
```

**Para que el vecino nunca vea un error 500:**

- **Timeout:** si Gemini tarda más de 8 s (`NEARBY_GEMINI_TIMEOUT_SECONDS`), responden las reglas.
- **Circuit breaker:** después de 3 fallas seguidas, el backend deja de llamar a Gemini por 60 s y responde con reglas al instante. Pasado ese tiempo prueba una llamada; si sale bien, vuelve a Gemini.
- **Validación:** el micrófono solo acepta valores que la app conoce para el modo de la zona (`NORMAL`, `PREVENTION` o `EMERGENCY`). Si Gemini inventa una categoría, se usan las reglas. Si Gemini no da la dirección, se toma del texto ("los pinos 340" → "Jr. Los Pinos 340").
- **Clave:** la API key va en un header y no se escribe en el log.

`NEARBY_AI_PROVIDER=anthropic` sigue funcionando igual con Claude. El índice de capacidades y el plan de rescate todavía no tienen versión Gemini: con `gemini` usan sus reglas.

## SMS por HTTP con reintentos

Con `NEARBY_SMS_PROVIDER=http` (o `gateway`, el nombre anterior, para no romper los `.env` existentes), `sms/HttpSmsGateway` envía con `RestClient` al microservicio `micro/sms-gateway`:

```
POST {NEARBY_SMS_GATEWAY_URL}/v1/messages
X-Api-Key: <NEARBY_SMS_GATEWAY_API_KEY>
{"to": "987654321", "text": "NEARBY SOS: ... https://maps.google.com/?q=-11.97900,-77.00300 · Código NB#SOS!H1@LF245$P:ALTA"}
```

El texto del SOS lleva el enlace del mapa y el código comprimido de `SosCodec`. La app lo descomprime en el modo brigadista, incluso sin internet.

**Reintentos:**

- Si el microservicio no responde, tarda o devuelve 5xx o 429, se reintenta **2 veces** (3 intentos en total, con espera de 0.5 s y luego 1 s).
- Un 4xx (clave inválida o número mal formado) no se reintenta.
- Si fallan todos los intentos, queda un `log.error("CRÍTICO: no se pudo enviar el SMS ...")`.

**El SOS no se cae si falla el SMS:**

- `SosService` solo publica los avisos. `DispatchListener` los envía **después del commit** y en segundo plano (`@TransactionalEventListener(AFTER_COMMIT)` + `@Async`).
- Cada push y cada SMS va en su propio `try/catch`. Si el SMS falla, el SOS ya está guardado y los demás avisos se envían igual.
- En el código de acceso (OTP), el error sí se informa al usuario (`SMS_SEND_FAILED`), para que pida otro.

## Pruebas

- `ai/GeminiTest`: usa un servidor falso. Comprueba la imagen en Base64, el header de la clave, el JSON de la foto, el formulario del micrófono, la caída a reglas con un 500 o una categoría inventada, y el circuit breaker (se abre, no llama y se cierra pasados 60 s).
- `sms/HttpSmsGatewayTest`: comprueba el envío con la clave, 503 dos veces y luego 202 (3 intentos), 500 siempre (3 intentos y error), 401 (sin reintento) y el microservicio caído.
- `notification/DispatchListenerTest`: un SMS que falla no frena el push ni los otros SMS.
- `config/RealProvidersWiringTest`: con `gemini` + `http`, Spring arranca con las implementaciones reales.
