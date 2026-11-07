-- Nearby · esquema inicial (FASE 2). Generado una vez a partir del modelo; desde ahora se edita a mano
-- con nuevas migraciones V2__, V3__... (nunca cambies una migración ya aplicada).
-- Todas las fechas son timestamptz (se guardan en UTC). Los enums se guardan como texto.

-- Zona (barrio) con su modo actual: NORMAL, PREVENTION o EMERGENCY.
CREATE TABLE zone (
    id uuid PRIMARY KEY,
    name varchar(120) NOT NULL UNIQUE,
    district varchar(120) NOT NULL,
    center_lat double precision NOT NULL,
    center_lng double precision NOT NULL,
    radius_m integer NOT NULL,
    mode varchar(40) NOT NULL DEFAULT 'NORMAL',
    mode_source varchar(40),
    mode_reason varchar(300),
    mode_activated_at timestamp with time zone,
    weather_level varchar(40),
    weather_valid_until timestamp with time zone,
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    updated_at timestamp with time zone NOT NULL DEFAULT now()
);

-- Código corto de calle para el SOS comprimido (LP = Jr. Los Pinos).
CREATE TABLE street_code (
    id uuid PRIMARY KEY,
    zone_id uuid NOT NULL REFERENCES zone (id),
    code varchar(4) NOT NULL,
    name varchar(120) NOT NULL,
    lat double precision NOT NULL,
    lng double precision NOT NULL,
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    updated_at timestamp with time zone NOT NULL DEFAULT now(),
    CONSTRAINT uq_street_code_zone_code UNIQUE (zone_id, code)
);
CREATE INDEX ix_street_code_zone_created_at ON street_code (zone_id, created_at);
CREATE INDEX ix_street_code_lat_lng ON street_code (lat, lng);

-- Vecino. El celular es el login.
CREATE TABLE app_user (
    id uuid PRIMARY KEY,
    phone varchar(9) NOT NULL UNIQUE,
    name varchar(120) NOT NULL,
    role varchar(40) NOT NULL DEFAULT 'NEIGHBOR',
    verified boolean NOT NULL DEFAULT false,
    zone_id uuid REFERENCES zone (id),
    lat double precision,
    lng double precision,
    address_text varchar(200),
    ai_consent boolean NOT NULL DEFAULT false,
    mode_dismissed_at timestamp with time zone,
    local_emergency boolean NOT NULL DEFAULT false,
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    updated_at timestamp with time zone NOT NULL DEFAULT now()
);
CREATE INDEX ix_app_user_zone_created_at ON app_user (zone_id, created_at);
CREATE INDEX ix_app_user_lat_lng ON app_user (lat, lng);

-- Código SMS de acceso (solo se guarda el hash).
CREATE TABLE otp_code (
    id uuid PRIMARY KEY,
    phone varchar(9) NOT NULL,
    code_hash varchar(100) NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    attempts integer NOT NULL DEFAULT 0,
    consumed boolean NOT NULL DEFAULT false,
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    updated_at timestamp with time zone NOT NULL DEFAULT now()
);
CREATE INDEX ix_otp_code_phone_created_at ON otp_code (phone, created_at);

-- Token de refresco (solo se guarda el hash).
CREATE TABLE refresh_token (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    token_hash varchar(100) NOT NULL UNIQUE,
    expires_at timestamp with time zone NOT NULL,
    revoked boolean NOT NULL DEFAULT false,
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    updated_at timestamp with time zone NOT NULL DEFAULT now()
);
CREATE INDEX ix_refresh_token_user ON refresh_token (user_id);

-- Contacto de confianza (hasta 3) que recibe la ubicación en un SOS.
CREATE TABLE trusted_contact (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    name varchar(120) NOT NULL,
    phone varchar(15) NOT NULL,
    position integer NOT NULL,
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    updated_at timestamp with time zone NOT NULL DEFAULT now(),
    CONSTRAINT uq_trusted_contact_user_position UNIQUE (user_id, position),
    CONSTRAINT ck_trusted_contact_1 CHECK (position BETWEEN 1 AND 3)
);

-- Ficha médica. Solo la ven BRIGADIST y ADMIN.
CREATE TABLE medical_card (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL UNIQUE REFERENCES app_user (id) ON DELETE CASCADE,
    blood_type varchar(5),
    allergies varchar(300),
    conditions varchar(300),
    lives_with varchar(300),
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    updated_at timestamp with time zone NOT NULL DEFAULT now()
);

