-- Estado de la respuesta al favor (PENDING, IN_PROGRESS, REJECTED, COMPLETED)
ALTER TABLE post_response ADD COLUMN IF NOT EXISTS status varchar(32) NOT NULL DEFAULT 'PENDING';
CREATE INDEX IF NOT EXISTS ix_post_response_status ON post_response (status);

