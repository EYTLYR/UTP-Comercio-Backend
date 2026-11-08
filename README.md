# Nearby API (UTP-Comercio-Backend)

Backend de **Nearby**, la app de vecinos de San Juan de Lurigancho. Sirve para:
- conseguir productos y servicios recomendados por otros vecinos («Visto Bueno Vecinal»);
- hacer favores sin dinero;
- coordinar la ayuda cuando hay una emergencia (sismo, huaico) o en prevención ante el Fenómeno El Niño.

Stack:
- Java 21 · Spring Boot 4.1 · PostgreSQL (Neon) · Flyway · Spring Security (JWT HS256) · springdoc · fotos en Neon Object Storage (S3).
- IA por reglas, o con Claude (SDK oficial de Anthropic) si se activa.

## Arquitectura

```text
 App Flutter (online / offline)                      Pasarela SMS
   │  JWT · JSON · SSE · sync/outbox                    │  POST /sms/inbound (X-Nearby-Token)
   ▼                                                    ▼
┌──────────────────────────────── Nearby API (Spring Boot) ─────────────────────────────────┐
│ auth · user · zone ─ modos NORMAL / PREVENTION / EMERGENCY por zona y por vecino          │
│ services (Visto Bueno) · posts (cadena de favores) · chat (polling + SSE)                │
│ sos (SOS, respuestas, check-ins, activación automática) · emergency (tablero, recursos)   │
│ ai  ─ Protocolo Lázaro: inventario → plan de rescate → misiones · micrófono inteligente   │
│ sms ─ código NB#SOS! (≤ 25 caracteres) · prevention ─ radar El Niño, calor, mitigación    │
│ sync ─ paquete offline + cola · map · notification · storage                              │
│                                                                                           │
│ Dispatch (después del commit, @Async) ──► NotificationService (log | FCM)                 │
│                                       └─► SmsGateway (console | microservicio SMS)        │
│ CapabilityExtractor / PhotoRiskAnalyzer ──► reglas  |  Claude (claude-haiku-5-5)          │
└───────────────────────────────────────────┬───────────────────────────────────────────────┘
                                            ▼
                 Neon PostgreSQL + PostGIS (Flyway V1..V4) · fotos en Neon Object Storage (S3, bucket nearby)
```

- **Paquetes por funcionalidad:** cada uno tiene su entidad, repositorio, servicio, controlador y `dto/`.
- **Errores:** siempre en formato ProblemDetail, con `code` estable y mensajes en español.
- **Sin conexión:** todo POST que se crea desde el teléfono es idempotente por `clientId`.

## Cómo correrlo

Los secretos van en **`.env`** (no se sube a git). Cópialo de [.env.example](.env.example) y llena la base de Neon y las credenciales S3. Spring Boot lo lee solo (`spring.config.import`).

En PowerShell puedes iniciar el servicio con `.\mvnw.cmd spring-boot:run`. Se respeta `SPRING_PROFILES_ACTIVE` del `.env` (por ejemplo `local` o `prod`); un perfil indicado con `-Dspring-boot.run.profiles=...` tiene prioridad. Sin esa variable se conserva `dev`; sin Docker usa `local` con la conexión PostgreSQL configurada.

Elige una de estas formas.

**0. Con Neon (base y fotos en la nube, lo normal ahora).** Perfil `local`: usa `DB_URL` y las credenciales S3 del `.env`. Si la base está vacía, Flyway crea las tablas y se crean solo los **datos base** (Zona 3 · Las Flores, sus 5 calles y 2 puntos seguros). No hay usuarios precargados: los crean los vecinos al registrarse.

```bash
./mvnw spring-boot:run "-Dspring-boot.run.profiles=local"
```

