-- V4: Add optimistic locking columns and unique constraint on pending connection requests.

ALTER TABLE identity.user_identity ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE profiles.user_profile  ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

CREATE UNIQUE INDEX IF NOT EXISTS idx_connreq_pending_unique
    ON profiles.connection_request (
        LEAST(from_user_id, to_user_id),
        GREATEST(from_user_id, to_user_id)
    )
    WHERE status = 'PENDING';
