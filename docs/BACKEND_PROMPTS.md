# Nearby: prompts para construir el backend (Spring Boot + PostgreSQL)

Este documento tiene **un prompt de contexto (PROMPT 0)** y **11 prompts por fases**. Están pensados para pegarlos, en orden, en tu asistente de código: Claude Code, Cursor, Copilot Chat o ChatGPT.

---

## Antes de empezar

### 1. Dependencias en Spring Initializr
Las 4 que ya elegiste **no alcanzan** para un backend REST. Esta es la lista completa:

| Dependencia | ¿Ya la tienes? | Para qué |
|---|---|---|
| PostgreSQL Driver | Sí | Conexión a la base de datos |
| Spring Data JPA | Sí | Entidades y repositorios |
| Lombok | Sí | Menos código repetitivo |
| Docker Compose Support | Sí | Levanta PostgreSQL al ejecutar la app |
| **Spring Web** | **Agregar (obligatorio)** | Endpoints REST |
| **Validation** | **Agregar** | `@Valid`, `@NotBlank`, etc. |
| **Spring Security** | **Agregar** | Seguridad |
| **OAuth2 Resource Server** | **Agregar** | Validar JWT (login por SMS) |
| **Flyway Migration** | **Agregar** | Versionar el esquema de la base de datos |
| **Spring Boot Actuator** | **Agregar** | `/actuator/health` |
| Testcontainers | Recomendado | Pruebas con PostgreSQL real |

Configuración sugerida en Initializr:
- **Project:** Maven
- **Language:** Java
- **Java:** 21
- **Group:** `pe.nearby`
- **Artifact:** `nearby-api`
- **Package:** `pe.nearby.api`

### 2. Cómo usar los prompts
1. Crea el proyecto en Initializr, descomprímelo y ábrelo en tu editor.
2. Crea la carpeta `docs/` y guarda allí este archivo y [PLAN.md](PLAN.md). Así el asistente puede leerlos.
3. Pega el **PROMPT 0** una sola vez al inicio de la conversación.
4. Pega **un prompt por fase** y espera a que compile y pasen las pruebas antes de seguir. **No pegues todo junto**: la calidad baja mucho.
5. Si una fase falla, pide: «Corre `./mvnw test`, revisa los errores y corrígelos sin cambiar el contrato de la API».

### 3. Orden y tiempo estimado (dentro de las 48 h)
| Fase | Prompt | Tiempo aprox. |
|---|---|---|
| Base del proyecto | 1 | 1 h |
| Modelo de datos + datos semilla | 2 | 2 h |
| Acceso por SMS + JWT | 3 | 2 h |
| Perfil, contactos, ficha | 4 | 1 h |
| Servicios + Visto Bueno | 5 | 2 h |
| Muro de favores + chat | 6 | 2 h |
| SOS + modos de la zona | 7 | 3 h |
| Emergencia: tablero, recursos, grupos, mapa | 8 | 3 h |
| IA Protocolo Lázaro + SMS comprimido | 9 | 3 h |
| El Niño: radar preventivo + mapa de calor | 10 | 3 h |
| Offline (sincronización), calidad y entrega | 11 | 2 h |

---

## PROMPT 0: Contexto maestro (pegar primero)

```text
Eres un desarrollador backend senior en Java y Spring Boot. Vas a construir el backend de "Nearby",
una app móvil (Flutter) para vecinos de un barrio de Lima, Perú (San Juan de Lurigancho), creada para
una hackathon de 48 horas. Escribe código de producción limpio, simple y probado. Explica poco y
entrega mucho. Todo el texto visible para el usuario (mensajes de error y de validación) va en
ESPAÑOL. El código, los nombres de clases y las variables van en INGLÉS.

## Qué es Nearby
Nearby conecta a los vecinos para obtener productos y servicios recomendados por otros vecinos, con
costos mínimos y sin moverse lejos. Integra intercambios solidarios y se transforma en situaciones
de emergencia para coordinar el apoyo. Tiene TRES MODOS por zona:
1. NORMAL (día a día):
   - Directorio de oficios con "Visto Bueno Vecinal".
   - Muro de cadena de favores (sin dinero).
   - Chat entre vecinos.
   - Mapa.
2. PREVENTION (Fenómeno El Niño, lluvias):
   - Los vecinos reportan riesgos con foto.
   - Una IA agrupa los reportes en zonas de riesgo (ámbar o rojo).
   - Mapa de calor.
   - Coordinación de recursos de mitigación (sacos de arena, motobombas) ANTES del desborde.
3. EMERGENCY (sismo, huaico, inundación):
   - SOS.
   - Tablero humanitario de vecinos vulnerables.
   - Recursos críticos, grupos de apoyo y puntos seguros.
   - Misiones de rescate asignadas por IA.
El botón SOS es la función más importante: debe ser rápida, confiable y funcionar aunque no haya
internet (fallback por SMS).

## Innovaciones que el backend debe soportar
A) "Protocolo Lázaro" (IA de doble vida):
   - En el día a día, y CON CONSENTIMIENTO del vecino, la IA lee las publicaciones del muro, los
     oficios y las reseñas.
   - Con eso construye un "inventario de supervivencia" de capacidades: escalera = rescate en altura,
     generador = energía, "me inyectó" en una reseña = soporte paramédico, "acompañar a mi abuela"
     = adulto mayor en casa, etc.
   - En una emergencia cruza ese inventario con los reportes y asigna automáticamente al vecino
     más cercano que tiene el recurso justo. También crea reportes preventivos de vecinos
     vulnerables que nadie reportó todavía.
   - Sin internet, los reportes se comprimen en un código de 25 caracteres o menos que cabe en un
     SMS. Ejemplo: NB#SOS!H1@LP340$P:ALTA
B) "Radar preventivo de quebradas y aniegos" (El Niño):
   - Crowdsourcing de reportes con foto.
   - Análisis de la foto con IA.
   - Agrupación por cercanía y tiempo, con nivel AMBER o RED y una explicación del patrón en
     lenguaje natural.
   - Mapa de calor.
   - Búsqueda de recursos de mitigación en el inventario para coordinar préstamos preventivos.

## Stack (NO cambiar)
- Java 21 y Spring Boot. Usa la versión que ya está en el pom.xml; no la bajes. Maven (./mvnw).
- Spring Web (MVC), Spring Data JPA (Hibernate), PostgreSQL, Flyway, Validation, Lombok.
- Spring Security con OAuth2 Resource Server (JWT HS256 con un secreto de variable de entorno).
- Spring Boot Docker Compose Support: el archivo compose.yaml en la raíz levanta PostgreSQL solo.
- Actuator (health). springdoc-openapi para Swagger UI: agrega la versión compatible con tu versión
  de Spring Boot.
- Pruebas: JUnit 5, Spring Boot Test, MockMvc y Testcontainers (PostgreSQL).
- Sin MapStruct: mapea con métodos estáticos en los DTO (records).

## Convenciones obligatorias
- Paquete base: pe.nearby.api. Estructura POR FUNCIONALIDAD, no por capa:
  pe.nearby.api.{common, config, auth, user, zone, services, posts, chat, sos, emergency,
  prevention, ai, sms, map, sync, notification, storage}
  Dentro de cada paquete: entidades, repositorios, servicios (lógica), controlador y dto/.
- IDs: UUID generados en la app (@UuidGenerator o UUID.randomUUID()).
- Entidades con Lombok: @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder.
  PROHIBIDO usar @Data o @EqualsAndHashCode en entidades. Relaciones @ManyToOne(fetch = LAZY).
  Evita el problema N+1 con @EntityGraph o JOIN FETCH en las consultas de listados.
- DTOs: Java records (Request y Response). Nunca expongas entidades en los controladores.
- @Transactional en la capa de servicio (readOnly = true en las lecturas).
- JSON en camelCase. Fechas OffsetDateTime en ISO-8601. Zona horaria America/Lima.
- Enums guardados como STRING (@Enumerated(EnumType.STRING)).
- Esquema SOLO por Flyway (src/main/resources/db/migration/V1__..., V2__...).
  spring.jpa.hibernate.ddl-auto=validate.
- Coordenadas: columnas lat y lng (double precision) con índice. Distancias con la fórmula de
  Haversine en una utilidad Java (GeoUtils). Para filtrar por radio en SQL usa primero un recuadro
  (bounding box) y luego Haversine. No uses PostGIS (más simple para 48 h).
- Paginación con Pageable. Respuesta paginada estándar:
  { "content": [...], "page": 0, "size": 20, "totalElements": 0, "totalPages": 0 }
- Errores con ProblemDetail (RFC 9457) desde un @RestControllerAdvice global, con campos extra
  "code" (por ejemplo "ENDORSEMENT_DUPLICATE") y "errors" (lista de errores de validación).
  Mensajes en español.
- Idempotencia offline: todo POST que crea contenido desde el teléfono (SOS, publicaciones,
  reportes, mensajes) acepta "clientId" (UUID que genera el teléfono).
  Restricción única (author_id, client_id).
  Si llega repetido, devuelve el recurso ya creado con 200, no un error. Así el teléfono puede
  reintentar sin duplicar al recuperar la señal.
- Seguridad:
  - Todo bajo /api/v1/** requiere JWT, excepto /api/v1/auth/**, el webhook /api/v1/sms/inbound
    (protegido con un token compartido), /actuator/health y Swagger.
  - Roles: NEIGHBOR, BRIGADIST, DELEGATE, ADMIN.
- Privacidad (regla de oro):
  - La dirección exacta y la ficha médica de una persona vulnerable SOLO las ven BRIGADIST y ADMIN.
  - Los demás vecinos reciben coordenadas aproximadas (redondeadas a 3 decimales, unos 100 m) y
    sin datos personales.
  - Las etiquetas de vulnerabilidad del inventario de IA se anonimizan para NEIGHBOR
    ("Vecino de Mz. D").
- Integraciones externas SIEMPRE detrás de una interfaz, con una implementación "dev" por defecto
  que solo escribe en el log:
  - SmsGateway (envío de SMS),
  - NotificationService (push),
  - PhotoRiskAnalyzer (visión),
  - CapabilityExtractor (IA de texto),
  - StorageService (fotos).
  La app debe funcionar completa en local SIN credenciales externas.
- Configuración en application.yml con perfiles "dev" (por defecto) y "prod".
  Secretos solo por variables de entorno.

## Cómo trabajar
- Al terminar cada fase:
  1. compila con ./mvnw -q verify;
  2. lista los archivos creados o cambiados;
  3. muestra ejemplos curl o un archivo .http (requests/<fase>.http) para probar;
  4. confirma los criterios de aceptación de la fase.
- No inventes funcionalidades fuera de la fase pedida. Si algo es ambiguo, elige lo más simple que
  cumpla el contrato y anótalo en docs/DECISIONES.md.
- El documento docs/BACKEND_PROMPTS.md (anexos A, B y C) tiene los datos semilla, los diccionarios
  de la IA y el formato SMS. Léelo cuando la fase lo indique.

Responde solo "Listo, contexto cargado" y espera la FASE 1.
```