**1. Sin Docker y sin internet.** Arranca un PostgreSQL temporal con tu instalación local de PostgreSQL + PostGIS (ver [Pruebas](#pruebas)) y fotos en memoria, y carga los datos de ejemplo de las pruebas (María, la brigada, etc.; solo aquí). Se borra al detener la app.

```bash
./mvnw spring-boot:test-run
```

**2. Con Docker** (perfil `dev`). Spring Boot levanta PostgreSQL solo, usando `compose.yaml`:

```bash
./mvnw spring-boot:run
```

**3. Con un PostgreSQL ya instalado** (perfil `local`; necesita la extensión PostGIS; en PowerShell):

```powershell
$env:DB_URL="jdbc:postgresql://localhost:5435/utp_comercio_db"; $env:DB_USER="utp_comercio_user"; $env:DB_PASSWORD="..."
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local"
```

**Producción** (VPS, Railway o Render) con `Dockerfile` multi-stage y `compose.prod.yaml` (solo la app; base y fotos en Neon):

```bash
cp .env.example .env   # llena Neon y S3, cambia los secretos
docker compose -f compose.prod.yaml --env-file .env up -d --build
```

- Con la base vacía solo se crean los datos base (zona, calles y puntos seguros); los usuarios son los que se registran.
- El código SMS es aleatorio y nunca se devuelve en la respuesta: con el perfil `prod`, `NEARBY_OTP_DEV_CODE` se ignora siempre.

**Para verificar:**
- Salud: http://localhost:8080/actuator/health
- Swagger UI: http://localhost:8080/swagger-ui.html
- Endpoints: [docs/API.md](docs/API.md)

## Pruebas

```bash
./mvnw verify
```

Son 88 pruebas contra PostgreSQL real con PostGIS. Las fotos van a memoria: las pruebas no tocan Neon ni S3.

- **Con Docker:** Testcontainers con la imagen `postgis/postgis:18-3.6-alpine` (las mismas versiones que Neon).
- **Sin Docker:** se crea un clúster temporal con la instalación local de PostgreSQL que tenga PostGIS (en Windows, el instalador de EDB y luego Stack Builder › Spatial Extensions › PostGIS). Se busca en `C:\Program Files\PostgreSQL\*`, `/usr/lib/postgresql/*` y Homebrew; si está en otro lado, define `NEARBY_PG_HOME` con la carpeta que contiene `bin/` y `share/`.

Cubren la lógica clave:
- el codec SMS y el clasificador;
- el extractor y el plan de rescate;
- la agrupación del radar;
- el Visto Bueno, los modos y la privacidad;
- la sincronización y la cola;
- el SOS y la autenticación.

## Usuarios y roles

La base arranca **vacía**: sin usuarios, sin publicaciones y sin zonas. Todo lo crean los vecinos y las Municipalidades.

**Zonas:**
- **Al guardar la casa** (`PUT /api/v1/me/location`), el vecino queda en la zona que cubre su casa. Si ninguna la cubre, se crea la zona de su barrio, con un radio de 1,2 km. La app manda `zoneName` (urbanización o barrio) y `district`, que saca de OpenStreetMap.
- **Desde su panel**, la Municipalidad crea zonas de su distrito con `POST /api/v1/municipality/zones` (`name`, `lat`, `lng`, `radiusM`). El ADMIN también puede, indicando `district`.
- **Para explorarlas** en el mapa: `GET /api/v1/zones`.
- **Zona de ejemplo antigua:** las bases que aún tienen la «Zona 3 · Las Flores» la pierden sola. Cada vecino que estaba en ella pasa a la zona de su barrio la próxima vez que abre la app, y se lleva lo que publicó. La zona se borra en el siguiente arranque, cuando ya no la usa nadie.

**Perfil:** `GET /api/v1/me/stats` devuelve los favores que hice, los vistos buenos que di y los favores que recibí.

El código SMS es **aleatorio** en cada pedido: llega por SMS (`NEARBY_SMS_PROVIDER=gateway`) o, sin SMS reales, aparece en el log del backend (`[SMS → 987 *** 321] Tu código Nearby es …`). Para una demo sin SMS se puede fijar con `NEARBY_OTP_DEV_CODE=123456` en `.env`; entonces `/auth/otp/request` lo devuelve en `devCode`.

Todos se registran como vecinos. Los roles no se configuran a mano: salen de la app.

```
NEARBY_ADMIN_PHONES=987654321   # administrador de la plataforma (quien aprueba Municipalidades)
NEARBY_BRIGADE_PHONES=          # opcional, solo para demos
```

`NEARBY_ADMIN_PHONES` es lo único fijo: alguien de confianza tiene que aprobar a las Municipalidades, porque si no cualquiera podría registrarse como Municipalidad y activar una emergencia en un distrito.

Los usuarios de ejemplo (María Quispe `987654321`, Brigada Z3 `999000111`, Municipalidad `999000000`…) solo existen en las pruebas y en `./mvnw spring-boot:test-run` (`src/test/.../seed/DemoDataSeeder.java`).

**Roles:**
- **NEIGHBOR:** vecino.
- **BRIGADIST:** ve direcciones exactas y fichas médicas durante un SOS, y genera planes de rescate.
- **DELEGATE:** delegado de una zona; cambia el modo de su zona y aprueba brigadistas de su zona.
- **MUNICIPALITY:** equipo de una Municipalidad aprobada. Administra las zonas de su distrito: modo de la zona, brigadistas, roles de los vecinos y su propio equipo.
- **ADMIN:** administrador de la plataforma Nearby. Aprueba Municipalidades, ve todas las zonas y puede reindexar la IA.

**Registro de Municipalidades:**
1. **Registrar:** el responsable ingresa con su celular y envía `POST /api/v1/municipalities` con nombre, distrito, RUC (11 dígitos, empieza con 20), responsable, cargo y correo institucional. Queda `PENDING`. Ve el estado con `GET /api/v1/municipalities/mine` (`NONE`, `PENDING`, `APPROVED` o `REJECTED`). `GET /api/v1/municipalities/districts` sugiere los distritos que ya tienen zonas. Un RUC y un distrito solo pueden tener un registro vigente.
2. **Aprobar:** el ADMIN lista los registros con `GET /api/v1/admin/municipalities` (pendientes primero) y responde con `POST /api/v1/admin/municipalities/{id}/approve` o `/reject` (`{"reason": "..."}`). Al aprobar, quien la registró pasa a MUNICIPALITY.
3. **Panel de la Municipalidad:**
   - `GET /api/v1/municipality/zones`: zonas de su distrito (el distrito se compara sin tildes ni mayúsculas con el de la zona).
   - `POST /api/v1/admin/zones/{id}/mode`: cambia el modo de una zona. Si la zona no es de su distrito responde 403.
   - **Equipo:** `GET /api/v1/municipality/staff`, `POST /api/v1/municipality/staff` (`{"phone": "9…"}`, la persona debe haber ingresado antes) y `DELETE /api/v1/municipality/staff/{userId}` (vuelve a ser vecino).

**Brigadistas:**
- **Pedir el rol:** el vecino lo pide con `POST /api/v1/me/brigade-request` (`{"skills": "..."}`) y ve el estado con `GET /api/v1/me/brigade-request`. Los estados son `NONE`, `PENDING`, `APPROVED` y `REJECTED`.
- **Aprobar o rechazar:** la Municipalidad de su distrito (o el delegado de su zona) lista las pendientes con `GET /api/v1/admin/brigade-requests` y responde con `POST /api/v1/admin/brigade-requests/{id}/approve` o `/reject`. Cada quien solo ve las de su jurisdicción. Al aprobar, el vecino pasa a BRIGADIST.
- **Cambiar un rol a mano:** la Municipalidad (solo vecinos de su distrito) o el ADMIN. Primero busca al vecino con `GET /api/v1/admin/users?phone=9…` y luego cambia su rol con `PUT /api/v1/admin/users/{id}/role`. Los roles posibles son NEIGHBOR, BRIGADIST y DELEGATE.
- **Cuándo rige el nuevo rol:** el perfil (`GET /me`) lo muestra de inmediato. Para los permisos del servidor rige al renovar el token; la app lo renueva sola al notar el cambio.

**Foto de perfil:** se sube con `PUT /api/v1/me/photo` (multipart, campo `photo`, JPG o PNG de hasta 5 MB) y se quita con `DELETE /api/v1/me/photo`. Se guarda donde se guardan las fotos de riesgo, y `photoUrl` aparece en `GET /me`.

**Prevención El Niño:** la Municipalidad lo activa con `POST /api/v1/admin/zones/{id}/mode` (`{"mode":"PREVENTION"}`) y vuelve a la normalidad con `NORMAL`. Cada vecino puede volver al modo normal solo en su app con `POST /api/v1/me/mode` (`{"action":"DISMISS"}`). Esto sirve para cualquier modo distinto de NORMAL.

## Guion de la demo

[requests/demo.http](requests/demo.http) reproduce todo el flujo con los datos de ejemplo, es decir, contra `./mvnw spring-boot:test-run` (con `NEARBY_OTP_DEV_CODE=123456`):

1. **Login** de María, la Brigada y la Municipalidad.
2. **SOS:** María avisa a 17 vecinos a 500 m o menos, a la brigada y a sus 3 contactos. Recibe el código `NB#SOS!S1@LF0$P:ALTA`.
3. **Modo emergencia:** la Municipalidad lo activa, María lo ve en su app y el plan de rescate se genera solo.
4. **Plan de rescate IA** (unos 65 ms):
   - 2 reportes preventivos: la abuela de Lucía y el paciente con oxígeno.
   - **Jorge y Pedro** van con la Sra. Teresa; **Taller Mendoza** (generador) va con el paciente con oxígeno.
5. **SMS:** la brigada decodifica `NB#SOS!H1@LP340$P:ALTA` = «1 herido en Jr. Los Pinos 340». El webhook convierte un SMS en un reporte.
6. **Modo prevención:** el radar muestra Quebrada Canto Grande en **rojo** y Jr. Los Pinos en **ámbar**.
7. **Reporte escalado:** el tercer reporte en Jr. Los Pinos sube la zona a **rojo** (`escalated: true`) y aparece: «Familia Ríos tiene sacos de arena y motobomba a 148 m».
8. **Coordinar:** abre el chat con la Familia Ríos y avisa a las viviendas cercanas.

Hay un archivo `.http` por módulo en [requests/](requests/): auth, perfil, servicios, muro-chat, sos, emergencia-mapa, ia-sms y el-nino.

## Qué se simula y cómo activar lo real

| Integración | En dev (por defecto) | Real |
|---|---|---|
| SMS (OTP y avisos) | `ConsoleSmsGateway`: escribe en el log, con el celular enmascarado | `NEARBY_SMS_PROVIDER=http` (o `gateway`): microservicio SMS (celular Android con SMS Gateway) con 2 reintentos, ver abajo |
| SMS entrante (SOS sin internet) | `POST /api/v1/sms/inbound` con `X-Nearby-Token` | El microservicio SMS reenvía cada SMS recibido a ese mismo endpoint |
| Push | `LoggingNotificationService`: escribe en el log | `NEARBY_NOTIFICATIONS_PROVIDER=fcm` (preparado en `FcmNotificationService`) |
| IA de texto (inventario) | Reglas del ANEXO B | `NEARBY_AI_PROVIDER=anthropic` + `ANTHROPIC_API_KEY` (Claude `claude-haiku-5-5`, con respaldo de reglas). El micrófono también puede usar Gemini (ver la fila de visión) |
| IA de visión (fotos El Niño) | Texto PHOTO_AI según la categoría | Claude con visión, o `NEARBY_AI_PROVIDER=gemini` + `GEMINI_API_KEY` (`gemini-2.5-flash` para la foto y el micrófono, con circuit breaker y respaldo de reglas; ver [docs/IA_GEMINI_Y_SMS.md](docs/IA_GEMINI_Y_SMS.md)) |
| Fotos | Neon Object Storage (S3, bucket privado `nearby`, carpeta `risk-photos/`), servidas por la API en `/files/...` | Igual; sirve cualquier S3 (AWS, R2, MinIO) cambiando `AWS_ENDPOINT_URL_S3` |
| Aviso meteorológico | Lo ingresa la Municipalidad en `/admin/zones/{id}/mode` | Leerlo de una fuente oficial (SENAMHI) |

## SMS reales (microservicio SMS)

Los SMS reales salen de un celular Android con chip peruano y la app **SMS Gateway for Android**. El backend no habla con el celular: consume el microservicio aparte [`micro/sms-gateway`](micro/sms-gateway/README.md).

```text
Backend ── POST /v1/messages (X-Api-Key) ──► microservicio SMS ──► sms-gate.app ──► celular ──► SMS
SMS entrante ──► celular ──► microservicio ── POST /api/v1/sms/inbound (X-Nearby-Token) ──► backend
```

**Para el equipo:** el microservicio ya corre en la PC de James y se publica en `https://fflx84lw-3000.brs.devtunnels.ms` (valor por defecto de `NEARBY_SMS_GATEWAY_URL`). No hace falta levantarlo: en tu `.env` pon `NEARBY_SMS_PROVIDER=gateway` y `NEARBY_SMS_GATEWAY_API_KEY` (pídele la clave a James; no la subas a git, el repo es público). Si falta la clave, el backend no arranca y lo dice. Si el túnel está apagado, el código no llega por SMS: búscalo en el log del backend o fíjalo con `NEARBY_OTP_DEV_CODE`.

**Para levantarlo tú:** `npm start` en `micro/sms-gateway` (puerto 3000) con el celular conectado, y en el backend `NEARBY_SMS_GATEWAY_URL=http://localhost:3000` con la misma clave que `SERVICE_API_KEY` del microservicio. Los SMS entrantes se reenvían a `FORWARD_URL` (`http://localhost:8080/api/v1/sms/inbound`) con `FORWARD_TOKEN` igual a `NEARBY_SMS_INBOUND_TOKEN` del backend: llegan al backend de quien corre el microservicio.

Si el SMS del código de ingreso falla y hay código fijo (`NEARBY_OTP_DEV_CODE`), igual se puede entrar con ese código; sin código fijo la API responde `422 SMS_SEND_FAILED`. Los avisos del SOS que fallan se registran en el log y no detienen el SOS.

## Variables de entorno

| Variable | Uso | Por defecto en dev |
|---|---|---|
| `NEARBY_JWT_SECRET` | Secreto HS256 (32 caracteres o más) | Valor de desarrollo |
| `NEARBY_SMS_INBOUND_TOKEN` | Token del webhook `/api/v1/sms/inbound` | `dev-sms-token` |
| `NEARBY_SMS_GATEWAY_URL`, `NEARBY_SMS_GATEWAY_API_KEY` | Microservicio SMS (con `NEARBY_SMS_PROVIDER=http` o `gateway`) | `http://localhost:3000` / — |
| `NEARBY_SMS_PROVIDER` / `NEARBY_NOTIFICATIONS_PROVIDER` | Proveedores reales (`http` para SMS) | `console` / `log` |
| `NEARBY_STORAGE_PROVIDER`, `NEARBY_S3_BUCKET`, `NEARBY_S3_PREFIX` | Fotos en S3 | `s3` / — / `risk-photos/` |
| `AWS_ENDPOINT_URL_S3`, `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, `AWS_REGION` | Credenciales S3 (Neon) | — / — / — / `us-east-2` |
| `NEARBY_AI_PROVIDER`, `ANTHROPIC_API_KEY`, `NEARBY_AI_MODEL` | IA con Claude (`anthropic`) | `rules` / — / `claude-haiku-5-5` |
| `GEMINI_API_KEY`, `NEARBY_GEMINI_MODEL`, `NEARBY_GEMINI_TIMEOUT_SECONDS` | IA con Gemini (`NEARBY_AI_PROVIDER=gemini`) | — / `gemini-2.5-flash` / `8` |
| `NEARBY_CORS_ORIGINS` | Orígenes CORS (patrones separados por coma) | `http://localhost:*` |
| `NEARBY_ADMIN_PHONES` | Celulares (separados por coma) del administrador de la plataforma, que aprueba Municipalidades | — |
| `NEARBY_BRIGADE_PHONES` | Opcional, para demos: celulares que entran como brigada. Solo sube a un vecino, nunca baja un rol | — |
| `NEARBY_OTP_DEV_CODE` | Código SMS fijo para pruebas sin SMS. Solo funciona con el perfil `dev` o `local`; con `prod` se ignora | — (aleatorio) |
| `NEARBY_SWAGGER_ENABLED` | Swagger en prod | `false` |
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | Base de datos (perfiles `local` y `prod`); en Neon, el host con `-pooler` | — |
| `DB_MIGRATION_URL` | Conexión directa (sin `-pooler`) para Flyway | `DB_URL` |
| `PORT` | Puerto HTTP | `8080` |

## Seguridad y robustez

- **Autenticación:** JWT HS256 con el rol en el token. El código SMS y el token de refresco se guardan como hash. El token de refresco rota, y si alguien reusa uno ya rotado se cierran todas las sesiones.
- **Límites en memoria:** SOS 10 por minuto, reportes de riesgo 20 por hora y pedidos de código SMS 30 por hora por IP. Además, 5 códigos por hora por celular y 5 intentos por código.
- **Privacidad:**
  - Las direcciones exactas y las fichas médicas solo las ve la brigada.
  - Las vulnerabilidades del inventario de la IA son anónimas para los vecinos.
  - Los logs no guardan celulares completos ni fichas médicas.
- **Trazabilidad:** cada petición tiene un `X-Correlation-Id`, que aparece en el log como `[cid]`.
- **SOS:** los avisos salen después del commit y en segundo plano. Si se cae un proveedor, el SOS no falla.

Las decisiones de diseño de cada fase están en [docs/DECISIONES.md](docs/DECISIONES.md).
