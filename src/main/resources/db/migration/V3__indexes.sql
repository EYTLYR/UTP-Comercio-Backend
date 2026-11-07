-- FASE 11: índices para las consultas más usadas (listas por zona y estado).
CREATE INDEX ix_post_zone_status ON post (zone_id, status);
CREATE INDEX ix_sos_alert_zone_status_created ON sos_alert (zone_id, status, created_at);
CREATE INDEX ix_vulnerable_report_zone_status ON vulnerable_report (zone_id, status);
CREATE INDEX ix_resource_listing_zone_active ON resource_listing (zone_id, active);
CREATE INDEX ix_risk_zone_zone_active ON risk_zone (zone_id, active);
CREATE INDEX ix_capability_zone_removed ON capability (zone_id, removed_by_user);
CREATE INDEX ix_check_in_zone_status_created ON check_in (zone_id, status, created_at);