---

## PROMPT 1: Base del proyecto

```text
FASE 1: Base del proyecto.

1. compose.yaml en la raíz:
   - Servicio "postgres" con la imagen postgres:16-alpine.
   - Base de datos nearby, usuario nearby, contraseña nearby.
   - Puerto 5432 y volumen con nombre "nearby-data".
   - Healthcheck con pg_isready.
   Verifica que Spring Boot Docker Compose Support lo levante solo al ejecutar ./mvnw spring-boot:run.
2. pom.xml: agrega lo que falte de la lista del contexto: springdoc-openapi-starter-webmvc-ui
   compatible, y Testcontainers PostgreSQL con su integración spring-boot-testcontainers para las
   pruebas. No cambies la versión de Spring Boot.
3. application.yml:
   - Perfil "dev" activo por defecto, con jpa.hibernate.ddl-auto=validate y
     jpa.open-in-view=false.
   - Jackson: escribir fechas en ISO y zona America/Lima.
   - server.error con include-message.
   - Bloque propio "nearby":
       jwt-secret (env NEARBY_JWT_SECRET; en dev un valor de 32+ caracteres)
       access-token-minutes: 60
       refresh-token-days: 30
       otp: { length: 6, expiry-minutes: 5, max-attempts: 5, max-requests-per-hour: 5,
              dev-fixed-code: "123456" }   (dev-fixed-code solo existe en el perfil dev)
       sms: { inbound-token: env NEARBY_SMS_INBOUND_TOKEN }
       emergency: { auto-activate-sos-count: 5, auto-activate-window-minutes: 10 }
       prevention: { cluster-radius-m: 150, cluster-window-hours: 6 }
       ai: { provider: "rules" }   ("rules" | "anthropic"; con anthropic se leen
                                    ANTHROPIC_API_KEY y nearby.ai.model, por defecto claude-haiku-5-5)
       storage: { local-dir: "./uploads" }
   - Perfil "prod": todo por variables de entorno.
   Mapea "nearby" con @ConfigurationProperties en un record NearbyProperties.
4. common/:
   - ApiError (@RestControllerAdvice) que devuelve ProblemDetail con "code" y "errors" y maneja:
     validación, NotFound (404), Conflict (409), Forbidden (403), BusinessRuleException (422)
     y errores genéricos (500, sin filtrar detalles internos).
   - Excepciones propias: NotFoundException, ConflictException y BusinessRuleException, cada una
     con un código.
   - GeoUtils: haversineMeters(lat1, lng1, lat2, lng2); boundingBox(lat, lng, radiusM);
     approx(double) que redondea a 3 decimales.
   - PageResponse<T> (record) con fábrica desde Page<T>.
   - Clock inyectable (bean Clock con zona America/Lima) para poder probar fechas.
5. config/:
   - SecurityConfig: por ahora permite /api/v1/auth/**, /actuator/health, /v3/api-docs/** y
     /swagger-ui/**. El resto queda autenticado (el JWT se completa en la FASE 3).
     CORS abierto en dev para http://localhost:* (Flutter web).
   - OpenApiConfig con título "Nearby API", versión v1 y esquema bearer.
6. Prueba de humo: @SpringBootTest con Testcontainers (@ServiceConnection) que levanta el contexto
   y llama a /actuator/health → UP.
7. README.md breve: requisitos (Java 21, Docker), cómo correr y URL de Swagger.

Criterios de aceptación:
- ./mvnw spring-boot:run levanta PostgreSQL con Docker y la app.
- http://localhost:8080/actuator/health → {"status":"UP"}.
- http://localhost:8080/swagger-ui.html abre.
- ./mvnw verify en verde.
```

---

## PROMPT 2: Modelo de datos, migraciones y datos semilla

