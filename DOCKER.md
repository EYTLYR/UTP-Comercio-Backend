# Construcción y despliegue del backend

PostgreSQL/PostGIS y el almacenamiento son servicios externos. Compose solo levanta la API. El servidor necesita Docker Engine con Docker Compose, la imagen exportada y el archivo `docker-compose.yml`.

## 1. Construir en tu equipo

Completa el `.env` del backend antes de construir. El Dockerfile lo incorpora en `/app/.env` y Spring lo importa automáticamente. Configura el perfil y las credenciales apropiados para el servidor. `DB_URL` y, si corresponde, `DB_MIGRATION_URL` deben apuntar a la base externa accesible desde el servidor; `localhost` dentro del contenedor apunta al propio contenedor. Si no necesitas una URL distinta para migraciones, omite `DB_MIGRATION_URL` en lugar de dejarla vacía.

Desde la raíz del backend, con Docker iniciado:

```powershell
docker compose -f compose.build.yaml build
docker image save -o nearby-backend.tar nearby-backend:server
```

La imagen debe corresponder a la arquitectura del servidor. Si el servidor es Linux AMD64 y construyes desde otra arquitectura, usa en lugar del primer comando:

```powershell
docker buildx build --platform linux/amd64 --load -t nearby-backend:server .
```

## 2. Copiar al servidor

Transfiere únicamente estos dos archivos a una carpeta del servidor:

- `nearby-backend.tar`
- `docker-compose.yml`

No necesitas copiar el repositorio, Java, Maven, Dockerfile ni `.env` al servidor.

## 3. Cargar y ejecutar

Dentro de esa carpeta del servidor:

```sh
docker load -i nearby-backend.tar
docker compose up -d
```

La carga de la imagen se realiza una vez por versión. Después basta con `docker compose up -d`. Compose usa exclusivamente la imagen local `nearby-backend:server`: no compila ni intenta descargar imágenes de un registro.

La API queda publicada en el puerto 8080. El contenedor se reinicia automáticamente si falla o se reinicia Docker. El healthcheck consulta `/actuator/health`.

```sh
docker compose ps
docker compose logs -f app
```

Para detener este despliegue: `docker compose down`.

## Actualizar una versión

Reconstruye y exporta en tu equipo. Transfiere el nuevo archivo y ejecuta en el servidor:

```sh
docker load -i nearby-backend.tar
docker compose up -d --force-recreate
```

Si cambias credenciales o URLs del `.env`, reconstruye la imagen para incorporar los nuevos valores. Las variables de entorno inyectadas al contenedor tienen prioridad sobre el archivo incorporado; el puerto interno se fija en 8080.

## Ejecutar sin Compose

Después de cargar la imagen también puedes ejecutar:

```sh
docker run -d --name nearby-backend --restart unless-stopped --init -p 8080:8080 nearby-backend:server
```

Usa esta opción o Compose para evitar que ambos intenten ocupar el mismo puerto.

## Notas de distribución

La imagen contiene las credenciales del `.env`: distribuye el archivo TAR por un canal privado y no publiques la imagen en un registro público. El servidor debe poder conectarse a PostgreSQL/PostGIS, S3 y los demás servicios configurados. El contenedor utiliza Java 21 y un usuario sin privilegios. La construcción omite las pruebas, que deben ejecutarse por separado antes de publicar.

`compose.yaml` y `docker-compose.yml` contienen el mismo despliegue para admitir ambos nombres habituales. `compose.prod.yaml` es un alias del primero. Para construir utiliza explícitamente `compose.build.yaml`; los archivos de despliegue nunca construyen imágenes.
