# Backend en Docker

PostgreSQL/PostGIS es un servicio externo. Este despliegue solo crea el contenedor de la API; no crea ni elimina bases de datos.

## Preparación

Mantén el archivo `.env` en la raíz del backend. Si aún no existe, copia `.env.example` y completa sus valores. Configura `SPRING_PROFILES_ACTIVE=prod`, `DB_URL`, `DB_USER`, `DB_PASSWORD`, el secreto JWT y las credenciales de los servicios que utilices. Para Neon puedes especificar `DB_MIGRATION_URL` con la conexión directa y usar el pooler en `DB_URL`; si no necesitas una URL distinta, omite esa variable en lugar de dejarla vacía.

La base debe tener PostGIS disponible y permitir conexiones desde el equipo o servidor Docker. Dentro del contenedor, `localhost` apunta al propio contenedor. Si PostgreSQL corre en Windows con Docker Desktop, utiliza `host.docker.internal` en la URL JDBC; si es remoto, utiliza su hostname real.

## Ejecutar con Compose

```powershell
docker compose up -d --build
docker compose logs -f app
```

`compose.yaml` y `docker-compose.yml` describen el mismo servicio. También puedes usar explícitamente `docker compose -f docker-compose.yml up -d --build`. El puerto del equipo se toma de `PORT` en `.env`, o de 8080. El puerto interno de Compose siempre es 8080.

Para detener únicamente este despliegue:

```powershell
docker compose down
```

## Imagen autónoma

```powershell
docker build -t nearby-backend:local .
docker run -d --name nearby-backend -p 8080:8080 nearby-backend:local
```

El Dockerfile copia `.env` a `/app/.env`; Spring ya lo importa automáticamente. No hace falta montar archivos adicionales. La imagen fija el puerto interno en 8080; puedes sobrescribirlo con `-e PORT=otro_puerto` y publicar ese mismo puerto. Las variables inyectadas con `--env-file` o `-e` tienen prioridad sobre el archivo incorporado. Compose carga además el `.env` actual al arrancar, por lo que permite cambiar valores sin reconstruir; con `docker run` sin argumentos adicionales, reconstruye para incorporar cambios.

La imagen contiene las credenciales del `.env`: almacénala y distribúyela en registros privados. Evita compartirla públicamente. Ser autónoma en configuración no elimina la necesidad de acceso de red a PostgreSQL, S3 y los demás servicios externos; las URLs deben ser accesibles desde cada ambiente.

La imagen usa Java 21, usuario sin privilegios y un healthcheck HTTP en `/actuator/health`. La construcción omite las pruebas; ejecútalas por separado antes de publicar la imagen.

