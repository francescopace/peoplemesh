-- V2: Enable pgvector and create profile tables

CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE profiles.user_profile (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id              UUID         NOT NULL UNIQUE REFERENCES identity.user_identity(id),
    profile_version      VARCHAR(20),
    roles_encrypted      TEXT,
    seniority            VARCHAR(20),
    industries_encrypted TEXT,
    skills_technical     TEXT[],
    skills_soft          TEXT[],
    tools_and_tech       TEXT[],
    languages_spoken     TEXT[],
    work_mode            VARCHAR(20),
    employment_type      VARCHAR(20),
    topics_frequent      TEXT[],
    learning_areas       TEXT[],
    project_types        TEXT[],
    collaboration_goals  TEXT[],
    country              VARCHAR(10),
    city_encrypted       TEXT,
    timezone             VARCHAR(60),
    show_city            BOOLEAN      NOT NULL DEFAULT false,
    show_country         BOOLEAN      NOT NULL DEFAULT true,
    searchable           BOOLEAN      NOT NULL DEFAULT false,
    contact_via          VARCHAR(50)  NOT NULL DEFAULT 'platform_only',
    embedding            vector(1536),
    generated_at         TIMESTAMPTZ,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at           TIMESTAMPTZ,
    last_active_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_profile_user_id ON profiles.user_profile (user_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_profile_searchable ON profiles.user_profile (searchable) WHERE deleted_at IS NULL AND searchable = true;

-- HNSW index for fast approximate nearest-neighbor search
CREATE INDEX idx_profile_embedding ON profiles.user_profile
    USING hnsw (embedding vector_cosine_ops)
    WHERE deleted_at IS NULL AND searchable = true AND embedding IS NOT NULL;

CREATE TABLE profiles.profile_consent (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id        UUID        NOT NULL REFERENCES identity.user_identity(id),
    scope          VARCHAR(100) NOT NULL,
    granted_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    ip_hash        VARCHAR(64),
    policy_version VARCHAR(20) NOT NULL,
    revoked_at     TIMESTAMPTZ
);

CREATE INDEX idx_consent_user ON profiles.profile_consent (user_id) WHERE revoked_at IS NULL;

CREATE TABLE profiles.connection_request (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    from_user_id     UUID        NOT NULL REFERENCES identity.user_identity(id),
    to_user_id       UUID        NOT NULL REFERENCES identity.user_identity(id),
    message_encrypted TEXT,
    status           VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    responded_at     TIMESTAMPTZ,
    CHECK (from_user_id != to_user_id)
);

CREATE INDEX idx_connreq_to_user ON profiles.connection_request (to_user_id, status);
CREATE INDEX idx_connreq_from_user_daily ON profiles.connection_request (from_user_id, created_at);

CREATE TABLE profiles.connection (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_a_id                   UUID        NOT NULL REFERENCES identity.user_identity(id),
    user_b_id                   UUID        NOT NULL REFERENCES identity.user_identity(id),
    shared_contact_a_encrypted  TEXT,
    shared_contact_b_encrypted  TEXT,
    connected_at                TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_a_id, user_b_id),
    CHECK (user_a_id != user_b_id)
);

CREATE INDEX idx_connection_users ON profiles.connection (user_a_id, user_b_id);

CREATE TABLE profiles.blocklist_entry (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    blocker_id  UUID        NOT NULL REFERENCES identity.user_identity(id),
    blocked_id  UUID        NOT NULL REFERENCES identity.user_identity(id),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (blocker_id, blocked_id),
    CHECK (blocker_id != blocked_id)
);
