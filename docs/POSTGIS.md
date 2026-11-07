# PostGIS para el módulo SOS

**Antes:** para avisar a los vecinos a 500 m de un SOS, el backend:

1. traía a **todos** los usuarios de la zona;
2. calculaba la distancia de cada uno en Java (Haversine);
3. descartaba los que estaban lejos.

Lo mismo hacía `GET /sos/nearby`, que cada app consulta cada 20 s. El costo crece con el tamaño de la zona y la base no podía usar ningún índice.

**Ahora:** la base guarda un punto PostGIS por usuario y por SOS. `ST_DWithin` filtra por radio usando un índice espacial GIST, y Java solo recibe a los que están cerca.

## 1. Script SQL (Flyway `V4__postgis.sql`)

[src/main/resources/db/migration/V4__postgis.sql](../src/main/resources/db/migration/V4__postgis.sql)

```sql
-- Habilitar PostGIS (Neon lo trae; versión 3.6.4 sobre PostgreSQL 18.6)
CREATE EXTENSION IF NOT EXISTS postgis;

-- Punto del vecino en WGS 84. Se genera solo a partir de lat/lng.
-- ST_MakePoint recibe (longitud, latitud).
ALTER TABLE app_user
    ADD COLUMN location geometry(Point, 4326)
    GENERATED ALWAYS AS (
        CASE WHEN lat IS NULL OR lng IS NULL THEN NULL
             ELSE ST_SetSRID(ST_MakePoint(lng, lat), 4326)
        END
    ) STORED;

-- Índice espacial GIST sobre la geometría
CREATE INDEX ix_app_user_location ON app_user USING GIST (location);
-- Índice GIST sobre geography: el que usa ST_DWithin en metros
CREATE INDEX ix_app_user_location_geog ON app_user USING GIST ((location::geography));
DROP INDEX IF EXISTS ix_app_user_lat_lng;

-- Lo mismo para los SOS ("SOS activos cerca de mí")
ALTER TABLE sos_alert
    ADD COLUMN location geometry(Point, 4326)
    GENERATED ALWAYS AS (ST_SetSRID(ST_MakePoint(lng, lat), 4326)) STORED;
CREATE INDEX ix_sos_alert_location_geog ON sos_alert USING GIST ((location::geography));
DROP INDEX IF EXISTS ix_sos_alert_lat_lng;
```

**Por qué una columna generada y no reemplazar `lat`/`lng`:**

- La API, el código SMS, el frontend y los demás módulos siguen usando `lat`/`lng` sin cambios.
- La base recalcula `location` en cada `INSERT`/`UPDATE`, así que nunca queda desincronizada.
- Las versiones anteriores del backend siguen funcionando contra la base migrada, porque solo se agregan columnas.

**Por qué `::geography`:** en `geometry(Point, 4326)` las distancias de `ST_DWithin` están en **grados**, no en metros. Al convertir a `geography`, PostGIS mide en metros sobre el elipsoide WGS 84. Para que esa consulta use un índice, hace falta el índice GIST sobre la expresión `(location::geography)`.

## 2. Entidad con Hibernate Spatial

Dependencia en `pom.xml`. La versión la fija Spring Boot (7.4.13, igual que Hibernate). Con PostgreSQL no hace falta `postgis-jdbc`.

```xml
<dependency>
    <groupId>org.hibernate.orm</groupId>
    <artifactId>hibernate-spatial</artifactId>
</dependency>
```

[AppUser.java](../src/main/java/eytlyr/com/utp_comercio_backend/user/AppUser.java):

```java
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;
import org.locationtech.jts.geom.Point;

@Column(name = "lat")
private Double lat;

@Column(name = "lng")
private Double lng;

/** Punto PostGIS (SRID 4326) generado por la base a partir de lat/lng. Solo lectura. */
@Generated(event = { EventType.INSERT, EventType.UPDATE })
@Column(name = "location", insertable = false, updatable = false, columnDefinition = "geometry(Point,4326)")
private Point location;
```

- `Point` es de JTS (`org.locationtech.jts`), que llega con `hibernate-spatial`.
- `insertable = false, updatable = false`: la base calcula la columna y Java nunca la escribe.
- `@Generated(INSERT, UPDATE)`: Hibernate vuelve a leer `location` después de guardar, así que `user.getLocation()` siempre está al día. `getX()` es la longitud y `getY()` la latitud.
- Con `ddl-auto: validate`, Hibernate comprueba que la columna sea `geometry`.