```text
FASE 2: Modelo de datos completo (entidades JPA + migración Flyway V1) y datos semilla.

Crea V1__schema.sql con TODAS las tablas, sus claves foráneas e índices: (zone_id, created_at),
(lat, lng) y las restricciones únicas indicadas. Crea las entidades JPA equivalentes en su paquete.

ENUMS (guardados como texto):
- Role: NEIGHBOR, BRIGADIST, DELEGATE, ADMIN
- ZoneMode: NORMAL, PREVENTION, EMERGENCY
- ModeSource: MUNICIPALITY, AUTO_SOS, USER
- ServiceCategory: PLUMBING, ELECTRICITY, CARPENTRY, LOCKSMITH, TUTORING, PET_CARE, SEWING, OTHER
- PostType: NEED, OFFER
- PostCategory: TOOL_LOAN, TEMPORARY_HELP, MATERIAL_DONATION, SKILL_EXCHANGE, SOLIDARITY_RIDE
- PostStatus: OPEN, MATCHED, DONE, CANCELLED
- SosType: HEALTH, FIRE, ROBBERY, EARTHQUAKE, VIOLENCE, OTHER
- SosStatus: ACTIVE, SAFE, CANCELLED
- ResponseStatus: GOING, DECLINED, ARRIVED
- CheckInStatus: SAFE, NEED_HELP
- VulnerableCategory: ELDERLY_ALONE, DISABILITY, CHILDREN_ALONE, INJURED, PREGNANT, TRAPPED, OXYGEN_DEPENDENT
- Priority: HIGH, MEDIUM
- ReportStatus: PENDING, ON_THE_WAY, ATTENDED
- ReportSource: USER, AI_PREVENTIVE, SMS
- ResourceType (emergencia): WATER, FOOD, MEDICINE, ENERGY, SHELTER, TRANSPORT
- MitigationType (prevención): SANDBAGS, PUMP, ROOF_COVER, SHOVELS, PLANKS, FLASHLIGHTS
- ListingKind: HAVE, NEED
- SafePointType: MEETING_POINT, SHELTER
- CapabilityTag: HEIGHT_RESCUE, RESCUE_TOOLS, BACKUP_POWER, WATER_RESERVE, EVAC_TRANSPORT,
  PARAMEDIC, TECHNICIAN, ELDERLY_AT_HOME, REDUCED_MOBILITY, OXYGEN_DEPENDENT,
  SANDBAGS, PUMP, ROOF_COVER, SHOVELS
- CapabilityKind: RESOURCE, SKILL, VULNERABILITY
- SourceType: POST, SERVICE, REVIEW, RESOURCE_LISTING
- RiskCategory: ACEQUIA_OVERFLOW, STREET_FLOODING, SEEPAGE, RAVINE_ACTIVITY, DRAIN_OVERFLOW, CRACK
- RiskLevel: AMBER, RED
- WeatherLevel: YELLOW, ORANGE, RED

TABLAS / ENTIDADES (campos mínimos; agrega created_at/updated_at con @CreationTimestamp/@UpdateTimestamp):
zone
  - name, district
  - center_lat, center_lng, radius_m
  - mode (ZoneMode, default NORMAL), mode_source, mode_reason, mode_activated_at
  - weather_level (nullable), weather_valid_until (nullable)
street_code
  - zone_id, code (VARCHAR 4, por ejemplo "LP"), name ("Jr. Los Pinos"), lat, lng
  - único (zone_id, code)
app_user
  - phone (único, 9 dígitos), name, role, verified (bool)
  - zone_id, lat, lng, address_text (privada)
  - ai_consent (bool, default false)
  - mode_dismissed_at (nullable: el vecino salió del modo de la zona en su app)
  - local_emergency (bool: el vecino activó el modo emergencia solo para él)
otp_code
  - phone, code_hash, expires_at, attempts, consumed (bool)
refresh_token
  - user_id, token_hash (único), expires_at, revoked
trusted_contact
  - user_id, name, phone, position (1..3)
  - único (user_id, position)
medical_card
  - user_id (único), blood_type, allergies, conditions
  - lives_with (VARCHAR con valores separados por coma, o @ElementCollection en medical_card_lives_with)
device_token
  - user_id, token, platform
neighbor_service    ← NO la llames "Service", para no confundirla con @Service
  - owner_id, title, category, description, price_text, schedule_text
  - lat, lng, available_in_emergency, active
  - endorsement_count (int, default 0)
  - @Version (bloqueo optimista)
endorsement
  - service_id, user_id
  - único (service_id, user_id)
review
  - service_id, author_id, text
post
  - author_id, zone_id, type, category, text, photo_url, lat, lng, status, client_id
  - único (author_id, client_id)
post_response
  - post_id, responder_id
  - único (post_id, responder_id)
conversation
  - post_id (nullable), service_id (nullable), user_a_id, user_b_id, context_text
conversation_message
  - conversation_id, sender_id (nullable para mensajes del sistema), text, client_id
  - único (sender_id, client_id)
sos_alert
  - user_id, zone_id, type (nullable), lat, lng, note, status
  - sms_code, notified_count, client_id, closed_at
  - único (user_id, client_id)
sos_response
  - alert_id, responder_id, status, eta_minutes
  - único (alert_id, responder_id)
check_in
  - user_id, zone_id, status
vulnerable_report
  - zone_id, reporter_id (nullable), category, detail, priority, status
  - assigned_to_id (nullable), assigned_label (texto: "Brigada Z3", "Jorge y Pedro")
  - lat, lng (exactas), address_text
  - source, client_id, source_capability_id (nullable)
resource_listing
  - user_id, zone_id
  - resource_type (nullable), mitigation_type (nullable): exactamente uno de los dos (CHECK)
  - kind, text, quantity, location_text, lat, lng, active
support_group
  - zone_id, name, icon, description, active_now, schedule_text, contact_phone, members_count
support_group_member
  - group_id, user_id
  - único
safe_point
  - zone_id, name, type, capacity, occupied, lat, lng
capability
  - zone_id, user_id (nullable para entidades sin usuario), display_name, zone_label ("Mz. B")
  - tag, kind, source_type, source_id, evidence_text, confidence (0..1)
  - lat, lng, removed_by_user (bool)
  - único (source_type, source_id, tag)
rescue_plan
  - zone_id, generated_at, duration_ms, missions_count, preventive_reports_count
mission_assignment
  - plan_id, report_id, capability_id, user_id, distance_m, reason (texto)
  - notified_at (nullable), status
risk_report
  - zone_id, reporter_id, category, text, photo_url
  - photo_analysis (texto), severity_hint (0..1)
  - lat, lng, address_text, client_id, risk_zone_id (nullable)
  - único (reporter_id, client_id)
risk_zone
  - zone_id, name, level, centroid_lat, centroid_lng
  - report_count, score, pattern_text, homes_estimate, active
mitigation_match
  - risk_zone_id, capability_id, user_id, distance_m, coordinated_at (nullable)
prep_checklist_item
  - user_id, item_key, done
  - único (user_id, item_key)

DATOS SEMILLA (perfil dev): implementa DemoDataSeeder (CommandLineRunner, @Profile("dev"),
idempotente: no hace nada si ya existe la zona). Carga EXACTAMENTE los datos del ANEXO A de
docs/BACKEND_PROMPTS.md: la zona, los códigos de calle, los usuarios, los servicios con sus
contadores de visto bueno, las reseñas, las publicaciones, el historial, los reportes vulnerables,
los recursos, los grupos, los puntos seguros, los reportes de riesgo y los listados de mitigación.
Convierte las coordenadas x,y del anexo a lat/lng con la fórmula del anexo.

Criterios de aceptación:
- Flyway aplica V1 sin errores y Hibernate valida el esquema (ddl-auto=validate).
- Al arrancar en dev existen la zona "Zona 3 · Las Flores" y todos los datos del anexo.
- Una prueba de repositorio verifica las restricciones únicas: endorsement duplicado →
  DataIntegrityViolationException.
```

---

## PROMPT 3: Acceso por celular, código SMS (OTP) y JWT

```text
FASE 3: Autenticación sin contraseña con código SMS y JWT.

Endpoints (públicos):
POST /api/v1/auth/otp/request   { "phone": "987654321" }
  - Valida un celular peruano: ^9\d{8}$. Si no cumple → 400 "Los celulares en Perú tienen 9 dígitos y empiezan con 9".
  - Límite: máximo nearby.otp.max-requests-per-hour por número → 429 "Demasiados intentos, espera unos minutos".
  - Genera un código de 6 dígitos (en dev siempre dev-fixed-code), lo guarda HASHEADO
    (SHA-256 + pepper del secreto JWT) con vencimiento de 5 minutos, invalida los anteriores
    y lo envía por SmsGateway.
  - Respuesta 202 { "expiresInSeconds": 300, "resendInSeconds": 30 }.
    En dev agrega "devCode": "123456".
POST /api/v1/auth/otp/verify    { "phone": "...", "code": "123456" }
  - Incrementa los intentos. Si supera max-attempts → 429.
  - Si el código es incorrecto o venció → 401 con code "OTP_INVALID" y mensaje
    "Código incorrecto. Revisa el SMS e inténtalo otra vez".
  - Si es correcto: lo marca como consumido. Si el usuario no existe, lo crea
    (role NEIGHBOR, verified=false, sin zona).
  - Respuesta 200 { accessToken, refreshToken, expiresIn, isNewUser, user: UserDto }.
POST /api/v1/auth/refresh       { "refreshToken": "..." } → rota el refresh token (revoca el anterior).
POST /api/v1/auth/logout        (autenticado) → revoca los refresh tokens del usuario. Respuesta 204.

JWT:
- HS256 con nearby.jwt-secret.
- Claims: sub = userId, phone, role, zoneId (si existe).
- Configura el Resource Server con NimbusJwtDecoder (clave secreta) y un JwtAuthenticationConverter
  que mapee el claim "role" a la autoridad ROLE_<role>.
- Crea un helper CurrentUser (componente) para obtener el userId del JWT en los servicios.

SmsGateway:
- Interfaz: send(String phone, String text).
- ConsoleSmsGateway (dev): escribe en el log
  "[SMS → 987654321] Tu código Nearby es 123456. No lo compartas."
- Deja preparado (sin implementar) TwilioSmsGateway con @ConditionalOnProperty.

Pruebas (MockMvc + Testcontainers):
- flujo feliz request → verify → acceso a GET /api/v1/me con el token;
- código incorrecto → 401;
- teléfono inválido → 400;
- exceso de solicitudes → 429;
- el refresh token rota.

Criterios: el archivo requests/auth.http muestra el flujo completo con el usuario semilla 987654321
(María Quispe).
```

---

## PROMPT 4: Perfil, ubicación y zona, contactos de confianza y ficha médica

```text
FASE 4: Perfil del vecino.

GET  /api/v1/me
  → UserDto { id, name, phone, role, verified, zone {id,name,mode,modeSource,weatherLevel}|null,
              aiConsent, effectiveMode }
  effectiveMode es el modo que la app debe mostrar:
  - si local_emergency = true → EMERGENCY;
  - si no, si la zona no está en NORMAL y mode_dismissed_at < mode_activated_at (o es null)
    → el modo de la zona;
  - en cualquier otro caso → NORMAL.
PUT  /api/v1/me                         { name } (2 a 60 caracteres)
PUT  /api/v1/me/location                { lat, lng, addressText }
  → asigna la zona más cercana cuyo centro esté a radius_m o menos
    (si no hay ninguna → 422 "Nearby aún no llega a tu zona").
  → devuelve UserDto. La dirección nunca se devuelve a otros usuarios.
GET/PUT /api/v1/me/trusted-contacts     [ { name, phone, position } ]   máximo 3 → 422 si hay más.
GET/PUT /api/v1/me/medical-card         { bloodType, allergies, conditions, livesWith: [ELDERLY, CHILDREN, DISABILITY, PETS] }
PUT  /api/v1/me/device-token            { token, platform }
PUT  /api/v1/me/settings                { aiConsent }
  → si aiConsent pasa a false: marca removed_by_user = true en las capabilities del usuario
    (y deja de extraerlas).
GET  /api/v1/me/ai-learnings            → capabilities del usuario: [ { id, tag, label, evidenceText, sourceType } ]
DELETE /api/v1/me/ai-learnings/{id}     → removed_by_user = true (nunca se vuelve a crear para esa fuente y etiqueta). 204.
GET/PUT /api/v1/me/prep-checklist       [ { itemKey: "GUTTERS"|"DOCS_HIGH"|"GO_BAG"|"SAFE_POINT", done } ]

Reglas:
- Solo el propio usuario lee y edita su ficha médica.
- Un BRIGADIST puede leer la ficha de una persona solo si ella tiene un SOS ACTIVE o un
  vulnerable_report abierto. Endpoint: GET /api/v1/brigade/medical-cards/{userId}
  (rol BRIGADIST o ADMIN); si no se cumple → 403.

Labels en español para los tags (usa un enum con label): HEIGHT_RESCUE "Rescate en altura",
RESCUE_TOOLS "Herramientas de rescate", BACKUP_POWER "Energía de respaldo", WATER_RESERVE "Reserva de agua",
EVAC_TRANSPORT "Transporte de evacuación", PARAMEDIC "Soporte paramédico", TECHNICIAN "Brigadista técnico",
ELDERLY_AT_HOME "Adulto mayor en casa", REDUCED_MOBILITY "Movilidad reducida", OXYGEN_DEPENDENT "Depende de oxígeno",
SANDBAGS "Sacos de arena", PUMP "Motobomba", ROOF_COVER "Calaminas y plásticos", SHOVELS "Palas y picos".

Pruebas:
- máximo 3 contactos;
- la ubicación asigna la Zona 3;
- effectiveMode en los 4 casos;
- borrar un aprendizaje de la IA.
```

