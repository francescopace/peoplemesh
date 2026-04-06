-- V5: Job posting domain and recruiter pipeline

CREATE SCHEMA IF NOT EXISTS jobs;

CREATE TABLE jobs.job_posting (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_user_id       UUID         NOT NULL REFERENCES identity.user_identity(id),
    title               VARCHAR(150) NOT NULL,
    description         TEXT         NOT NULL,
    requirements_text   TEXT,
    skills_required     TEXT[],
    work_mode           VARCHAR(20),
    employment_type     VARCHAR(20),
    country             VARCHAR(10),
    status              VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    embedding           vector(1536),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    published_at        TIMESTAMPTZ,
    closed_at           TIMESTAMPTZ,
    CHECK (status IN ('DRAFT', 'PUBLISHED', 'PAUSED', 'FILLED', 'CLOSED'))
);

CREATE INDEX idx_job_posting_owner ON jobs.job_posting (owner_user_id, status);
CREATE INDEX idx_job_posting_country ON jobs.job_posting (country) WHERE status = 'PUBLISHED';
CREATE INDEX idx_job_posting_embedding ON jobs.job_posting
    USING hnsw (embedding vector_cosine_ops)
    WHERE status = 'PUBLISHED' AND embedding IS NOT NULL;

CREATE TABLE jobs.candidate_pipeline_entry (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id              UUID         NOT NULL REFERENCES jobs.job_posting(id) ON DELETE CASCADE,
    candidate_user_id   UUID         NOT NULL REFERENCES identity.user_identity(id),
    stage               VARCHAR(30)  NOT NULL DEFAULT 'APPLIED',
    shortlisted         BOOLEAN      NOT NULL DEFAULT false,
    notes               TEXT,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_stage_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CHECK (stage IN ('APPLIED', 'SHORTLISTED', 'SCREENING', 'INTERVIEW', 'OFFER', 'HIRED', 'REJECTED')),
    UNIQUE (job_id, candidate_user_id)
);

CREATE INDEX idx_pipeline_job_stage ON jobs.candidate_pipeline_entry (job_id, stage);
CREATE INDEX idx_pipeline_shortlist ON jobs.candidate_pipeline_entry (job_id, shortlisted) WHERE shortlisted = true;
