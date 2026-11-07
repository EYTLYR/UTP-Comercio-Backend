# Decisiones del backend

Este archivo anota en qué se aparta el código de los prompts de [BACKEND_PROMPTS.md](BACKEND_PROMPTS.md), y por qué.

## Fase 1

1. **Paquete base `eytlyr.com.utp_comercio_backend`** en lugar de `pe.nearby.api`. Es el paquete que generó el equipo en Spring Initializr. Así se respeta el repo existente y los subpaquetes por funcionalidad quedan iguales (`common`, `config`, `auth`…).
2. **Spring Boot 4.1.2.** Es la versión del `pom.xml`. Trae Jackson 3, starters modulares (`spring-boot-starter-webmvc`, etc.) y Testcontainers 2. Con ella se usa springdoc-openapi **3.2.0**, que está compilado contra Boot 4.1.2.
3. **Puerto del host aleatorio en `compose.yaml`** (`'5432'` en vez de `'5432:5432'`). La PC de desarrollo ya tiene un PostgreSQL 18 escuchando en 5432. Spring Boot Docker Compose Support detecta el puerto que se asignó, así que no hace falta fijarlo.
4. **Perfil `local`**, para trabajar sin Docker contra un PostgreSQL instalado o contra el repo `UTP-Comercio-Data-Base` (puerto 5435). Es un grupo que también activa `dev`.
5. **PostgreSQL embebido para pruebas** (zonky `embedded-postgres`, binarios 16.15). Se usa solo cuando no hay Docker; si hay Docker, se usa Testcontainers como pide el prompt. Con esto, `./mvnw verify` y `./mvnw spring-boot:test-run` funcionan en cualquier PC. El clúster se crea con UTF-8 y locale C para no heredar la codificación WIN1252 de Windows.
6. **El `JwtDecoder` HS256 ya está configurado** (con `nearby.jwt-secret`), así que el resto de la API responde 401 con ProblemDetail. La emisión de tokens llega en la fase 3.
7. **CORS** se configura con `nearby.cors.allowed-origin-patterns`. En dev vale `http://localhost:*` y `http://127.0.0.1:*`; en prod se define por `NEARBY_CORS_ORIGINS`.
8. **Swagger desactivado en `prod`** salvo que se defina `NEARBY_SWAGGER_ENABLED=true`.
9. **Los 401 y 403 del filtro de seguridad** se escriben a mano como ProblemDetail, porque ocurren antes de llegar a Spring MVC. Los 403 por `@PreAuthorize` sí pasan por `ApiError`.

## Fase 2

1. **`BaseEntity`** (`common`) da a todas las entidades el `id` UUID (`GenerationType.UUID`), `createdAt` y `updatedAt`.
   - Las fechas se completan con `@PrePersist` y `@PreUpdate` en lugar de `@CreationTimestamp`.
   - `createdAt` solo se llena si viene vacío. Así el seeder puede cargar el historial con fechas en el pasado, que la IA usa en la fase 9.
2. **Enum nuevo `MissionStatus`** (`PROPOSED`, `NOTIFIED`, `DONE`) para `mission_assignment.status`. El prompt pedía la columna, pero no definía sus valores.
3. **Restricciones extra:**
   - `zone.name` único: el seeder lo usa para no cargar dos veces.
   - `device_token.token` único.
   - `vulnerable_report (reporter_id, client_id)` único, por la regla de idempotencia sin conexión.
   - `trusted_contact.position` entre 1 y 3.
   - `resource_listing.quantity` mayor o igual a 0.
4. **`medical_card` es `@OneToOne`** con `user_id` único. Las demás relaciones son `@ManyToOne(fetch = LAZY)`.
5. **Borrado en cascada (`ON DELETE CASCADE`)** solo en los datos propios del usuario: contactos, ficha, tokens y checklist.
6. **Resúmenes de recursos del ANEXO A** (por ejemplo, agua 14/9 o sacos 40/120). Se cargan los listados de ejemplo del anexo y un listado agregado por tipo, «Otros vecinos de la zona (registro de la junta vecinal)», que completa la cantidad. Ese listado y el refugio de la junta vecinal quedan a nombre del usuario «Municipalidad SJL».
7. **`endorsement_count`** se carga con los números del prototipo (48, 41…) sin crear filas en `endorsement`, porque solo hay 19 usuarios. Desde la fase 5, el contador sube junto con cada Visto Bueno nuevo.
8. **Historial del muro:** las publicaciones antiguas quedan en estado `DONE` y las recientes en `OPEN`. A cada una se le asignó la categoría que corresponde a su texto (escalera → `TOOL_LOAN`, etc.).
9. **Fotos de los reportes de riesgo:** `photo_url` es una ruta de ejemplo (`/demo/risk/<categoria>.jpg`). Los archivos llegan con el `StorageService` de la fase 10. `photo_analysis` y `severity_hint` usan los textos PHOTO_AI del ANEXO B.
10. **`V1__schema.sql`** se escribió a partir de la misma especificación que las entidades, para que coincidan con `ddl-auto=validate`. Desde ahora, cualquier cambio de esquema va en una migración nueva (`V2__...`).

