-- V1: Consolidated baseline schema — identity, audit, mesh, skills.

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
    is_admin          BOOLEAN      NOT NULL DEFAULT false,
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
    node_type       VARCHAR(30)  NOT NULL,
    title           VARCHAR(200) NOT NULL,
    description     TEXT         NOT NULL,
    external_id     TEXT,
    tags            TEXT[],
    structured_data JSONB,
    country         VARCHAR(10),
    embedding       vector(384),
    searchable      BOOLEAN      NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CHECK (node_type IN ('USER', 'JOB', 'COMMUNITY', 'EVENT', 'PROJECT', 'INTEREST_GROUP'))
);

-- FK: user_identity.node_id -> mesh_node with CASCADE
ALTER TABLE identity.user_identity ADD CONSTRAINT user_identity_node_id_fkey
    FOREIGN KEY (node_id) REFERENCES mesh.mesh_node(id) ON DELETE CASCADE;

-- mesh_node indexes
CREATE INDEX idx_mesh_node_type       ON mesh.mesh_node (node_type);

CREATE INDEX idx_mesh_node_embedding ON mesh.mesh_node
    USING hnsw (embedding vector_cosine_ops)
    WHERE embedding IS NOT NULL;

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

CREATE TABLE skills.skill_definition (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name          VARCHAR(200) NOT NULL,
    aliases       TEXT[] NOT NULL DEFAULT ARRAY[]::TEXT[],
    usage_count   INTEGER NOT NULL DEFAULT 0 CHECK (usage_count >= 0),
    embedding     vector(384),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (name)
);

CREATE INDEX idx_skill_def_usage_count ON skills.skill_definition (usage_count);
CREATE INDEX idx_skill_def_aliases  ON skills.skill_definition USING gin (aliases);

-- ============================================================================
-- Persistence performance indexes (collapsed from former V2)
-- ============================================================================

-- ATS job upsert lookup on source/external_id.
CREATE UNIQUE INDEX idx_mesh_node_job_source_external_id
    ON mesh.mesh_node (
        (structured_data->>'source'),
        (structured_data->>'external_id')
    )
    WHERE node_type = 'JOB' AND structured_data IS NOT NULL;

-- Speed up overlap filters on profile tags when used in search/match.
CREATE INDEX idx_mesh_node_tags_gin
    ON mesh.mesh_node USING gin (tags);

CREATE INDEX idx_skill_def_embedding
    ON skills.skill_definition
    USING hnsw (embedding vector_cosine_ops)
    WHERE embedding IS NOT NULL;
