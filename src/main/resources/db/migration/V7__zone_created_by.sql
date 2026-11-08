-- Quién registró la zona (vecino, Municipalidad o plataforma). Null en las zonas creadas al guardar una casa.
ALTER TABLE zone ADD COLUMN created_by uuid REFERENCES app_user (id) ON DELETE SET NULL;
CREATE INDEX idx_zone_created_by ON zone (created_by);
