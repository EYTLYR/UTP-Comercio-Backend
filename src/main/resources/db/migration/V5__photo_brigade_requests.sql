-- Foto de perfil del vecino (URL relativa servida por /files, igual que las fotos de riesgo).
ALTER TABLE app_user ADD COLUMN photo_url varchar(300);

-- Solicitudes para ser brigadista: el vecino la pide y la Municipalidad (ADMIN o DELEGATE) la aprueba.
CREATE TABLE brigade_request (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    skills varchar(300),
    status varchar(20) NOT NULL,
    decided_by uuid REFERENCES app_user (id) ON DELETE SET NULL,
    decided_at timestamp with time zone,
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    updated_at timestamp with time zone NOT NULL DEFAULT now(),
    CONSTRAINT ck_brigade_request_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED'))
);

-- Una sola solicitud pendiente por vecino.
CREATE UNIQUE INDEX uq_brigade_request_pending ON brigade_request (user_id) WHERE status = 'PENDING';
CREATE INDEX idx_brigade_request_status ON brigade_request (status, created_at);