-- Token de notificaciones push del teléfono.
CREATE TABLE device_token (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    token varchar(300) NOT NULL UNIQUE,
    platform varchar(16) NOT NULL,
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    updated_at timestamp with time zone NOT NULL DEFAULT now()
);
CREATE INDEX ix_device_token_user ON device_token (user_id);

-- Ítem marcado de la lista "Prepara tu casa" (El Niño).
CREATE TABLE prep_checklist_item (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    item_key varchar(40) NOT NULL,
    done boolean NOT NULL DEFAULT false,
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    updated_at timestamp with time zone NOT NULL DEFAULT now(),
    CONSTRAINT uq_prep_checklist_item_user_item_key UNIQUE (user_id, item_key)
);

-- Oficio del directorio con su contador de Visto Bueno Vecinal.
CREATE TABLE neighbor_service (
    id uuid PRIMARY KEY,
    owner_id uuid NOT NULL REFERENCES app_user (id),
    title varchar(120) NOT NULL,
    category varchar(40) NOT NULL,
    description varchar(1000),
    price_text varchar(80),
    schedule_text varchar(80),
    lat double precision NOT NULL,
    lng double precision NOT NULL,
    available_in_emergency boolean NOT NULL DEFAULT false,
    active boolean NOT NULL DEFAULT true,
    endorsement_count integer NOT NULL DEFAULT 0,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    updated_at timestamp with time zone NOT NULL DEFAULT now()
);
CREATE INDEX ix_neighbor_service_lat_lng ON neighbor_service (lat, lng);
CREATE INDEX ix_neighbor_service_owner ON neighbor_service (owner_id);

-- Visto Bueno de un vecino a un servicio (uno por vecino).
CREATE TABLE endorsement (
    id uuid PRIMARY KEY,
    service_id uuid NOT NULL REFERENCES neighbor_service (id),
    user_id uuid NOT NULL REFERENCES app_user (id),
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    updated_at timestamp with time zone NOT NULL DEFAULT now(),
    CONSTRAINT uq_endorsement_service_user UNIQUE (service_id, user_id)
);
CREATE INDEX ix_endorsement_user ON endorsement (user_id);

-- Reseña de un servicio.
CREATE TABLE review (
    id uuid PRIMARY KEY,
    service_id uuid NOT NULL REFERENCES neighbor_service (id),
    author_id uuid NOT NULL REFERENCES app_user (id),
    text varchar(1000) NOT NULL,
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    updated_at timestamp with time zone NOT NULL DEFAULT now()
);
CREATE INDEX ix_review_service ON review (service_id);
CREATE INDEX ix_review_author ON review (author_id);

-- Publicación del muro de la cadena de favores (sin dinero).
CREATE TABLE post (
    id uuid PRIMARY KEY,
    author_id uuid NOT NULL REFERENCES app_user (id),
    zone_id uuid NOT NULL REFERENCES zone (id),
    type varchar(40) NOT NULL,
    category varchar(40) NOT NULL,
    text varchar(1000) NOT NULL,
    photo_url varchar(500),
    lat double precision NOT NULL,
    lng double precision NOT NULL,
    status varchar(40) NOT NULL DEFAULT 'OPEN',
    client_id uuid,
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    updated_at timestamp with time zone NOT NULL DEFAULT now(),
    CONSTRAINT uq_post_author_client UNIQUE (author_id, client_id)
);
CREATE INDEX ix_post_zone_created_at ON post (zone_id, created_at);
CREATE INDEX ix_post_lat_lng ON post (lat, lng);

-- Vecino que respondió "yo ayudo" a una publicación.
CREATE TABLE post_response (
    id uuid PRIMARY KEY,
    post_id uuid NOT NULL REFERENCES post (id),
    responder_id uuid NOT NULL REFERENCES app_user (id),
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    updated_at timestamp with time zone NOT NULL DEFAULT now(),
    CONSTRAINT uq_post_response_post_responder UNIQUE (post_id, responder_id)
);
CREATE INDEX ix_post_response_responder ON post_response (responder_id);

