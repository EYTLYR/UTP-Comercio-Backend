-- Estadísticas de zona (favores resueltos, etc.).
CREATE TABLE IF NOT EXISTS zone_stats (
    zone_id uuid PRIMARY KEY REFERENCES zone (id) ON DELETE CASCADE,
    favors_resolved integer NOT NULL DEFAULT 0,
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    updated_at timestamp with time zone NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS ix_zone_stats_favors_resolved ON zone_stats (favors_resolved);
