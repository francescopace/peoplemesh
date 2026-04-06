-- V3: Audit log table (append-only, never contains profile content)

CREATE TABLE audit.audit_log (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id_hash  VARCHAR(64)  NOT NULL,
    action        VARCHAR(100) NOT NULL,
    tool_name     VARCHAR(100),
    timestamp     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    ip_hash       VARCHAR(64),
    metadata_json JSONB
);

CREATE INDEX idx_audit_user_hash ON audit.audit_log (user_id_hash, timestamp);
CREATE INDEX idx_audit_action ON audit.audit_log (action, timestamp);