---

## PROMPT 5: Servicios de proximidad y Visto Bueno Vecinal

```text
FASE 5: Módulo 1, economía local.

GET  /api/v1/services?category=&q=&lat=&lng=&radiusM=2000&emergencyOnly=false&sort=endorsements|distance&page=&size=
  → PageResponse<ServiceSummaryDto { id, ownerName, title, category, categoryLabel, distanceM,
     endorsementCount, endorsedByMe, availableInEmergency, priceText }>
  - "q" busca sin distinguir mayúsculas ni tildes en título, descripción y nombre del dueño
    (usa unaccent o normaliza en Java).
  - Si no envían lat/lng, usa la ubicación del usuario.
GET  /api/v1/services/{id}
  → ServiceDetailDto: lo anterior + description, scheduleText, ownerVerified, zoneName,
    endorserInitials (hasta 4), latestReviews (3), ownerPhoneMasked ("987 *** 120").
POST /api/v1/services                   (ofrecer mi oficio) { title, category, description, priceText, scheduleText, availableInEmergency }
PUT/DELETE /api/v1/services/{id}        solo el dueño.
POST /api/v1/services/{id}/endorsements      → Visto Bueno Vecinal
DELETE /api/v1/services/{id}/endorsements/me
GET/POST /api/v1/services/{id}/reviews       { text } (5 a 500 caracteres)
POST /api/v1/services/{id}/contact           → crea u obtiene la conversación con el dueño (para el botón "Mensaje")

REGLAS DEL VISTO BUENO (códigos de error exactos):
- El usuario debe estar verified → 403 ENDORSEMENT_NOT_VERIFIED "Solo vecinos verificados pueden dar visto bueno".
- Debe estar en la MISMA zona que el dueño → 403 ENDORSEMENT_OTHER_ZONE.
- No puede dar visto bueno a su propio servicio → 422 ENDORSEMENT_SELF.
- Uno por persona → 409 ENDORSEMENT_DUPLICATE.
- Actualiza endorsement_count dentro de la misma transacción (con @Version). Si hay conflicto
  optimista, reintenta una vez.
- Cada reseña nueva y cada servicio creado o editado publica un evento de dominio
  (ApplicationEventPublisher) llamado ContentCreatedEvent(sourceType, sourceId). La IA lo usará en la FASE 9.

Pruebas: las 4 reglas del visto bueno; búsqueda por "gasfitero" encuentra a Julio Quispe;
orden por distancia.
```

---

## PROMPT 6: Muro de favores (cadena de favores) y chat

```text
FASE 6: Módulo 2, economía solidaria.

POST /api/v1/posts                      { clientId, type, category, text?, photoUrl?, lat?, lng? }
  - Si no hay texto, genera uno: "Necesito: préstamo de herramientas" u "Ofrezco: …".
  - La ubicación por defecto es la del usuario.
  - Es idempotente por clientId.
  - Publica ContentCreatedEvent(POST, id).
GET  /api/v1/posts?type=&category=&radiusM=1000&page=&size=
  → PostDto { id, type, category, categoryLabel, text, authorName, authorInitials, distanceM,
              createdAt, respondedByMe, status }
GET  /api/v1/posts/{id}
PATCH /api/v1/posts/{id}/status         { status }  solo el autor.
DELETE /api/v1/posts/{id}               solo el autor (borrado lógico: CANCELLED).
POST /api/v1/posts/{id}/responses       ("Yo te ayudo" / "Me interesa")
  → crea post_response (único), crea u obtiene la conversación con el autor y agrega un mensaje
    del sistema:
    "Le ofreciste ayuda: «texto del post»" (NEED) o "Te interesa: «texto»" (OFFER).
  → devuelve { conversationId }.
  → notifica al autor (NotificationService).
GET  /api/v1/neighborhood/stats         → { favorsThisWeek, monthlyGoal: 180, myFavors } (favores = post_response de los últimos 7 días en la zona)

CHAT:
GET  /api/v1/conversations              → [ { id, otherUserName, otherInitials, lastMessage, lastAt, unread } ]
GET  /api/v1/conversations/{id}/messages?after=<ISO>&limit=50   (solo participantes → 403)
POST /api/v1/conversations/{id}/messages { clientId, text (1..1000) }   idempotente por clientId
  → notifica al otro participante.
Tiempo real: para la hackathon, POLLING con "after". Agrega también un endpoint SSE opcional
GET /api/v1/conversations/{id}/stream (SseEmitter) que emita los mensajes nuevos.

NotificationService (interfaz):
- notifyUser(userId, title, body, data);
- notifyZone(zoneId, title, body, data, Predicate<User> filter).
- LoggingNotificationService (dev). Deja FcmNotificationService con @ConditionalOnProperty
  (Firebase Admin SDK, opcional).

Pruebas:
- idempotencia (mismo clientId → mismo id);
- responder un post crea la conversación con el mensaje del sistema;
- un no participante no puede leer el chat.
```

---

## PROMPT 7: SOS, alertas a vecinos y modos de la zona

```text
FASE 7: El botón SOS y los modos (NORMAL, PREVENTION, EMERGENCY).

SOS (lo más crítico: responde en menos de 300 ms y nunca debe fallar por un servicio externo;
las notificaciones van @Async después de confirmar la transacción con
@TransactionalEventListener(AFTER_COMMIT)):
POST /api/v1/sos                        { clientId, lat, lng, type?, note? }
  - Idempotente por clientId.
  - Crea sos_alert ACTIVE y calcula sms_code con el codec de la FASE 9 (por ahora deja un
    TODO que use SosCodec).
  - Después de confirmar:
    - notifica a los vecinos a 500 m o menos (por la última ubicación conocida) y a los
      BRIGADIST de la zona;
    - envía SMS a los contactos de confianza:
      "NEARBY SOS: {nombre} necesita ayuda. Ubicación: https://maps.google.com/?q={lat},{lng} · Código {smsCode}".
  - Respuesta 201 { id, status, notifiedNeighbors, contactsNotified, brigadeNotified, smsCode, createdAt }.
PATCH /api/v1/sos/{id}                  { type }  (el usuario elige el tipo durante la cuenta regresiva o después)
POST /api/v1/sos/{id}/safe              → SAFE, closed_at; avisa a los contactos "{nombre} está a salvo".
POST /api/v1/sos/{id}/cancel            → CANCELLED (no envía nada más).
GET  /api/v1/sos/{id}                   → detalle con responders [ { name, status, distanceM, etaMinutes } ].
GET  /api/v1/sos/nearby?radiusM=1000    → alertas ACTIVE cercanas, para quien puede ayudar:
     { id, userName, type, distanceM, minutesAgo, goingCount, approxLat, approxLng }.
POST /api/v1/sos/{id}/responses         { status: GOING|DECLINED|ARRIVED }
  - GOING calcula eta_minutes = distancia / 80 m por minuto (caminando).
  - DECLINED notifica a otros vecinos más lejanos (radio 1000 m).
  - ARRIVED avisa a la persona y a la brigada.

ACTIVACIÓN AUTOMÁTICA: si en la zona hay al menos nearby.emergency.auto-activate-sos-count alertas
ACTIVE dentro de la ventana de minutos configurada → zone.mode = EMERGENCY, mode_source = AUTO_SOS,
y se notifica a la zona.

MODOS DE LA ZONA:
POST   /api/v1/admin/zones/{id}/mode    (ADMIN o DELEGATE) { mode: NORMAL|PREVENTION|EMERGENCY, reason, weatherLevel?, validUntil? }
  - Guarda mode_source = MUNICIPALITY y mode_activated_at = ahora, y notifica a la zona:
    "Modo emergencia activo: {reason}" o "Alerta por lluvias · nivel {weatherLevel}".
GET    /api/v1/zones/current            → { id, name, mode, modeSource, modeReason, modeActivatedAt, weatherLevel, weatherValidUntil }
POST   /api/v1/me/mode                  { action: "DISMISS" | "ACTIVATE_LOCAL_EMERGENCY" | "CLEAR_LOCAL" }
  - DISMISS: el vecino vuelve al modo normal SOLO en su app (mode_dismissed_at = ahora).
    Sigue recibiendo las notificaciones críticas (SOS a 500 m y cambios de modo de la zona).
  - ACTIVATE_LOCAL_EMERGENCY: local_emergency = true (lo usa el botón "Activar modo emergencia" de la pantalla Alerta enviada).
  - CLEAR_LOCAL: local_emergency = false.
  - Siempre devuelve el UserDto con effectiveMode.
  - Si la zona vuelve a cambiar de modo después de DISMISS, el vecino lo vuelve a ver
    (porque mode_activated_at > mode_dismissed_at).
POST   /api/v1/check-ins                { status: SAFE|NEED_HELP } → si es SAFE, avisa a los contactos de confianza por SMS.

Pruebas:
- crear un SOS notifica (mock de NotificationService y de SmsGateway);
- idempotencia;
- la activación automática con 5 SOS;
- DISMISS más una nueva activación;
- una falla del SmsGateway NO hace fallar el POST /sos.
```