## Fase 3

1. **`GET /api/v1/me` adelantado.** Lo piden las pruebas de esta fase y `verify` devuelve el `UserDto`, así que ya existe con `effectiveMode` calculado según la regla de la fase 4. El resto de `/me` llega en la fase 4.
2. **Hash con pepper:** el código SMS se guarda como HMAC-SHA256(secreto JWT, `celular:código`) y se compara en tiempo constante. El token de refresco es aleatorio (32 bytes) y se guarda con el mismo HMAC.
3. **Rotación con detección de reuso:** si llega un token de refresco ya rotado, se revocan todas las sesiones del usuario y se responde 401 `REFRESH_INVALID`.
4. **Límite de intentos:** al pasar `max-attempts`, el código se quema (no sirve ni el correcto) y se responde 429 `OTP_TOO_MANY_ATTEMPTS`. Los intentos fallidos se guardan aunque haya error (`noRollbackFor`).
5. **Límite de solicitudes:** cuenta los códigos pedidos por ese celular en la última hora (tabla `otp_code`) y responde 429 `OTP_TOO_MANY_REQUESTS`.
6. **Usuario nuevo:** se crea con el nombre provisional «Vecino», `verified=false` y sin zona. La app lo completa en el registro, con `isNewUser=true`.
7. **Claim `iss` = `nearby-api`.** Spring Security 7 agrega la autoridad `FACTOR_BEARER` junto a `ROLE_<rol>`, y eso es normal.
8. **Validación:** cuando hay un solo error, `detail` es ese mensaje (por ejemplo, «Los celulares en Perú tienen 9 dígitos y empiezan con 9»), para que la app lo muestre tal cual.
9. **SMS:** `nearby.sms.provider` elige `console` (por defecto) o `twilio` (preparado, sin implementar).

## Fase 4

1. **Ficha médica para la brigada:** «tiene un reporte vulnerable abierto» se interpreta como un `vulnerable_report` donde la persona es quien reporta (`reporter_id`), en estado `PENDING` u `ON_THE_WAY`. El reporte no guarda a quién se refiere. La otra vía es tener un `sos_alert` `ACTIVE`. Si no se cumple ninguna, se responde 403 `MEDICAL_CARD_NOT_AVAILABLE`.
2. **`PUT /me/location`** elige la zona más cercana cuyo radio cubre el punto. La dirección se guarda, pero ninguna respuesta la devuelve. El JWT ya emitido conserva el `zoneId` anterior: la app debe llamar a `/auth/refresh` después de cambiar de zona.
3. **`PUT /me/trusted-contacts`** reemplaza la lista completa. Si un contacto viene sin `position`, se usa su orden en la lista. Más de 3 contactos devuelve 422 `TRUSTED_CONTACTS_MAX` y una posición repetida, 422 `TRUSTED_CONTACTS_POSITION`. Si hay error, la lista anterior no cambia.
4. **`livesWith`** se guarda separado por comas, con estos valores: `ELDERLY`, `CHILDREN`, `DISABILITY` y `PETS`. En la ficha de María, el seeder ahora usa `ELDERLY`.
5. **`GET /me/medical-card`** sin ficha devuelve una vacía (200), no un 404.
6. **`PUT /me/device-token`:** un token pertenece a un solo teléfono. Si ya estaba registrado con otra cuenta, pasa a la cuenta actual.
7. **`GET /me/prep-checklist`** siempre devuelve los 4 ítems en orden. `PUT` actualiza solo los ítems enviados.
8. **`CapabilityTag`** tiene `label()` con el nombre en español.

## Fase 5

