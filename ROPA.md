# Record of Processing Activities (ROPA)

**Controller:** PeopleMesh  
**Date:** April 2026  
**Status:** Draft — to be reviewed by DPO / legal counsel

---

## Processing Activities

| # | Activity | Purpose | Lawful Basis | Data Categories | Data Subjects | Recipients / Sub-processors | Transfers Outside EU | Retention |
|---|----------|---------|-------------|----------------|---------------|---------------------------|---------------------|-----------|
| 1 | **User registration** | Create user account via OAuth provider | Consent (Art. 6(1)(a)) | OAuth subject, provider name; **`identity.user_identity`** links to **`mesh.mesh_node`** via **`node_id`** (same person may have multiple identities across providers). Contact identifier for USER nodes is **`mesh.mesh_node.external_id`** (often an email) | Platform users | OAuth login providers (Google, Microsoft) | Depends on provider | Active account lifetime; immediately hard-deleted on erasure (**`mesh_node`** deletion cascades to linked **`user_identity`** rows) |
| 2 | **Profile creation and storage** | Build multi-dimensional user profile | Consent — scope: `professional_matching` | Name, contact identifier on USER **`mesh_node`** (**`external_id`**, often an email), city, country, timezone, roles, industries, skills, hobbies, interests, education, personality, work preferences | Platform users | None (internal storage) | No | Active account lifetime; deleted on account erasure per retention policy |
| 3 | **Semantic embedding generation** | Generate vector representations for matching | Consent — scope: `embedding_processing` | Roles, industries, skills, hobbies, sports, education, causes, personality, work mode, employment type, country (PII excluded: no name, email) | Platform users | Configured AI provider (cloud or self-hosted) | Possible when using a cloud provider (may process outside EU under SCCs); none when self-hosted | Embeddings stored until profile deletion; text sent transiently to API |
| 4 | **Profile matching** | Suggest relevant people, jobs, communities | Consent — scope: `professional_matching` | Profile embeddings, metadata filters (country, seniority, work mode); country and city are always shown in match results (visibility is controlled exclusively via GDPR Art. 18 restrict processing) | Platform users | None (internal pgvector query) | No | Results not persisted; computed on demand |
| 5 | **CV import and structuring** | Parse CV to pre-fill profile fields | Consent (Art. 6(1)(a)) — user-initiated action | Full CV file content (as uploaded by user) | Platform users | Docling (self-hosted). Configured AI provider for LLM structuring (cloud or self-hosted) | Possible when using a cloud provider; none when self-hosted | Parsed data returned directly to client for preview; original file not retained; no server-side persistence of parsed result |
| 6 | **OAuth profile import** | Import profile data from GitHub | Consent (Art. 6(1)(a)) — user-initiated action | Provider profile fields (name, bio, skills, email, avatar, repository languages, repo topics) | Platform users | GitHub (OAuth data access + REST API for repos/languages) | Depends on provider | No server-side persistence of import data; imported data is sent to the browser via `postMessage` (popup window), previewed client-side, and applied only if the user confirms |
| 7 | **Job postings (ATS ingest)** | Ingest job data from external ATS for candidate matching | Legitimate interest (Art. 6(1)(f)) — employer-initiated | Job title, description, requirements, skills, external ATS identifier | ATS operator (employer) | External ATS (data source; no PII sent to ATS) | Depends on ATS provider | Deleted on owner account erasure or when ATS marks job as closed/deleted |
| 8 | **Mesh node management** | Create and publish mesh nodes (communities, events, projects, interest groups) | Consent — scope: `professional_matching` | Node title, description, tags, structured fields, creator reference (`mesh_node.created_by`); optional **`closed_at`** for lifecycle | Platform users | None | No | Deleted on owner account erasure |
| 9 | **Auto-clustering** | Discover communities from profile similarities | Consent — scope: `embedding_processing` | Aggregated trait lists (skills, hobbies, sports, causes, topics, countries) from cluster members | Platform users | Configured AI provider for cluster naming (cloud or self-hosted) | Possible when using a cloud provider; none when self-hosted | Cluster data refreshed periodically |
| 10 | **Audit logging** | Security, compliance, notifications | Legitimate interest (Art. 6(1)(f)) | SHA-256 hashed mesh node ID (session principal), SHA-256 hashed IP, action name, tool name, timestamp, optional metadata | Platform users | None | No | Retained for security purposes; pseudonymised |
| 11 | **Consent records** | Track and prove consent | Legal obligation (Art. 6(1)(c)) | Mesh node ID (`mesh.mesh_node` primary key), scope, policy version, hashed IP, grant/revoke timestamps; stored in **`mesh.mesh_node_consent`** keyed by **`node_id`** | Platform users | None | No | Retained for compliance; deleted on account erasure |
| 12 | **Notifications** | Inform users of account activity | Legitimate interest (Art. 6(1)(f)) | Email address (for delivery), action summary | Platform users | Email provider (planned: AWS SES) | No (EU region) | Not persisted beyond delivery |
| 13 | **Skill catalog and assessment** | Track user skill proficiency for matching and analytics | Consent — scope: `professional_matching` | Skill ID, proficiency level (0–5), interest flag, assessment source (SELF/IMPORT/CV_PARSE), timestamp; assessments in **`skills.skill_assessment`** (`node_id` → `mesh.mesh_node`) | Platform users | None (internal storage). Catalog definitions in **`skills.skill_catalog`** / **`skills.skill_definition`**. Catalog write operations restricted to `can_manage_skills` entitlement. | No | Deleted on account erasure when the USER **`mesh_node`** row is removed (**`ON DELETE CASCADE`**) |
| 14 | **Public profile viewing** | Allow authenticated users to view published profiles | Consent — scope: `professional_matching` (profile visibility / searchability controlled on the USER **`mesh_node`**) | Public profile fields (name, contact **`external_id`**, roles, skills, skill assessments, interests, location) | Platform users (viewers and profile owners) | None (internal read) | No | No additional data stored; reads existing profile data subject to visibility rules |

**Consent enforcement:** All consent scopes listed above (`professional_matching`, `embedding_processing`) are enforced at service level. Revoking a consent via the Privacy Dashboard immediately blocks the corresponding processing activity; re-granting it restores access. Enforcement is checked at the point of each operation, not only at consent grant time. CV and OAuth provider imports are user-initiated actions and do not require a separate consent toggle.

## Technical and Organisational Measures (Art. 32)

- **TLS in transit** for all HTTPS traffic and **infrastructure-level encryption at rest** (database / storage)
- **Access control** via OAuth2 (OIDC) multi-provider authentication (no password storage); session principal is the USER **`mesh_node.id`**; activity for retention is tracked on **`identity.user_identity.last_active_at`**
- HMAC-SHA256 signed session cookies (HttpOnly, SameSite=Lax, Secure)
- CSRF custom header check for state-changing API requests
- CORS origin allowlist
- Security headers (HSTS, CSP, X-Frame-Options, Referrer-Policy, Permissions-Policy)
- TLS 1.3 for HTTPS
- Pseudonymised audit logs (hashed mesh node ID and IP)
- Maintenance endpoints protected by API key + IP allowlist
- Configurable data retention with automatic enforcement
- Granular consent management with per-scope revocation

## Contact

- **Data Controller contact:** security@peoplemesh.org
- **DPO:** (to be appointed)

## Review Schedule

This ROPA should be reviewed and updated whenever:
- New processing activities are added
- New sub-processors are introduced
- Data flows change
- At minimum annually