---

## PROMPT 8: Emergencia: tablero humanitario, recursos, grupos de apoyo y mapa

```text
FASE 8: Funcionalidades del modo emergencia.

TABLERO HUMANITARIO:
GET  /api/v1/zones/{id}/dashboard       → { reportsTotal, pending, attended, safeCheckIns, activeSos }
GET  /api/v1/vulnerable-reports?status=&page=
  → VulnerableReportDto { id, category, categoryLabel, detail, priority, status, assignedLabel,
     source, createdAt, addressText, lat, lng }
  - PRIVACIDAD: si el usuario NO es BRIGADIST ni ADMIN:
    - addressText = solo la manzana o la calle sin número ("Mz. C" o "Jr. Los Pinos");
    - lat y lng aproximadas;
    - sin datos del reportante.
POST /api/v1/vulnerable-reports         { clientId, category, detail?, priority?, lat?, lng?, addressText? }  idempotente.
  - Notifica a los BRIGADIST de la zona.
POST /api/v1/vulnerable-reports/{id}/assign      ("Voy yo") → ON_THE_WAY, assigned_to = yo, assigned_label = mi nombre.
PATCH /api/v1/vulnerable-reports/{id}/status     { status } (asignado o BRIGADIST).

RECURSOS CRÍTICOS (economía solidaria en emergencia):
GET  /api/v1/resources/summary          → [ { type, label, have, need } ] por ResourceType en la zona (suma quantity, o cuenta si quantity es null).
GET  /api/v1/resources?type=&kind=      → listados.
POST /api/v1/resources                  { clientId?, resourceType, kind, text, quantity?, locationText? }

GRUPOS DE APOYO:
GET  /api/v1/support-groups             → con membersCount, activeNow y joinedByMe.
POST /api/v1/support-groups/{id}/members   (unirme)   ·   DELETE .../members/me
GET  /api/v1/services?emergencyOnly=true   (ya existe: vecinos con oficio disponibles).

PUNTOS SEGUROS Y MAPA:
GET  /api/v1/safe-points
GET  /api/v1/map/pins?layer=daily|emergency|prevention&radiusM=1500
  → [ { id, kind, lat, lng, icon, title, subtitle, refType, refId } ]
  - daily: servicios (kind "svc"), posts NEED ("need") y OFFER ("offer").
  - emergency: vulnerable_report abiertos ("vuln", aproximados para NEIGHBOR), safe_point ("safe"),
    resource_listing HAVE ("res"), support_group ("group").
  - prevention: risk_report ("risk"), capabilities SANDBAGS, PUMP, ROOF_COVER o SHOVELS ("mitig")
    y safe_point ("safe"). Se completa en la FASE 10.
  El campo "icon" usa los nombres de Material Symbols que usa la app: plumbing, elderly, flag,
  water_drop, medical_services, flood, landslide, inventory_2, etc.

Pruebas:
- un NEIGHBOR no ve la dirección exacta y un BRIGADIST sí;
- "Voy yo" cambia el estado;
- el resumen de recursos;
- las capas del mapa.
```

---

## PROMPT 9: IA "Protocolo Lázaro" + SOS comprimido para SMS

```text
FASE 9: Inventario de supervivencia, misiones de rescate automáticas y codec SMS.
Lee el ANEXO B (diccionarios) y el ANEXO C (formato SMS) de docs/BACKEND_PROMPTS.md.

1) EXTRACCIÓN DE CAPACIDADES (IA silenciosa del día a día)
- Interfaz CapabilityExtractor:
  List<ExtractedCapability> extract(String text, SourceType source)
  con ExtractedCapability(tag, confidence, evidence).
- RuleBasedCapabilityExtractor (por defecto, nearby.ai.provider=rules):
  - normaliza el texto (minúsculas, sin tildes);
  - busca las palabras clave del ANEXO B (confidence 0.7);
  - además, por categoría de servicio: ELECTRICITY o PLUMBING → TECHNICIAN (confidence 0.9).
- AnthropicCapabilityExtractor (si nearby.ai.provider=anthropic):
  - llama a la Messages API de Anthropic con el modelo de nearby.ai.model (por defecto
    claude-haiku-5-5);
  - pide SOLO JSON con la forma
    { "capabilities": [ { "tag": <CapabilityTag>, "confidence": 0..1, "evidence": "<frase literal>" } ] }
    y un system prompt que explique cada tag;
  - valida el JSON contra el enum (descarta tags desconocidos).
  - Si falla o vence el tiempo (3 s), usa el extractor de reglas como respaldo.
  - Usa el SDK oficial de Java de Anthropic o RestClient; revisa la documentación oficial para la
    forma exacta de la petición. NUNCA registres la API key en el log.
- CapabilityIndexer:
  - escucha ContentCreatedEvent con @TransactionalEventListener(AFTER_COMMIT) y @Async;
  - SOLO procesa si el autor tiene ai_consent = true;
  - hace upsert en la tabla capability por (source_type, source_id, tag), respetando
    removed_by_user (nunca recrea algo que el vecino borró);
  - copia lat, lng y zone_label del autor.
- POST /api/v1/admin/ai/reindex (ADMIN) reprocesa todo el contenido de la zona.

2) INVENTARIO
GET /api/v1/ai/inventory?kind=RESOURCE|SKILL|VULNERABILITY
  → [ { id, tag, label, kind, displayName, zoneLabel, evidenceText, sourceType, sourceAgo, lat, lng } ]
  - Para NEIGHBOR, las VULNERABILITY van anonimizadas:
    displayName = "Vecino de {zoneLabel}", sin evidencia y con coordenadas aproximadas.
GET /api/v1/ai/inventory/snapshot       → versión compacta para guardar en el teléfono
  (para la asignación offline en Flutter) con ETag; responde 304 si no cambió.
GET /api/v1/ai/inventory/stats          → { resources, skills, preventiveAlerts }

3) PLAN DE RESCATE (misiones automáticas)
POST /api/v1/ai/rescue-plans            (BRIGADIST, DELEGATE o ADMIN; también se dispara al pasar la zona a EMERGENCY)
  Algoritmo (mide la duración en ms y guárdala en rescue_plan.duration_ms):
  a) Reportes preventivos: por cada capability VULNERABILITY (sin removed_by_user) que NO tenga un
     vulnerable_report abierto a menos de 60 m:
     - crea un vulnerable_report con source = AI_PREVENTIVE, prioridad HIGH, categoría
       ELDERLY_AT_HOME→ELDERLY_ALONE, REDUCED_MOBILITY→DISABILITY, OXYGEN_DEPENDENT→OXYGEN_DEPENDENT;
     - detail = "Nadie lo ha reportado. La IA lo sabe por el {fuente} ({hace X}): «{evidencia}»."
  b) Para cada vulnerable_report abierto, usa MISSION_NEEDS del ANEXO B. Para cada necesidad, toma
     la capability RESOURCE o SKILL más cercana (Haversine), sin repetir persona dentro de la misma
     misión. Guarda mission_assignment con distance_m y
     reason = "{fuente} {hace X}: «{evidencia}»".
  Respuesta: { planId, durationMs, missions: [ { reportId, reportSummary, assignments: [ { userName, label, distanceM, reason } ] } ], preventiveReports: n }
GET  /api/v1/ai/rescue-plans/latest
POST /api/v1/ai/missions/{reportId}/notify  → notifica a los asignados ("Te necesitan a {d} m: {categoría}. Llevas: {label}"),
     pone el reporte en ON_THE_WAY con assigned_label "Jorge y Pedro" y marca notified_at.

4) CLASIFICADOR DE TEXTO (micrófono inteligente)
POST /api/v1/ai/classify                { text, mode: NORMAL|PREVENTION|EMERGENCY }
  → { type, category, priority, location, chips: [..] }, con las mismas reglas del ANEXO B (sección classify).
  Ejemplo:
    "Por favor ayúdenme, se cayó la pared de mi casa en Los Pinos 340 y mi papá está herido de un brazo" (EMERGENCY)
    → { type:"VULNERABLE", category:"INJURED", priority:"HIGH", location:"Jr. Los Pinos 340" }.
  (El teléfono hace lo mismo offline; este endpoint sirve para validar y para la versión web.)

5) CODEC SMS (ANEXO C)
- SosCodec.encode(...) y SosCodec.decode(code), con pruebas unitarias exhaustivas.
  Las calles se resuelven con la tabla street_code de la zona.
- POST /api/v1/sms/encode               { category|sosType, people, addressText|lat/lng, priority } → { code, length }
- POST /api/v1/sms/decode               { code } → { type, typeLabel, people, addressText, lat, lng, priority } o 422 SMS_CODE_INVALID.
- POST /api/v1/sms/inbound              (webhook de la pasarela SMS; header X-Nearby-Token = nearby.sms.inbound-token) { from, body }
  - si el body contiene un código válido, lo decodifica, crea un vulnerable_report con source = SMS
    (y busca al usuario por teléfono) y notifica a la brigada. Responde 200 siempre que el token
    sea válido.
- Completa el TODO de la FASE 7: el sms_code del SOS se calcula con SosCodec.

Pruebas:
- el extractor encuentra los 14+ hallazgos de los datos semilla;
- respeta el consentimiento y el borrado;
- el plan de rescate asigna a Jorge Paredes (escalera) y a Pedro Ramos (palanca) para la
  Sra. Teresa, y a Taller Mendoza (generador) para el paciente con oxígeno;
- se crean 2 reportes preventivos;
- encode y decode de ida y vuelta para todos los tipos;
- un código mayor a 25 caracteres → error;
- el webhook crea el reporte.
```