-- Chat entre dos vecinos, opcionalmente sobre una publicación o un servicio.
CREATE TABLE conversation (
    id uuid PRIMARY KEY,
    post_id uuid REFERENCES post (id),
    service_id uuid REFERENCES neighbor_service (id),
    user_a_id uuid NOT NULL REFERENCES app_user (id),
    user_b_id uuid NOT NULL REFERENCES app_user (id),
    context_text varchar(200),
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    updated_at timestamp with time zone NOT NULL DEFAULT now()
);
CREATE INDEX ix_conversation_post ON conversation (post_id);
CREATE INDEX ix_conversation_service ON conversation (service_id);
CREATE INDEX ix_conversation_user_a ON conversation (user_a_id);
CREATE INDEX ix_conversation_user_b ON conversation (user_b_id);

-- Mensaje de chat. sender null = mensaje del sistema.
CREATE TABLE conversation_message (
    id uuid PRIMARY KEY,
    conversation_id uuid NOT NULL REFERENCES conversation (id),
    sender_id uuid REFERENCES app_user (id),
    text varchar(2000) NOT NULL,
    client_id uuid,
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    updated_at timestamp with time zone NOT NULL DEFAULT now(),
    CONSTRAINT uq_conversation_message_sender_client UNIQUE (sender_id, client_id)
);
CREATE INDEX ix_conversation_message_conversation_created_at ON conversation_message (conversation_id, created_at);

-- Alerta SOS de un vecino.
CREATE TABLE sos_alert (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES app_user (id),
    zone_id uuid NOT NULL REFERENCES zone (id),
    type varchar(40),
    lat double precision NOT NULL,
    lng double precision NOT NULL,
    note varchar(500),
    status varchar(40) NOT NULL DEFAULT 'ACTIVE',
    sms_code varchar(25),
    notified_count integer NOT NULL DEFAULT 0,
    client_id uuid,
    closed_at timestamp with time zone,
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    updated_at timestamp with time zone NOT NULL DEFAULT now(),
    CONSTRAINT uq_sos_alert_user_client UNIQUE (user_id, client_id)
);
CREATE INDEX ix_sos_alert_zone_created_at ON sos_alert (zone_id, created_at);
CREATE INDEX ix_sos_alert_lat_lng ON sos_alert (lat, lng);
CREATE INDEX ix_sos_alert_status_created_at ON sos_alert (status, created_at);

-- Vecino que responde a un SOS (voy, no puedo, llegué).
CREATE TABLE sos_response (
    id uuid PRIMARY KEY,
    alert_id uuid NOT NULL REFERENCES sos_alert (id),
    responder_id uuid NOT NULL REFERENCES app_user (id),
    status varchar(40) NOT NULL DEFAULT 'GOING',
    eta_minutes integer,
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    updated_at timestamp with time zone NOT NULL DEFAULT now(),
    CONSTRAINT uq_sos_response_alert_responder UNIQUE (alert_id, responder_id)
);
CREATE INDEX ix_sos_response_responder ON sos_response (responder_id);

-- "Estoy bien" o "necesito ayuda" durante una emergencia.
CREATE TABLE check_in (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES app_user (id),
    zone_id uuid NOT NULL REFERENCES zone (id),
    status varchar(40) NOT NULL,
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    updated_at timestamp with time zone NOT NULL DEFAULT now()
);
CREATE INDEX ix_check_in_zone_created_at ON check_in (zone_id, created_at);
CREATE INDEX ix_check_in_user ON check_in (user_id);

-- Capacidad del inventario de supervivencia extraída por la IA (con consentimiento).
CREATE TABLE capability (
    id uuid PRIMARY KEY,
    zone_id uuid NOT NULL REFERENCES zone (id),
    user_id uuid REFERENCES app_user (id),
    display_name varchar(120) NOT NULL,
    zone_label varchar(40),
    tag varchar(40) NOT NULL,
    kind varchar(40) NOT NULL,
    source_type varchar(40) NOT NULL,
    source_id uuid NOT NULL,
    evidence_text varchar(1000) NOT NULL,
    confidence double precision NOT NULL,
    lat double precision NOT NULL,
    lng double precision NOT NULL,
    removed_by_user boolean NOT NULL DEFAULT false,
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    updated_at timestamp with time zone NOT NULL DEFAULT now(),
    CONSTRAINT uq_capability_source_type_source_tag UNIQUE (source_type, source_id, tag)
);
CREATE INDEX ix_capability_zone_created_at ON capability (zone_id, created_at);
CREATE INDEX ix_capability_lat_lng ON capability (lat, lng);
CREATE INDEX ix_capability_zone_tag ON capability (zone_id, tag);
CREATE INDEX ix_capability_user ON capability (user_id);

