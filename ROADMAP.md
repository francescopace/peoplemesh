# PeopleMesh — Product Roadmap

## Guiding Principle

Ship the smallest possible system that proves: "I can find the right person faster than current tools." Everything else is secondary.

## Product Philosophy (Open-Source First)

1. Internal discovery first: solve real intranet pain before expanding scope.
2. Open-source core first: improvements in matching and search should land in the OSS core by default.
3. Trust and privacy first: opt-in data use, transparent controls, no personal data brokerage.
4. Sustainable enterprise value: monetize reliability, compliance, integrations, and support, not attention or ads.

---

## Phase 0 — Pre-Pilot (Critical Foundation) ✅ COMPLETE

Goal: Have a working end-to-end matching system for a small team.

- [x] **Semantic Search** — Free text search (Google/ChatGPT style), LLM structured query parsing (`LlmSearchQueryParser`), embedding generation (`EmbeddingService` via LangChain4j), vector search (pgvector HNSW), hybrid scoring (embedding + metadata via `MatchingService`), Top-K retrieval (candidate pool 50). REST: `POST /api/v1/matches/prompt` and related endpoints. MCP: `peoplemesh_match`, `peoplemesh_match_me`, `peoplemesh_match_node`, `peoplemesh_get_my_profile`.
- [x] **Unified mesh_node model** — Single `mesh_node` table for all entity types (USER, JOB, COMMUNITY, EVENT, PROJECT, INTEREST_GROUP) with polymorphic `node_type`, `structured_data` JSON, and pgvector `embedding`. Profiles stored as USER nodes. Ownership graph via `created_by` FK.
- [x] **Multi-dimensional profiles** — Professional + personal data with semantic embeddings. Profile building via web UI, CV import (Docling + LLM via `DoclingCvParser` / `CvProfileMergeService`), GitHub import (`OAuthProfileParser`). Import preview modal with per-field selection before applying.
- [x] **LangChain4j abstraction** — OpenAI in prod, Ollama/Granite locally (zero-cost dev). Configurable embedding and chat models.
- [x] **Profile Quality (Seed Data)** — Manual onboarding via web UI + LDAP bulk import (`LdapImportService` with preview, audit, batch embedding generation via maintenance endpoints). Required fields: role, skills, experience summary. Optional: Slack username, languages.
- [x] **Contact Layer** — "Contact on Slack", copy email/username on public profiles. No Slack API integration (by design).
- [x] **Fast Search UX** — < 2s response time target, clean search-to-results UI with min score threshold filtering.
- [x] **Improved Ranking** — Hybrid scoring tuning in `MatchingService` (embedding similarity + skill-level weighting + metadata). Skill Catalog adds proficiency-aware matching.
- [x] **Basic profile enrichment (LLM from CV)** — CV import via Docling + LLM structuring (`LlmProfileStructuring`), GitHub import with repo language/topic enrichment.
- [x] **Privacy dashboard** — GDPR export (Art. 15/20), account deletion (Art. 17, hard delete), processing restriction (Art. 18), granular per-scope consent management (`MeshNodeConsent`). Two-column layout. Retention enforcement via `GdprService`.
- [x] **Skill Catalog** — Importable taxonomies (ESCO, O*NET, custom CSV), self-assessment (0–5 scale), reconciliation with free-text tags (`SkillReconciliationService`), multi-catalog. Write ops gated by `is_admin` entitlement.
- [x] **Skill Catalog web UI** — Catalog management page, skill self-assessment in profile with catalog selector, level bars, interest indicators, reconciliation suggestions. Level badges on search result skill tags.
- [x] **Public profiles** — Read-only view (`#/people/:id`) for published users, accessible from search results, My Mesh, job candidate lists. Displays identity, professional info, skill assessments, interests, location, contact actions.
- [x] **Job ingestion** — JOB mesh nodes ingested from external ATS via `POST /api/v1/maintenance/ingest/jobs` with idempotent `external_id`-based upsert (`JobService`). Manual JOB creation via user APIs is blocked.
- [x] **Auto-discovered communities** — K-means clustering on profile embeddings (`ClusteringService` / `ClusteringScheduler`) with LLM-generated names (`ClusterNamingLlm`, heuristic fallback). Feature-flagged, disabled by default.
- [x] **Unified My Mesh view** — Status filters (All/Discover/Joined) for discovering and managing mesh nodes.
- [x] **Static SPA web UI** — Vanilla JS + CSS SPA with views for landing, profile, search, explore, jobs, events, skills, privacy, public profile, terms, privacy policy. Event-driven notification hooks (dry-run mode).
- [x] **OAuth multi-provider auth** — Google, Microsoft, GitHub via `OAuthLoginResource` + `OAuthTokenExchangeService`. HMAC-signed session cookies (`SessionService`). OIDC bearer for MCP.
- [x] **LDAP directory import** — `LdapImportService` with configurable URL/bind/base/filter/paging, preview endpoint, import with audit, batch embedding generation. Exposed via maintenance endpoints (`X-Maintenance-Key`) and operator CLI (`./pmc` Bash script).
- [x] **Access Control (Light)** — Entitlement-based access via `EntitlementService` (`is_admin` flag on `UserIdentity`), configured per environment.
- [x] **Security hardening** — CSRF header filter, security headers (HSTS, CSP, etc.), bot blocking filter, CORS allowlist, maintenance API key + CIDR restriction.
- [x] **Audit trail** — Pseudonymised audit logging (`AuditLogEntry`) with SHA-256 hashed mesh node ID and IP. No profile content in logs.
- [x] **DPIA** — Data Protection Impact Assessment drafted, covering GDPR Art. 22, data minimisation, retention, encryption, consent, and sub-processor risks.
- [x] Basic reranking logic — implemented in `SearchService.rerank` with seniority-aware boost/penalty after unified scoring

