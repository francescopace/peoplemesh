-- V1: Full schema — identity, audit, mesh, skills.

-- ============================================================================
-- Schemas
-- ============================================================================

CREATE SCHEMA IF NOT EXISTS identity;
CREATE SCHEMA IF NOT EXISTS audit;
CREATE SCHEMA IF NOT EXISTS mesh;
CREATE SCHEMA IF NOT EXISTS skills;

-- ============================================================================
-- Extensions
-- ============================================================================

CREATE EXTENSION IF NOT EXISTS vector SCHEMA public;

-- ============================================================================
-- Identity
-- ============================================================================

CREATE TABLE identity.user_identity (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    oauth_provider    VARCHAR(50)  NOT NULL,
    oauth_subject     VARCHAR(255) NOT NULL,
    node_id           UUID         NOT NULL,
    can_create_job    BOOLEAN      NOT NULL DEFAULT false,
    can_manage_skills BOOLEAN      NOT NULL DEFAULT false,
    last_active_at    TIMESTAMPTZ,
    UNIQUE (oauth_provider, oauth_subject)
);

CREATE TABLE identity.consumed_consent_token (
    token_hash  VARCHAR(64)  PRIMARY KEY,
    consumed_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    expires_at  TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_consumed_token_expires
    ON identity.consumed_consent_token (expires_at);

-- ============================================================================
-- Audit
-- ============================================================================

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
CREATE INDEX idx_audit_action    ON audit.audit_log (action, timestamp);

-- ============================================================================
-- Mesh
-- ============================================================================

CREATE TABLE mesh.mesh_node (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    created_by      UUID         NOT NULL,
    node_type       VARCHAR(30)  NOT NULL,
    title           VARCHAR(200) NOT NULL,
    description     TEXT         NOT NULL,
    external_id     TEXT,
    tags            TEXT[],
    structured_data JSONB,
    country         VARCHAR(10),
    embedding       vector(1024),
    searchable      BOOLEAN      NOT NULL DEFAULT true,
    closed_at       TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CHECK (node_type IN ('USER', 'JOB', 'COMMUNITY', 'EVENT', 'PROJECT', 'INTEREST_GROUP'))
);

-- Self-referencing FK: created_by points to another mesh_node (or self for USER)
ALTER TABLE mesh.mesh_node ADD CONSTRAINT mesh_node_created_by_fkey
    FOREIGN KEY (created_by) REFERENCES mesh.mesh_node(id);

-- FK: user_identity.node_id -> mesh_node with CASCADE
ALTER TABLE identity.user_identity ADD CONSTRAINT user_identity_node_id_fkey
    FOREIGN KEY (node_id) REFERENCES mesh.mesh_node(id) ON DELETE CASCADE;

-- mesh_node indexes
CREATE INDEX idx_mesh_node_created_by ON mesh.mesh_node (created_by);
CREATE INDEX idx_mesh_node_type       ON mesh.mesh_node (node_type);

CREATE INDEX idx_mesh_node_embedding ON mesh.mesh_node
    USING hnsw (embedding vector_cosine_ops)
    WHERE embedding IS NOT NULL;

CREATE UNIQUE INDEX idx_mesh_node_user_unique
    ON mesh.mesh_node (created_by) WHERE node_type = 'USER';

CREATE INDEX idx_mesh_node_job_skills ON mesh.mesh_node
    USING gin ((structured_data->'skills_required'))
    WHERE node_type = 'JOB';

CREATE INDEX idx_mesh_node_external_id_user
    ON mesh.mesh_node (external_id) WHERE node_type = 'USER';

-- user_identity indexes
CREATE INDEX idx_user_identity_oauth
    ON identity.user_identity (oauth_provider, oauth_subject);
CREATE INDEX idx_user_identity_node
    ON identity.user_identity (node_id);
CREATE INDEX idx_user_identity_activity
    ON identity.user_identity (node_id, last_active_at);

-- ── Consent ──

CREATE TABLE mesh.mesh_node_consent (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    node_id        UUID         NOT NULL REFERENCES mesh.mesh_node(id),
    scope          VARCHAR(100) NOT NULL,
    granted_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    ip_hash        VARCHAR(64),
    policy_version VARCHAR(20)  NOT NULL,
    revoked_at     TIMESTAMPTZ
);

CREATE INDEX idx_mesh_node_consent_node_scope_active
    ON mesh.mesh_node_consent (node_id, scope) WHERE revoked_at IS NULL;

-- ============================================================================
-- Skills
-- ============================================================================

CREATE TABLE skills.skill_catalog (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name          VARCHAR(200) NOT NULL,
    description   TEXT,
    level_scale   JSONB NOT NULL,
    source        VARCHAR(50),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_skill_catalog_name
    ON skills.skill_catalog (name);

CREATE TABLE skills.skill_definition (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    catalog_id         UUID NOT NULL REFERENCES skills.skill_catalog(id) ON DELETE CASCADE,
    category           VARCHAR(200) NOT NULL,
    name               VARCHAR(200) NOT NULL,
    aliases            TEXT[],
    lxp_recommendation VARCHAR(500),
    embedding          vector,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (catalog_id, name)
);

CREATE INDEX idx_skill_def_catalog  ON skills.skill_definition (catalog_id);
CREATE INDEX idx_skill_def_catalog_category_name
    ON skills.skill_definition (catalog_id, category, name);
CREATE INDEX idx_skill_def_aliases  ON skills.skill_definition USING gin (aliases);

CREATE TABLE skills.skill_assessment (
    node_id     UUID NOT NULL REFERENCES mesh.mesh_node(id) ON DELETE CASCADE,
    skill_id    UUID NOT NULL REFERENCES skills.skill_definition(id) ON DELETE CASCADE,
    level       SMALLINT NOT NULL CHECK (level >= 0),
    interest    BOOLEAN NOT NULL DEFAULT false,
    source      VARCHAR(20) NOT NULL DEFAULT 'SELF',
    assessed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (node_id, skill_id),
    CHECK (source IN ('SELF', 'IMPORT', 'CV_PARSE'))
);

CREATE INDEX idx_skill_assessment_node  ON skills.skill_assessment (node_id);
CREATE INDEX idx_skill_assessment_level ON skills.skill_assessment (skill_id, level);