-- Reporte del tablero humanitario. Dirección exacta solo para BRIGADIST y ADMIN.
CREATE TABLE vulnerable_report (
    id uuid PRIMARY KEY,
    zone_id uuid NOT NULL REFERENCES zone (id),
    reporter_id uuid REFERENCES app_user (id),
    category varchar(40) NOT NULL,
    detail varchar(1000) NOT NULL,
    priority varchar(40) NOT NULL,
    status varchar(40) NOT NULL DEFAULT 'PENDING',
    assigned_to_id uuid REFERENCES app_user (id),
    assigned_label varchar(120),
    lat double precision NOT NULL,
    lng double precision NOT NULL,
    address_text varchar(200),
    source varchar(40) NOT NULL DEFAULT 'USER',
    client_id uuid,
    source_capability_id uuid REFERENCES capability (id),
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    updated_at timestamp with time zone NOT NULL DEFAULT now(),
    CONSTRAINT uq_vulnerable_report_reporter_client UNIQUE (reporter_id, client_id)
);
CREATE INDEX ix_vulnerable_report_zone_created_at ON vulnerable_report (zone_id, created_at);
CREATE INDEX ix_vulnerable_report_lat_lng ON vulnerable_report (lat, lng);
CREATE INDEX ix_vulnerable_report_assigned_to ON vulnerable_report (assigned_to_id);
CREATE INDEX ix_vulnerable_report_source_capability ON vulnerable_report (source_capability_id);

-- Tengo / necesito un recurso. Exactamente uno: resourceType (emergencia) o mitigationType (prevención).
CREATE TABLE resource_listing (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES app_user (id),
    zone_id uuid NOT NULL REFERENCES zone (id),
    resource_type varchar(40),
    mitigation_type varchar(40),
    kind varchar(40) NOT NULL,
    text varchar(500) NOT NULL,
    quantity integer NOT NULL DEFAULT 1,
    location_text varchar(200),
    lat double precision NOT NULL,
    lng double precision NOT NULL,
    active boolean NOT NULL DEFAULT true,
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    updated_at timestamp with time zone NOT NULL DEFAULT now(),
    CONSTRAINT ck_resource_listing_1 CHECK ((resource_type IS NULL) <> (mitigation_type IS NULL)),
    CONSTRAINT ck_resource_listing_2 CHECK (quantity >= 0)
);
CREATE INDEX ix_resource_listing_zone_created_at ON resource_listing (zone_id, created_at);
CREATE INDEX ix_resource_listing_lat_lng ON resource_listing (lat, lng);
CREATE INDEX ix_resource_listing_user ON resource_listing (user_id);

-- Grupo de apoyo del directorio de emergencia.
CREATE TABLE support_group (
    id uuid PRIMARY KEY,
    zone_id uuid NOT NULL REFERENCES zone (id),
    name varchar(120) NOT NULL,
    icon varchar(40) NOT NULL,
    description varchar(500),
    active_now boolean NOT NULL DEFAULT false,
    schedule_text varchar(120),
    contact_phone varchar(15),
    members_count integer NOT NULL DEFAULT 0,
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    updated_at timestamp with time zone NOT NULL DEFAULT now()
);
CREATE INDEX ix_support_group_zone_created_at ON support_group (zone_id, created_at);

-- Vecino que se unió a un grupo de apoyo.
CREATE TABLE support_group_member (
    id uuid PRIMARY KEY,
    group_id uuid NOT NULL REFERENCES support_group (id),
    user_id uuid NOT NULL REFERENCES app_user (id),
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    updated_at timestamp with time zone NOT NULL DEFAULT now(),
    CONSTRAINT uq_support_group_member_group_user UNIQUE (group_id, user_id)
);
CREATE INDEX ix_support_group_member_user ON support_group_member (user_id);

-- Punto de reunión o refugio.
CREATE TABLE safe_point (
    id uuid PRIMARY KEY,
    zone_id uuid NOT NULL REFERENCES zone (id),
    name varchar(120) NOT NULL,
    type varchar(40) NOT NULL,
    capacity integer,
    occupied integer,
    lat double precision NOT NULL,
    lng double precision NOT NULL,
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    updated_at timestamp with time zone NOT NULL DEFAULT now()
);
CREATE INDEX ix_safe_point_zone_created_at ON safe_point (zone_id, created_at);
CREATE INDEX ix_safe_point_lat_lng ON safe_point (lat, lng);

