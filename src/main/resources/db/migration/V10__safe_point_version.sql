-- Columna de versión para Optimistic Locking en safe_point (albergues/refugios).
ALTER TABLE safe_point ADD COLUMN IF NOT EXISTS version bigint NOT NULL DEFAULT 0;

