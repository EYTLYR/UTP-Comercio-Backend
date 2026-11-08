-- Municipalidades registradas desde la app. Un funcionario la registra (PENDING) y el administrador
-- de la plataforma la aprueba. Al aprobarla, quien la registró pasa al rol MUNICIPALITY y administra
-- las zonas de su distrito: modo de la zona, brigadistas, roles y su propio equipo.
CREATE TABLE municipality (
    id uuid PRIMARY KEY,
    name varchar(160) NOT NULL,
    district varchar(120) NOT NULL,
    -- Distrito en minúsculas y sin tildes, para compararlo con zone.district.
    district_key varchar(120) NOT NULL,
    ruc varchar(11) NOT NULL,
    contact_name varchar(120) NOT NULL,
    contact_position varchar(120),
    email varchar(160) NOT NULL,
    status varchar(20) NOT NULL,
    requested_by uuid NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    decided_by uuid REFERENCES app_user (id) ON DELETE SET NULL,
    decided_at timestamp with time zone,
    reject_reason varchar(300),
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    updated_at timestamp with time zone NOT NULL DEFAULT now(),
    CONSTRAINT ck_municipality_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    CONSTRAINT ck_municipality_ruc CHECK (ruc ~ '^(20)[0-9]{9}$')
);

-- Un RUC y un distrito no pueden tener dos registros vigentes (pendientes o aprobados).
CREATE UNIQUE INDEX uq_municipality_ruc_active ON municipality (ruc) WHERE status <> 'REJECTED';
CREATE UNIQUE INDEX uq_municipality_district_active ON municipality (district_key) WHERE status <> 'REJECTED';
CREATE INDEX idx_municipality_status ON municipality (status, created_at);
CREATE INDEX idx_municipality_requested_by ON municipality (requested_by, created_at);

-- Funcionarios de la Municipalidad (rol MUNICIPALITY).
ALTER TABLE app_user ADD COLUMN municipality_id uuid REFERENCES municipality (id) ON DELETE SET NULL;
CREATE INDEX idx_app_user_municipality ON app_user (municipality_id);