---

## PROMPT 10: Fenómeno El Niño: radar preventivo, mapa de calor y mitigación

```text
FASE 10: Modo prevención (El Niño). Lee el ANEXO B (sección prevención).

1) REPORTES DE RIESGO (vecinos como sensores)
POST /api/v1/risk-reports   multipart/form-data: data (JSON { clientId, category?, text?, lat?, lng?, addressText? }) + photo (opcional, jpg/png, máx. 5 MB)
  - Guarda la foto con StorageService:
    - LocalStorageService en dev, en nearby.storage.local-dir, servida en /files/{nombre};
    - deja una interfaz para S3 en prod.
  - Analiza la foto con PhotoRiskAnalyzer:
    - RuleBasedPhotoRiskAnalyzer (dev) devuelve el texto por categoría del ANEXO B (PHOTO_AI) y
      severityHint;
    - AnthropicPhotoRiskAnalyzer (si nearby.ai.provider=anthropic) envía la imagen en base64 a un
      modelo con visión y pide JSON:
      { "category": <RiskCategory>, "waterLevelCm": int|null, "severity": 0..1, "summary": "<1 frase en español>" }.
      Si no viene category, usa la que propone la IA (por defecto STREET_FLOODING).
  - Es idempotente por clientId.
  - Después de confirmar, ejecuta RiskClusteringService.recompute(zoneId).
  - Respuesta 201 { report, photoAnalysis, riskZone: RiskZoneDto|null, escalated: boolean }.
    "escalated" es true si la zona del reporte subió a RED con este reporte. ES EL MOMENTO WOW DE LA DEMO.
GET /api/v1/risk-reports?hours=6        → reportes recientes de la zona.

2) AGRUPACIÓN Y NIVEL (RiskClusteringService)
- Toma los risk_report de la zona de las últimas nearby.prevention.cluster-window-hours horas.
- Agrupa por enlace simple: dos reportes van al mismo grupo si están a cluster-radius-m (150 m)
  o menos. Solo cuentan los grupos de 2 o más reportes.
- score = suma de los pesos por categoría (ANEXO B).
  level = RED si hay 3 o más reportes o si score >= 4; si no, AMBER.
- name = calle más común sin número; usa la street_code más cercana al centroide si no hay texto.
- pattern_text con la plantilla del ANEXO B. Ejemplo:
  "3 vecinos de Jr. Los Pinos reportaron filtración y acequia al ras en la última hora: la acequia podría desbordarse y anegar la calle."
- homes_estimate = 6 + 4 × reportes.
- Mitigación: según MITIGATION_NEEDS, busca la capability más cercana de cada necesidad
  (SANDBAGS, PUMP, ROOF_COVER, SHOVELS, EVAC_TRANSPORT), agrupa por persona y guárdalo en mitigation_match.
- Haz upsert de risk_zone (identifícalas por el conjunto de reportes o por la cercanía del centroide)
  y desactiva las zonas que ya no tienen grupo.
- Si una zona pasa de AMBER a RED, o aparece directamente en RED, notifica a los vecinos a 300 m
  o menos con el texto de alerta del ANEXO B:
  "Se detectó riesgo de {riesgo} en {calle}. {Vecino} tiene {recursos} a {d} m. ¿Coordinamos el préstamo preventivo?"
  (si el recurso es transporte: "¿Coordinamos la evacuación preventiva de adultos mayores y niños?").

3) ENDPOINTS DEL RADAR
POST /api/v1/risk-zones/analyze         → recalcula y devuelve las zonas (botón "Analizar con IA"; incluye durationMs).
GET  /api/v1/risk-zones                 → [ RiskZoneDto { id, name, level, centroidLat, centroidLng, reportCount, pattern, homesEstimate, alertText,
                                            reports: [ { category, label, text, authorName, minutesAgo, hasPhoto } ],
                                            mitigation: [ { userName, labels, distanceM, evidence, coordinated } ] } ]
POST /api/v1/risk-zones/{id}/coordinate → crea u obtiene la conversación con el primer vecino de mitigation, agrega el mensaje del sistema
                                           "Préstamo preventivo para {zona}: {recursos}", marca coordinated_at,
                                           notifica a las viviendas cercanas y devuelve { conversationId, notifiedHomes }.
GET  /api/v1/map/heat                   → [ { lat, lng, radiusM: RED 220 / AMBER 150, level, label: "Riesgo inminente · Jr. Los Pinos" } ]
Completa la capa "prevention" de /api/v1/map/pins.

4) MITIGACIÓN
GET  /api/v1/mitigation/summary         → [ { type, label, have, need } ] por MitigationType.
GET/POST /api/v1/mitigation             → resource_listing con mitigation_type (Necesito / Ofrezco).

Pruebas:
- con los datos semilla, "Quebrada Canto Grande" queda en RED (3 reportes) y "Jr. Los Pinos" en
  AMBER (2 reportes);
- al publicar un tercer reporte de filtración en Jr. Los Pinos 330 → escalated = true y la zona
  pasa a RED;
- la mitigación sugerida para Jr. Los Pinos es la Familia Ríos (sacos de arena + motobomba) a unos
  150 m;
- la coordinación crea el chat;
- el mapa de calor devuelve 2 zonas.
```

---

## PROMPT 11: Offline (sincronización), seguridad final, calidad y entrega

```text
FASE 11: Cierre.

1) SINCRONIZACIÓN PARA OFFLINE (la app Flutter guarda la zona en el teléfono)
GET /api/v1/sync?since=<ISO opcional>
  → { serverTime, zone, services[], posts[], safePoints[], supportGroups[], streetCodes[],
      inventorySnapshot, riskZones[], deleted: { posts:[ids], services:[ids] } }
  Solo devuelve lo modificado desde "since", con un límite de tamaño razonable.
  Los datos de vulnerables siguen la regla de privacidad del rol.
POST /api/v1/sync/outbox  [ { kind: "POST"|"SOS"|"VULNERABLE_REPORT"|"RISK_REPORT"|"MESSAGE"|"CHECK_IN", payload } ]
  → procesa en orden, cada uno idempotente por clientId, y devuelve [ { clientId, status: "CREATED"|"DUPLICATE"|"ERROR", id?, error? } ].
  Sirve para enviar todo lo que quedó en cola al volver la señal.

2) SEGURIDAD Y ROBUSTEZ
- Rate limit simple en memoria (bucket por usuario) para POST /sos (10/min),
  /risk-reports (20/h) y /auth/otp/request.
- Valida los tamaños de todos los textos y sanea las rutas de archivos.
- Agrega índices que falten con V2__indexes.sql y revisa las consultas N+1 con
  spring.jpa.properties.hibernate.generate_statistics en una prueba.
- Logs con un correlationId por request (MDC) y sin datos personales: ni teléfonos completos ni
  ficha médica.

3) CALIDAD Y ENTREGA
- Cobertura de pruebas de la lógica clave: codec SMS, clasificador, extractor, plan de rescate,
  agrupación de riesgo, visto bueno, modos y privacidad.
- Carpeta requests/ con archivos .http para cada módulo, que sirva como guion de la demo.
  Debe reproducir:
  login → SOS → modo emergencia → plan de rescate IA → SMS decode → modo prevención →
  reporte de riesgo escalado → coordinar.
- Dockerfile multi-stage (temurin 21: build con ./mvnw package y runtime JRE) y compose.prod.yaml
  (app + postgres) para desplegar en un VPS o en Railway o Render.
- README completo:
  - arquitectura (diagrama en texto), cómo correr, variables de entorno y roles;
  - usuarios demo (ANEXO A);
  - código OTP de dev;
  - guion de la demo;
  - qué se simula (SMS, push y visión en modo "rules") y cómo activar los proveedores reales.
- docs/API.md: tabla de todos los endpoints con método, ruta, rol y descripción.

Criterios de aceptación: ./mvnw verify en verde; docker compose -f compose.prod.yaml up levanta todo;
el guion .http completo funciona contra una base recién creada.
```

---

# ANEXOS (referencia para el asistente)

## ANEXO A: Datos semilla (Zona 3 · Las Flores, San Juan de Lurigancho)

**Conversión de coordenadas** del prototipo, donde x,y van de 0 a 100 en una cuadrícula y cada unidad equivale a unos 12 m. Las coordenadas son referenciales:
```
lat = -11.9790 + (50 - y) * 0.000108
lng = -77.0030 + (x - 50) * 0.000110
```