Si en otra tabla prefieres que Java escriba el punto, quita `insertable/updatable = false` y `@Generated`, y créalo así:

```java
GeometryFactory gf = new GeometryFactory(new PrecisionModel(), 4326);
Point p = gf.createPoint(new Coordinate(lng, lat)); // (x = longitud, y = latitud)
```

## 3. Repositorio: usuarios a 500 m con `ST_DWithin`

[AppUserRepository.java](../src/main/java/eytlyr/com/utp_comercio_backend/user/AppUserRepository.java):

```java
@Query(value = """
        SELECT u.* FROM app_user u
        WHERE u.location IS NOT NULL
          AND ST_DWithin(u.location::geography,
                         ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography,
                         :radiusM)
        ORDER BY u.location::geography <-> ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography
        """, nativeQuery = true)
List<AppUser> findWithinRadius(@Param("lat") double lat, @Param("lng") double lng,
        @Param("radiusM") double radiusM);
```

Uso en [SosService.java](../src/main/java/eytlyr/com/utp_comercio_backend/sos/SosService.java):

```java
List<AppUser> neighbors = users.findWithinRadius(req.lat(), req.lng(), ALERT_RADIUS_M).stream() // 500 m
        .filter(u -> !u.getId().equals(userId) && u.getRole() != Role.BRIGADIST)
        .toList();
```

El mismo patrón se usa en otras dos consultas:

- `findInRing(lat, lng, 500, 1000)`: si un vecino responde "no puedo", se avisa al anillo de 500 m a 1 km.
- `SosAlertRepository.findActiveWithinRadius(...)`: alimenta `GET /sos/nearby`.

**Por qué una query nativa y no JPQL:** la comparación en metros necesita la conversión `::geography`, y HQL no la expresa. Con HQL y Hibernate Spatial solo se puede trabajar en **grados** sobre `geometry`:

```java
// Alternativa HQL (radio en grados, no en metros: 500 m ≈ 0.0045° de latitud en Lima).
// Verificada con Hibernate 7.4: el cast a Boolean es necesario porque Hibernate no conoce
// el tipo de retorno de st_dwithin. También funciona function('ST_DWithin', ...) = true.
@Query("select u from AppUser u where cast(st_dwithin(u.location, :point, :degrees) as Boolean) = true")
List<AppUser> findWithinDegrees(@Param("point") Point point, @Param("degrees") double degrees);

// :point se arma con JTS, en SRID 4326 y con (x = longitud, y = latitud):
Point point = new GeometryFactory(new PrecisionModel(), 4326).createPoint(new Coordinate(lng, lat));
```

Sin el cast, `dwithin(...) = true` y `st_dwithin(...) = true` fallan con *Cannot compare left expression of type 'java.lang.Object'*. Esta alternativa sirve para radios aproximados. Para el SOS, la query nativa con `geography` es exacta y usa el índice.

## Cómo comprobar que usa el índice

```sql
EXPLAIN ANALYZE
SELECT u.* FROM app_user u
WHERE u.location IS NOT NULL
  AND ST_DWithin(u.location::geography, ST_SetSRID(ST_MakePoint(-77.003, -11.979), 4326)::geography, 500);
-- Con suficientes filas debe aparecer: Index Scan using ix_app_user_location_geog
```

Con pocas filas, PostgreSQL puede preferir un Seq Scan porque es más barato. Es normal.

## Pruebas

[GeoRadiusQueryTest](../src/test/java/eytlyr/com/utp_comercio_backend/user/GeoRadiusQueryTest.java) crea vecinos a 0, ~299, ~487, ~520, ~664 m y ~2.2 km de un punto. Comprueba que:

- `findWithinRadius(500)` devuelve los tres primeros, ordenados por distancia;
- el anillo de 500 m a 1 km devuelve los de ~520 y ~664 m;
- un usuario sin ubicación nunca aparece.

Las pruebas necesitan PostGIS:

- **Con Docker:** usan `postgis/postgis:18-3.6-alpine`.
- **Sin Docker:** usan la instalación local de PostgreSQL con PostGIS (ver el README).
