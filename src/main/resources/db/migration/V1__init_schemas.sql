-- V1: Create separated schemas and identity tables
-- Schema separation enforces data isolation between identity, profile, and audit data.

CREATE SCHEMA IF NOT EXISTS identity;
CREATE SCHEMA IF NOT EXISTS profiles;
CREATE SCHEMA IF NOT EXISTS audit;

-- Identity schema: OAuth credentials and email (restricted access)
CREATE TABLE identity.user_identity (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    oauth_provider  VARCHAR(50)  NOT NULL,
    oauth_subject   VARCHAR(255) NOT NULL,
    email_encrypted TEXT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at      TIMESTAMPTZ,
    UNIQUE (oauth_provider, oauth_subject)
);

CREATE INDEX idx_user_identity_oauth
    ON identity.user_identity (oauth_provider, oauth_subject)
    WHERE deleted_at IS NULL;