**Zona:** "Zona 3 · Las Flores", distrito "San Juan de Lurigancho", centro (x50, y50), radio 1500 m, modo NORMAL.

**Códigos de calle:**

| Código | Nombre | x | y |
|---|---|---|---|
| LP | Jr. Los Pinos | 64 | 61 |
| LF | Jr. Las Flores | 50 | 50 |
| LR | Pje. Las Rosas | 40 | 74 |
| AP | Av. Principal | 80 | 36 |
| QC | Quebrada Canto Grande | 18 | 20 |

**Usuarios:** el teléfono es el login; todos verified=true salvo que se indique otra cosa; ai_consent=true; en dev, OTP 123456.

| Nombre | Teléfono | Rol | x,y | Notas |
|---|---|---|---|---|
| María Quispe | 987654321 | NEIGHBOR | 50,50 | Usuaria demo. Contactos: Mamá 987000120, Carlos 956000447, Sra. Rosa 944000908. Ficha: O+, alergia penicilina, vive con adulto mayor |
| Brigada Z3 (Coordinador) | 999000111 | BRIGADIST | 16,26 | |
| Municipalidad SJL | 999000000 | ADMIN | 50,50 | |
| Julio Quispe | 911000001 | NEIGHBOR | 28,30 | Servicio: Gasfitería, 48 vistos buenos, «Desde S/ 35», Lun–Sáb 7–19, emergencias sí |
| Luis Mendoza | 911000002 | NEIGHBOR | 72,24 | Electricidad, 41, «Desde S/ 40», emergencias sí |
| Rosa Flores | 911000003 | NEIGHBOR | 60,66 | Clases de matemática, 37, «S/ 20 la hora» |
| Pedro Ramos | 911000004 | NEIGHBOR | 28,42 | Carpintería, 33, «Según el trabajo», emergencias sí. Descripción: «Carpintero con herramientas de palanca y gata hidráulica» |
| Miguel Ccori | 911000005 | NEIGHBOR | 16,46 | Cerrajería, 29, «Desde S/ 25», 24 h, emergencias sí |
| Carmen Huamán | 911000006 | NEIGHBOR | 55,44 | Costura y arreglos, 26 |
| Ana Torres | 911000007 | NEIGHBOR | 48,46 | Paseo de mascotas, 22 |
| Elena Vargas | 911000008 | NEIGHBOR | 46,56 | |
| Jorge Paredes | 911000009 | NEIGHBOR | 37,30 | |
| Familia Ríos | 911000010 | NEIGHBOR | 76,40 | (cochera en x72,y52) |
| Lucía Mamani | 911000011 | NEIGHBOR | 44,50 | |
| Óscar Díaz | 911000012 | NEIGHBOR | 20,60 | |
| Carla Soto | 911000013 | NEIGHBOR | 54,27 | |
| Familia Soto | 911000014 | NEIGHBOR | 57,21 | |
| Taller Mendoza | 911000015 | NEIGHBOR | 61,16 | |
| Teresa Rojas | 911000016 | NEIGHBOR | 34,34 | 82 años |

**Reseñas** (en el servicio de Julio Quispe): Elena Vargas «Llegó a la hora, explicó todo y cobró lo justo.»; Jorge Paredes «Vecino de confianza. Volvió al día siguiente a revisar.»; Teresa Rojas «Me atendió un domingo por una urgencia.» En el servicio de Rosa Flores: Teresa Rojas «Gracias Rosa por inyectarme cuando me enfermé».

**Publicaciones del muro (recientes):**
| Autor | Tipo | Categoría | Texto |
|---|---|---|---|
| Elena Vargas | NEED | TOOL_LOAN | Necesito un taladro por 2 horas para colgar una repisa |
| Jorge Paredes | OFFER | SOLIDARITY_RIDE | Voy al mercado en mi mototaxi a las 4 pm, tengo 2 asientos libres |
| Familia Ríos | OFFER | MATERIAL_DONATION | Regalo 10 ladrillos y medio saco de cemento |
| Lucía Mamani | NEED | TEMPORARY_HELP | ¿Alguien puede acompañar a mi abuela 1 hora? No puede caminar sola |
| Óscar Díaz | OFFER | SKILL_EXCHANGE | Enseño a usar el celular a cambio de ayuda con mi jardín |
| Carla Soto | NEED | SOLIDARITY_RIDE | Necesito que lleven a mi papá a su cita médica, usa silla de ruedas |

**Historial más antiguo** (con created_at en el pasado; alimenta la IA):
| Autor | Hace | Texto | Ubicación (x,y) |
|---|---|---|---|
| Jorge Paredes | 21 días | Presto escalera grande de 6 metros y taladro | 37,30 |
| Taller Mendoza | 60 días | Presto mi generador eléctrico para la fiesta del barrio | 61,16 |
| Familia Soto | 35 días | Busco quien me ayude a cargar el balón de oxígeno de mi papá | 57,21 |
| Óscar Díaz | 7 días | Regalo botiquín completo; soy técnico en enfermería jubilado | 20,60 |
| Familia Ríos | 1 día | Tenemos tanque de agua de 1100 litros, pueden sacar si se corta | 76,40 |
| Familia Ríos | 120 días | Guardamos 20 sacos de arena y una motobomba del último invierno; si alguien necesita, avisen | 72,52 |
| Pedro Ramos | 60 días | Me sobraron calaminas y plástico grueso de la obra, los regalo | 28,42 |
| Óscar Díaz | 21 días | Presto palas y picos para limpiar la acequia | 20,60 |
| María Quispe | 60 días | Ofrezco ayuda con primeros auxilios, hice el curso de la brigada | 50,50 |

**Reportes vulnerables (emergencia):**
| Categoría | Dirección | Detalle | Prioridad | Estado | x,y |
|---|---|---|---|---|---|
| ELDERLY_ALONE | Mz. C lote 12 | La Sra. Teresa (82) vive sola en un 2do piso y no responde. La escalera de su casa se cayó. | HIGH | PENDING | 34,34 |
| INJURED | Jr. Los Pinos 340 | Corte en el brazo por vidrios. Está consciente. | HIGH | ON_THE_WAY (Brigada Z3) | 66,62 |
| DISABILITY | Mz. F lote 3 | Usa silla de ruedas y vive en un 2do piso. | MEDIUM | ON_THE_WAY (Luis M.) | 55,28 |
| CHILDREN_ALONE | Pje. Las Rosas 18 | Dos niños solos; la mamá trabaja en el centro. | HIGH | ATTENDED (Rosa F.) | 40,72 |

**Recursos de emergencia (summary have/need):** WATER 14/9, FOOD 8/12, MEDICINE 5/7, ENERGY 6/15, SHELTER 3/4, TRANSPORT 4/2. Listados de ejemplo: Familia Ríos HAVE agua «20 litros de agua potable»; Carla Soto NEED agua «Agua para 4 personas (1 bebé)»; Taller Mendoza HAVE energía «Generador: carga tu celular de 4 a 6 pm»; Junta vecinal HAVE refugio «Local comunal abierto · 60 cupos».

**Grupos de apoyo:** Brigada de primeros auxilios Z3 (12 miembros, activa); Electricistas de guardia (4, activa); Evaluación de viviendas (5, activa); Olla común Las Flores (20, activa, «Comida caliente 12:00 y 18:00»); Apoyo psicológico (6, desde las 14:00).

**Puntos seguros:** «Punto de reunión · Parque Las Flores» MEETING_POINT (24,52); «Refugio · Local comunal» SHELTER capacidad 60, ocupados 22 (80,76).

**Reportes de riesgo (prevención, en las últimas 3 h):**
| Autor | Categoría | Dirección | Texto | Foto | x,y | Hace |
|---|---|---|---|---|---|---|
| Carla Soto | SEEPAGE | Jr. Los Pinos 318 | Está saliendo agua del suelo de mi patio | sí | 62,60 | 40 min |
| Jorge Paredes | ACEQUIA_OVERFLOW | Jr. Los Pinos 352 | La acequia está al ras, el agua ya pasa la vereda | sí | 67,64 | 1 h |
| Lucía Mamani | RAVINE_ACTIVITY | Quebrada Canto Grande (parte alta) | Baja lodo con piedras pequeñas por la quebrada | sí | 16,16 | 25 min |
| Óscar Díaz | CRACK | Quebrada Canto Grande (parte alta) | Se abrió una grieta en el muro de contención | sí | 22,20 | 1 h |
| Teresa Rojas | STREET_FLOODING | Quebrada Canto Grande (parte baja) | El agua ya entra a las casas de la parte baja | no | 19,25 | 50 min |
| Elena Vargas | STREET_FLOODING | Av. Principal 410 | Se forma una laguna en la esquina del paradero | sí | 82,36 | 2 h |
| Jorge Paredes | DRAIN_OVERFLOW | Pje. Las Rosas 12 | El buzón está botando agua | no | 40,76 | 3 h |

**Mitigación (summary have/need):** SANDBAGS 40/120, PUMP 2/5, ROOF_COVER 15/30, SHOVELS 9/6, PLANKS 6/10, FLASHLIGHTS 12/20. Listados: Familia Ríos HAVE sacos «20 sacos de arena y una motobomba guardados» (Cochera Mz. B); Carla Soto NEED sacos «Sacos para la puerta: ya entra agua por la vereda»; Pedro Ramos HAVE calaminas; Lucía Mamani NEED plástico «Plástico para cubrir mi techo de estera»; Óscar Díaz HAVE palas.

