-- PostGIS para las búsquedas por distancia del módulo SOS (vecinos a 500 m).
-- Neon trae PostGIS; solo hay que habilitarlo en la base.
CREATE EXTENSION IF NOT EXISTS postgis;

-- Punto del vecino en WGS 84 (SRID 4326). Es una columna GENERADA a partir de lat/lng:
-- la base la mantiene sola, así que todo el código que ya guarda lat/lng sigue igual
-- y no puede quedar desincronizada. Ojo: ST_MakePoint recibe (longitud, latitud).
ALTER TABLE app_user
    ADD COLUMN location geometry(Point, 4326)
    GENERATED ALWAYS AS (
        CASE WHEN lat IS NULL OR lng IS NULL THEN NULL
             ELSE ST_SetSRID(ST_MakePoint(lng, lat), 4326)
        END
    ) STORED;

-- Índice espacial GIST sobre la geometría (consultas en grados, ST_Intersects, mapas).
CREATE INDEX ix_app_user_location ON app_user USING GIST (location);

-- Índice GIST sobre la misma columna como geography: es el que usa
-- ST_DWithin(location::geography, punto::geography, metros) para medir en metros reales.
CREATE INDEX ix_app_user_location_geog ON app_user USING GIST ((location::geography));

-- El índice B-tree por (lat, lng) ya no hace falta para buscar vecinos.
DROP INDEX IF EXISTS ix_app_user_lat_lng;

-- Lo mismo para los SOS: "SOS activos cerca de mí" (la app lo consulta cada 20 s).
ALTER TABLE sos_alert
    ADD COLUMN location geometry(Point, 4326)
    GENERATED ALWAYS AS (ST_SetSRID(ST_MakePoint(lng, lat), 4326)) STORED;
CREATE INDEX ix_sos_alert_location_geog ON sos_alert USING GIST ((location::geography));
DROP INDEX IF EXISTS ix_sos_alert_lat_lng;