1. **La búsqueda de servicios** se hace en memoria, dentro de la zona del usuario: una zona tiene decenas de oficios, no miles.
   - `TextUtils.matches` compara sin tildes y por raíz, quitando sufijos como -ero, -ería o -ia. Así «gasfitero» encuentra «Gasfitería» sin necesitar `unaccent` en PostgreSQL.
   - Si algún día hay miles de oficios, conviene pasarla a SQL con `unaccent` y `pg_trgm`.
2. **`ServiceCategory`** tiene `label()` en español.
3. **Orden de las reglas del Visto Bueno:** no verificado (403), su propio servicio (422), otra zona (403) y repetido (409). Si dos peticiones llegan a la vez, la restricción única responde 409 (`CONFLICT`). Si `@Version` detecta un conflicto, el controlador reintenta una vez.
4. **`DELETE /services/{id}`** es un borrado lógico (`active=false`). Nadie puede reseñar su propio servicio (422 `REVIEW_SELF`).
5. **`POST /services`** exige que el usuario haya guardado su ubicación (422 `LOCATION_REQUIRED`) y usa esas coordenadas.
6. **`ChatService.getOrCreate`** está adelantado de la fase 6, para el botón «Mensaje»: crea una conversación por par de vecinos y por contexto (servicio o publicación).

## Fase 6

1. **Migración `V2__chat_read_state.sql`:** agrega `user_a_read_at` y `user_b_read_at` a `conversation` para calcular `unread`.
   - Se cuentan los mensajes del otro participante y del sistema posteriores a mi última lectura.
   - Leer (`GET messages`) o escribir marca la conversación como leída.
2. **`Created<T>`** es el resultado de los POST idempotentes: 201 si el recurso se creó y 200 si ya existía con ese `clientId`.
3. **El muro** muestra las publicaciones `OPEN` y `MATCHED` de la zona del usuario, filtradas por radio en memoria. Sin zona, la lista está vacía. Para publicar hace falta tener zona (422 `ZONE_REQUIRED`).
4. **Responder dos veces** a la misma publicación devuelve la misma conversación, sin otro mensaje del sistema ni otra notificación. Nadie puede responder a su propia publicación (422 `POST_SELF`).
5. **`myFavors`** cuenta todas las respuestas del usuario (histórico). `favorsThisWeek` cuenta las de la zona en los últimos 7 días.
6. **Notificaciones:**
   - El aviso de un mensaje nuevo y el envío por SSE se hacen después del commit (`@TransactionalEventListener`).
   - El aviso al autor de la publicación se hace en línea, porque el servicio de desarrollo solo escribe en el log.
   - `nearby.notifications.provider` elige `log` (por defecto) o `fcm` (preparado, sin implementar).
7. **Consultas de conversación separadas** por contexto (publicación, servicio o ninguno), para no comparar parámetros nulos en SQL.

## Fase 7

1. **`Dispatch` + `DispatchListener`:** todos los avisos (push a usuarios, push a la zona y SMS) se publican como un evento. Se envían con `@Async` después del commit (`@TransactionalEventListener(AFTER_COMMIT)`) y cada envío va en su propio try/catch, así una falla del `SmsGateway` o del push nunca hace fallar el `POST /sos`.
2. **Destinatarios del SOS** (calculados en la transacción, para que la respuesta traiga los números):
   - vecinos de la zona a 500 m o menos, según su última ubicación;
   - todos los `BRIGADIST` de la zona;
   - los contactos de confianza, por SMS.
3. **`SosCodec.forSos`** ya genera el código SMS: tipo de SOS (S, F, R, Q, V o X si no hay tipo), 1 persona, la calle más cercana con el número 0 y prioridad `ALTA` (por ejemplo, `NB#SOS!X1@LF0$P:ALTA`). La decodificación queda para la fase 9.
4. **Zona del SOS:** la del vecino. Si no tiene, la que mejor cubre el punto, para no rechazar nunca un SOS por falta de zona.
5. **Activación automática:** al crear un SOS se cuentan los SOS `ACTIVE` de la zona dentro de la ventana. La respuesta incluye `zoneEmergencyActivated`.
6. **`DISMISS`** también apaga la emergencia local. Cuando la Municipalidad cambia el modo, se actualiza `mode_activated_at` y el vecino vuelve a ver el modo.
7. **`DECLINED`** avisa a los vecinos que están entre 500 m y 1000 m (los que no recibieron el primer aviso).
8. **`GET /sos/{id}`** está disponible para cualquier usuario autenticado: lo necesitan los vecinos que responden. `GET /sos/nearby` devuelve coordenadas aproximadas.