## ANEXO B: Diccionarios de la IA (reglas)

**Palabras clave → CapabilityTag** (normalizar a minúsculas y sin tildes):
| Tag | Kind | Palabras clave |
|---|---|---|
| HEIGHT_RESCUE | RESOURCE | escalera |
| RESCUE_TOOLS | RESOURCE | palanca, gata, taladro, barreta |
| BACKUP_POWER | RESOURCE | generador, panel solar |
| WATER_RESERVE | RESOURCE | tanque de agua, cisterna |
| EVAC_TRANSPORT | RESOURCE | mototaxi, camioneta, asientos libres |
| PARAMEDIC | SKILL | inyect, enfermer, primeros auxilios |
| TECHNICIAN | SKILL | electricista, gasfiter (+ categoría ELECTRICITY o PLUMBING) |
| ELDERLY_AT_HOME | VULNERABILITY | abuela, abuelo, adulto mayor |
| REDUCED_MOBILITY | VULNERABILITY | silla de ruedas, no puede caminar |
| OXYGEN_DEPENDENT | VULNERABILITY | oxigeno |
| SANDBAGS | RESOURCE | sacos de arena |
| PUMP | RESOURCE | motobomba |
| ROOF_COVER | RESOURCE | calamina, plastico grueso |
| SHOVELS | RESOURCE | palas |

**MISSION_NEEDS** (categoría del reporte → necesidades, en orden):
- ELDERLY_ALONE → HEIGHT_RESCUE, RESCUE_TOOLS
- TRAPPED → HEIGHT_RESCUE, RESCUE_TOOLS
- INJURED → PARAMEDIC, EVAC_TRANSPORT
- DISABILITY → EVAC_TRANSPORT, HEIGHT_RESCUE
- CHILDREN_ALONE → PARAMEDIC
- PREGNANT → PARAMEDIC, EVAC_TRANSPORT
- OXYGEN_DEPENDENT → BACKUP_POWER, PARAMEDIC

**classify (micrófono inteligente)**: en todos los modos, ubicación con las regex `(los pinos|las flores|las rosas|principal)\s*(\d{1,4})` → «Jr. Los Pinos 340» y `manzana\s*([a-z])\s*lote\s*(\d+)` → «Mz. C lote 12».
- **EMERGENCY**:
  - Si habla de agua, comida, víveres, medicina, carga, refugio o transporte (y no de herido, atrapado o sangre) → recurso NEED u OFFER («tengo», «ofrezco», «regalo», «comparto», «presto» = OFFER).
  - Si no → VULNERABLE con categoría:
    - herid, sangr, fractur, golpe → INJURED
    - atrapad, «se cayó», escombro, derrumb → TRAPPED
    - oxigeno → OXYGEN_DEPENDENT
    - abuel, adulto mayor, anciano → ELDERLY_ALONE
    - niño, niña, bebé → CHILDREN_ALONE
    - embarazada, gestante → PREGNANT
    - silla de ruedas, discapacidad → DISABILITY
  - Prioridad HIGH si dice: herid, sangr, atrapad, no respira, inconsciente, fuego, incendio, «se cayó», derrumb.
- **NORMAL**:
  - Tipo: OFFER si dice presto, ofrezco, regalo, tengo, enseño, puedo o dono; si no, NEED.
  - Categoría:
    - escalera, taladro, herramienta → TOOL_LOAN
    - acompañ, cuidar → TEMPORARY_HELP
    - ladrillo, cemento, madera, material → MATERIAL_DONATION
    - enseñ, clase, tarea → SKILL_EXCHANGE
    - llevar, lleven, auto, asiento, mercado, cita → SOLIDARITY_RIDE
- **PREVENTION**:
  - Si habla de sacos, motobomba, calamina, plástico, palas o tablones → mitigación NEED u OFFER.
  - Si no → RISK_REPORT con categoría:
    - quebrada, huaico, lodo, piedras → RAVINE_ACTIVITY
    - grieta, rajadura → CRACK
    - acequia, canal → ACEQUIA_OVERFLOW
    - filtra, humedad, gotea, brota → SEEPAGE
    - desagüe, buzón → DRAIN_OVERFLOW
    - agua, charco, inund, aniego, laguna → STREET_FLOODING
  - Prioridad HIGH si dice: desborda, huaico, grieta, «entra agua», «al ras», «sube rápido».

**Prevención:**
- **Pesos por categoría:** RAVINE_ACTIVITY 2, CRACK 2, ACEQUIA_OVERFLOW 1.5, STREET_FLOODING 1, SEEPAGE 1, DRAIN_OVERFLOW 1.
- **MITIGATION_NEEDS:**
  - ACEQUIA_OVERFLOW → SANDBAGS, PUMP, SHOVELS
  - STREET_FLOODING → SANDBAGS, PUMP
  - SEEPAGE → SANDBAGS, ROOF_COVER
  - RAVINE_ACTIVITY → SANDBAGS, EVAC_TRANSPORT
  - DRAIN_OVERFLOW → SHOVELS, PUMP
  - CRACK → SANDBAGS, EVAC_TRANSPORT
- **Inferencia** (en este orden):
  1. Si hay RAVINE_ACTIVITY → «posible activación de la quebrada (huaico): prepara la evacuación».
  2. Si hay ACEQUIA_OVERFLOW junto con SEEPAGE o STREET_FLOODING → «la acequia podría desbordarse y anegar la calle».
  3. Si solo hay ACEQUIA_OVERFLOW → «la acequia está cerca de desbordarse».
  4. Si hay SEEPAGE → «hay filtración subterránea: revisa cimientos y muros».
  5. En cualquier otro caso → «riesgo de aniego en la pista y las viviendas bajas».
- **Plantilla del patrón:** `"{n} vecinos de {calle} reportaron {tipos unidos con comas e 'y'} en la última hora: {inferencia}."` Los nombres cortos de los tipos son: acequia al ras, agua acumulada, filtración, quebrada activa, desagüe colapsado y grieta.
- **Nombre del riesgo:** huaico (si hay quebrada), desborde de acequia (si hay acequia) o aniegos.
- **PHOTO_AI (texto por categoría para el analizador de reglas):**
  - ACEQUIA_OVERFLOW: «Agua corriendo sobre la vereda, cauce al 95 %. Riesgo alto de desborde.» (0.85)
  - STREET_FLOODING: «Agua acumulada de unos 25 cm en la pista. Riesgo medio de aniego.» (0.55)
  - SEEPAGE: «Mancha de humedad y agua brotando del piso. Posible filtración subterránea.» (0.5)
  - RAVINE_ACTIVITY: «Lodo y piedras en el cauce. Posible activación de la quebrada. Riesgo alto.» (0.9)
  - DRAIN_OVERFLOW: «Buzón rebalsando con agua turbia. Riesgo medio de aniego.» (0.5)
  - CRACK: «Grieta diagonal de unos 2 cm en muro de contención. Riesgo alto.» (0.85)

## ANEXO C: Formato del SOS comprimido (SMS o sonido)

```
NB#SOS!{T}{n}@{LOC}$P:{ALTA|MEDIA}        largo máximo 25 caracteres
```
- `NB#SOS!`: prefijo fijo.
- `{T}`: tipo, una letra:
  - H herido · A atrapado · M adulto mayor · D discapacidad
  - N niños solos · G gestante · O dependiente de oxígeno
  - S salud · F incendio · R robo · Q sismo · V violencia · X otro
  - **Mapeo:** INJURED→H, TRAPPED→A, ELDERLY_ALONE→M, DISABILITY→D, CHILDREN_ALONE→N, PREGNANT→G, OXYGEN_DEPENDENT→O. Para el SOS: HEALTH→S, FIRE→F, ROBBERY→R, EARTHQUAKE→Q, VIOLENCE→V, OTHER→X.
- `{n}`: número de personas, de 1 a 9.
- `{LOC}`:
  - **Calle:** código de calle de la tabla `street_code` más el número. Ejemplo: `LP340` = Jr. Los Pinos 340.
  - **Manzana y lote:** `M{letra}{lote}`. Ejemplo: `MC12` = Mz. C lote 12.
  - **Sin dirección:** usar la `street_code` más cercana a la posición y el número `0`.
- `$P:`: prioridad, `ALTA` o `MEDIA`.
- **Regex de decodificación:** `^NB#SOS!([A-Z])(\d)@([A-Z]+?)(\d+)\$P:(ALTA|MEDIA)$`. Un prefijo de 2 letras que empieza con `M` y no está en street_code se interpreta como manzana.
- **Ejemplos:**
  - `NB#SOS!H1@LP340$P:ALTA` = 1 herido en Jr. Los Pinos 340, prioridad alta.
  - `NB#SOS!A2@MC12$P:ALTA` = 2 atrapados en Mz. C lote 12.
  - `NB#SOS!O1@MF1$P:MEDIA` = dependiente de oxígeno en Mz. F lote 1.
- **Siguiente versión:** firma corta HMAC para evitar códigos falsos, dentro del límite de caracteres.