---

## Phase 1 — Pilot (Internal Team Validation)

Goal: Prove real usage in staffing / expert discovery.

Execution order for pilot validation: **track -> feedback -> improve**.

### MUST-HAVE

- [ ] **Usage Tracking** — Searches per user, clicks per result, contacts initiated. *Not implemented:* no analytics/telemetry layer; audit log tracks actions but no search-specific usage metrics or dashboards.
- [ ] **Feedback Loop (Manual)** — Collect "good match / bad match" per result, adjust scoring. *Not implemented:* no feedback collection UI or storage.
- [ ] **Matching Quality Layer** — Skill normalization (OpenShift ↔ K8s, etc.), must-have vs nice-to-have logic in query parsing, language filtering (hard constraint), seniority boost formula. *Mostly done:* query parsing supports explicit `must_have` / `nice_to_have` (`LlmSearchQueryParser`, `ParsedSearchQuery`); language constraints are pushed into retrieval (`MeshNodeSearchRepository.unifiedVectorSearch`); scoring includes must-have penalty and seniority rerank (`SearchService`). *Remaining:* broaden/standardize synonym normalization coverage (beyond current alias handling and catalog reconciliation) and keep tuning/validation on pilot data.
- [ ] **Email Delivery in NotificationService** — `NotificationService` currently runs in dry-run mode (`dryRun=true`) and logs notifications without sending them. Add a real transport (SMTP via Quarkus Mailer or external provider such as SendGrid/SES) to enable actual email notifications (match alerts, welcome, consent changes, etc.).
- [ ] **Share to Slack (Light)** — Copy profile summary, share in Slack channels. *Not implemented.*
- [ ] **CV import enrichment: certifications** — Add dedicated `professional.certifications` support end-to-end (schema, `LlmProfileStructuring` extraction/normalization, `ProfileService` selective import, profile/import UI mapping, fake data + seed refresh, docs/tests).

### NICE-TO-HAVE

- [ ] Multi-view embedding (core + skills) — single embedding per node today
- [ ] Name and surname search via LLM query parsing — detect person-name intent in free-text prompts and enrich structured query fields (for example `firstName`/`lastName`) before retrieval/ranking.
- [ ] Reranker (Granite or custom)

---

## Phase 2 — Pilot Expansion

Goal: Replicate success in multiple teams / orgs.

### MUST-HAVE

- [ ] **Multi-Tenant (Basic)** — Tenant isolation so each company sees only its own data. *Current state:* single global mesh, `company` is metadata only, no row-level filtering. *Target:*
  - `tenant_id` column on `mesh_node`, `user_identity`, `mesh_node_consent`, and skill tables
  - Tenant resolved at login from OIDC claims, stored in session with `mesh_node.id`
  - Hibernate filter or Panache base query appends `tenant_id = :current`
  - Embeddings/indexes partitioned or filtered per tenant
  - ATS ingest scoped via `X-Tenant-Id` or API key mapping
  - Skill Catalog bound to tenants (already multi-catalog)
  - Clustering and GDPR scoped per tenant
