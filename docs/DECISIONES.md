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