-- Plan de rescate generado por la IA en una emergencia.
CREATE TABLE rescue_plan (
    id uuid PRIMARY KEY,
    zone_id uuid NOT NULL REFERENCES zone (id),
    generated_at timestamp with time zone NOT NULL,
    duration_ms bigint NOT NULL,
    missions_count integer NOT NULL DEFAULT 0,
    preventive_reports_count integer NOT NULL DEFAULT 0,
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    updated_at timestamp with time zone NOT NULL DEFAULT now()
);
CREATE INDEX ix_rescue_plan_zone_created_at ON rescue_plan (zone_id, created_at);

-- Misión: el vecino más cercano con la capacidad justa para un reporte.
CREATE TABLE mission_assignment (
    id uuid PRIMARY KEY,
    plan_id uuid NOT NULL REFERENCES rescue_plan (id),
    report_id uuid NOT NULL REFERENCES vulnerable_report (id),
    capability_id uuid NOT NULL REFERENCES capability (id),
    user_id uuid NOT NULL REFERENCES app_user (id),
    distance_m integer NOT NULL,
    reason varchar(500) NOT NULL,
    notified_at timestamp with time zone,
    status varchar(40) NOT NULL DEFAULT 'PROPOSED',
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    updated_at timestamp with time zone NOT NULL DEFAULT now()
);
CREATE INDEX ix_mission_assignment_plan ON mission_assignment (plan_id);
CREATE INDEX ix_mission_assignment_report ON mission_assignment (report_id);
CREATE INDEX ix_mission_assignment_capability ON mission_assignment (capability_id);
CREATE INDEX ix_mission_assignment_user ON mission_assignment (user_id);

-- Zona de riesgo calculada por la IA agrupando reportes cercanos (AMBER o RED).
CREATE TABLE risk_zone (
    id uuid PRIMARY KEY,
    zone_id uuid NOT NULL REFERENCES zone (id),
    name varchar(120) NOT NULL,
    level varchar(40) NOT NULL,
    centroid_lat double precision NOT NULL,
    centroid_lng double precision NOT NULL,
    report_count integer NOT NULL DEFAULT 0,
    score double precision NOT NULL,
    pattern_text varchar(1000) NOT NULL,
    homes_estimate integer NOT NULL DEFAULT 0,
    active boolean NOT NULL DEFAULT true,
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    updated_at timestamp with time zone NOT NULL DEFAULT now()
);
CREATE INDEX ix_risk_zone_zone_created_at ON risk_zone (zone_id, created_at);

-- Reporte de riesgo con foto (El Niño).
CREATE TABLE risk_report (
    id uuid PRIMARY KEY,
    zone_id uuid NOT NULL REFERENCES zone (id),
    reporter_id uuid NOT NULL REFERENCES app_user (id),
    category varchar(40) NOT NULL,
    text varchar(1000) NOT NULL,
    photo_url varchar(500),
    photo_analysis varchar(500),
    severity_hint double precision,
    lat double precision NOT NULL,
    lng double precision NOT NULL,
    address_text varchar(200),
    client_id uuid,
    risk_zone_id uuid REFERENCES risk_zone (id),
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    updated_at timestamp with time zone NOT NULL DEFAULT now(),
    CONSTRAINT uq_risk_report_reporter_client UNIQUE (reporter_id, client_id)
);
CREATE INDEX ix_risk_report_zone_created_at ON risk_report (zone_id, created_at);
CREATE INDEX ix_risk_report_lat_lng ON risk_report (lat, lng);
CREATE INDEX ix_risk_report_risk_zone ON risk_report (risk_zone_id);

-- Recurso de mitigación del inventario propuesto para una zona de riesgo.
CREATE TABLE mitigation_match (
    id uuid PRIMARY KEY,
    risk_zone_id uuid NOT NULL REFERENCES risk_zone (id),
    capability_id uuid NOT NULL REFERENCES capability (id),
    user_id uuid NOT NULL REFERENCES app_user (id),
    distance_m integer NOT NULL,
    coordinated_at timestamp with time zone,
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    updated_at timestamp with time zone NOT NULL DEFAULT now()
);
CREATE INDEX ix_mitigation_match_risk_zone ON mitigation_match (risk_zone_id);
CREATE INDEX ix_mitigation_match_capability ON mitigation_match (capability_id);
CREATE INDEX ix_mitigation_match_user ON mitigation_match (user_id);