- [ ] **Access Control (Extended)** — Email domain restriction, broader role model beyond the current `is_admin` entitlement.
- [ ] **Metrics Dashboard** — Time-to-find, match quality (manual feedback), adoption rate. *Not implemented.*
- [ ] **Better Matching** — Multi-embedding weighting, reranker, diversity in results. *Not implemented beyond current hybrid scoring.*

### NICE-TO-HAVE

- [ ] Slack deep linking (if mapping available)
- [ ] Query history / saved searches

---

## Phase 3 — Enterprise Productization

Goal: Turn into an enterprise-ready product.

### MUST-HAVE

- [ ] **Integrations** — HRIS (read-only first), Slack (official app).
  - **Workday ATS connector**: pull Job Requisitions via RaaS or REST API, map to `AtsJobPayload`, feed into `JobService.upsertFromAts()`. Options: `@Scheduled` Quarkus service or external bridge calling `POST /api/v1/maintenance/ingest/jobs`. Auth: Workday OAuth 2.0 or RaaS Basic Auth.
- [ ] **Enterprise Features** — Full role-based access (beyond the current `is_admin` entitlement), admin console UI. *Partial:* SSO via OIDC and audit logs already implemented in Phase 0.
- [ ] **Matching Engine v2** — Domain-specific embeddings, advanced reranking. *Not started.*
- [ ] **Deployment Options** — SaaS, private cloud, on-prem (OpenShift). *Partial:* `Dockerfile.jvm` (UBI9 OpenJDK 21 runtime) available; native profile in `pom.xml`. No Helm chart, no Operator, no multi-env deployment pipeline yet.

---

## Phase 4 — Enterprise Scale and Ecosystem

Goal: Scale adoption across multiple organizations while preserving tenant boundaries and trust.

### MUST-HAVE

- [ ] **Profile Portability** — Export/import user profile data between tenants with explicit user consent and auditability. *Partial:* GDPR data export already implemented.
- [ ] **Enterprise Integration Pack** — Production-grade connectors (HRIS/ATS/identity/collaboration) with clear boundaries and read-only-first defaults. *Not started.*
- [ ] **Operations and Governance at Scale** — Tenant-level admin controls, policy templates, and compliance reporting. *Not started.*

### NICE-TO-HAVE

- [ ] Verified credentials (light)
- [ ] Tenant-level analytics packs

---

## Phase 5 — Sustainability Expansion

Goal: Grow sustainable revenue while keeping the open-source core and privacy commitments intact.

### MUST-HAVE

- [ ] **Commercial Licensing Program** — Clear commercial terms for organizations that cannot operate under AGPL obligations. *Not started.*
- [ ] **Managed Offering and SLAs** — Hosted deployments, support tiers, and reliability guarantees. *Not started.*
- [ ] **Enterprise Services** — Onboarding, migration, and integration services for large deployments. *Not started.*

### NICE-TO-HAVE

- [ ] Privacy-safe aggregated benchmarking (organization-level only, no personal data resale)
- [ ] Partner ecosystem for implementation support

---

## Cross-Phase Priorities (Always)

1. Matching Quality > Everything: Always invest here first.
2. Profile Quality > Quantity: Better 20 users than 1000 bad profiles.
3. Open-Source Core Momentum: Keep core improvements upstream and documented.
4. Trust and Privacy: Always opt-in, no hidden data usage.
5. Sustainable Monetization: Monetize enterprise value, never personal data exploitation.

---

## Summary

| Phase | Status | Done | Remaining |
|-------|--------|------|-----------|
| **Phase 0** | **COMPLETE** | 24/24 | — |
| **Phase 1** | In progress | 0/6 must-have | Usage tracking, feedback loop, matching quality finalization, email notifications, Slack share, certifications import |
| **Phase 2** | Not started | 0/4 | Multi-tenant, access control, metrics, better matching |
| **Phase 3** | Not started | 0/4 (SSO + audit done in P0) | Integrations, RBAC, matching v2, deployment |
| **Phase 4** | Not started | 0/3 (export done in P0) | Portability, enterprise integration pack, governance at scale |
| **Phase 5** | Not started | 0/3 | Commercial licensing, managed offering, enterprise services |
