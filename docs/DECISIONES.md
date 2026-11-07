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
