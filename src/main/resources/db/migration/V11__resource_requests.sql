-- Flujo de "Ping de Disponibilidad" para recursos críticos y de mitigación (motobombas, etc.)
CREATE TABLE resource_request (
    id uuid PRIMARY KEY,
    requester_id uuid NOT NULL REFERENCES app_user (id),
    resource_type varchar(40),
    mitigation_type varchar(40),
    status varchar(30) NOT NULL DEFAULT 'PENDING',
    lat double precision NOT NULL,
    lng double precision NOT NULL,
    note varchar(500),
    accepted_by_id uuid REFERENCES app_user (id),
    accepted_listing_id uuid REFERENCES resource_listing (id),
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    updated_at timestamp with time zone NOT NULL DEFAULT now(),
    CONSTRAINT ck_resource_request_type CHECK ((resource_type IS NOT NULL) OR (mitigation_type IS NOT NULL))
);

CREATE INDEX ix_resource_request_requester ON resource_request (requester_id);
CREATE INDEX ix_resource_request_status ON resource_request (status);

-- Proveedores candidatos (hasta 3 vecinos más cercanos a quienes se les hace el ping simultáneo)
CREATE TABLE resource_request_candidate (
    id uuid PRIMARY KEY,
    request_id uuid NOT NULL REFERENCES resource_request (id) ON DELETE CASCADE,
    provider_id uuid NOT NULL REFERENCES app_user (id),
    listing_id uuid NOT NULL REFERENCES resource_listing (id),
    status varchar(30) NOT NULL DEFAULT 'PENDING',
    notified_at timestamp with time zone NOT NULL DEFAULT now(),
    responded_at timestamp with time zone,
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    updated_at timestamp with time zone NOT NULL DEFAULT now(),
    CONSTRAINT uq_resource_request_candidate UNIQUE (request_id, provider_id)
);

CREATE INDEX ix_resource_request_candidate_provider ON resource_request_candidate (provider_id, status);
CREATE INDEX ix_resource_request_candidate_request ON resource_request_candidate (request_id);

