# Data Protection Impact Assessment (DPIA)

**System:** PeopleMesh  
**Date:** April 2026  
**Status:** Draft — to be reviewed by DPO / legal counsel

---

## 1. Description of Processing

PeopleMesh is a people-matching platform that processes personal data to connect users with people, jobs, communities, and events. Processing includes:

- **Profile creation and storage**: users provide professional, personal, and social information (name, contact identifier on the USER **`mesh_node`** as **`external_id`** (often email), city, skills, hobbies, interests, education). The primary persisted profile entity is **`mesh.mesh_node`**; OAuth logins are **`identity.user_identity`** rows referencing that node via **`node_id`** (multi-provider: several identities per node). Data is protected with **TLS in transit**, **infrastructure-level encryption at rest** (database / storage), and **access control via OAuth2** (OIDC).
- **Semantic embedding generation**: profile text (excluding PII like name and email; country is included for matching quality) is converted to vector embeddings used for matching. The embedding model is configurable and can be either a cloud-based AI provider (with appropriate DPA in place) or a self-hosted model (no data leaves the host).
- **Automated matching and profiling**: the system uses vector similarity search (pgvector) combined with metadata filters to suggest people, jobs, communities, and events. This constitutes profiling under GDPR Art. 22.
- **CV and OAuth provider import**: users can upload CVs (processed by Docling) or import profile data from GitHub. CV content is sent to a configured LLM for structuring into profile fields. The LLM can be either a cloud-based AI provider or a self-hosted model. OAuth imports are performed via a popup window: the backend exchanges the OAuth code, fetches provider data (for GitHub: user profile, repository languages, and repo topics via the REST API), builds an import schema, and returns it to the popup via an HTML page with `postMessage`. No import data is persisted server-side — it is held in the browser until the user confirms or discards.
- **Auto-clustering**: k-means clustering on profile embeddings to auto-discover communities. Aggregated trait data from clusters is sent to the configured LLM for naming.
- **Skill assessment and cataloging**: users self-assess proficiency levels on skills from imported catalogs. Assessment data (skill ID, level 0–5, interest flag, source) is stored in **`skills.skill_assessment`** (linked to **`mesh.mesh_node`** by **`node_id`**). Catalog metadata lives in **`skills.skill_catalog`** and **`skills.skill_definition`**. Definitions may be imported from external taxonomies (ESCO, O*NET, custom CSV) but contain no personal data.

## 2. Necessity and Proportionality

| Principle | Assessment |
|-----------|-----------|
| **Purpose limitation** | Data is processed solely for matching and community features. No advertising or data sales. |
| **Data minimisation** | Only data volunteered by the user is collected. Embedding text excludes direct identifiers (name, email). Audit logs use hashed mesh node IDs (session principal) and IPs. |
| **Storage limitation** | Inactive profiles are auto-deleted after 12 months. Account deletion is immediate (hard delete). |
| **Accuracy** | Users can update their profile at any time. Import changes are previewed before applying. |
| **Lawful basis** | Consent (Art. 6(1)(a)) for each processing purpose with granular scope management. Legitimate interest for security and fraud prevention. |

## 3. Risk Assessment

### 3.1 Risks to Data Subjects

| Risk | Likelihood | Severity | Mitigation |
|------|-----------|----------|------------|
| **Unauthorised access to personal data** | Low | High | **Encryption at rest** and **TLS in transit**; **OAuth2 (OIDC)** authentication; HMAC-signed sessions with HttpOnly/SameSite cookies; CSRF protection; security headers. |
| **Profiling without awareness** | Medium | Low | Transparent consent management with granular per-scope toggles enforced at service level (revoking a consent blocks the corresponding processing activity); Privacy Dashboard showing all active consents with consequence warnings; users can restrict processing (Art. 18) via a single centralised control; modal confirmation required before revoking any consent. |
| **Data leak to third-party sub-processors** | Low | High | Embedding text excludes PII (name, email). DPA required with any cloud AI provider. Data minimisation for each external call. When self-hosted models are configured, no external transfers occur. |
| **Data breach** | Low | High | Encryption at rest; pseudonymised audit logs; HSTS; TLS 1.3. Breach notification process documented. |
| **Incomplete erasure on account deletion** | Low | Medium | Erasure targets the USER **`mesh.mesh_node`** row; linked **`identity.user_identity`** and **`skills.skill_assessment`** rows follow **`ON DELETE CASCADE`** from that node; **`mesh.mesh_node_consent`**, other mesh nodes created by the user, and audit data are removed in the same deletion workflow where foreign keys are not cascading. |
| **Skill proficiency data used for performance evaluation** | Low | Medium | Assessment is user-controlled (SELF source); no manager override without consent. Data subject can delete assessments at any time. |
| **Public profile data exposure** | Low | Low | Public profiles only show data the user has published. Email and Slack handle are copy-to-clipboard (not hyperlinked). Only authenticated users can view public profiles. Viewing does not create persistent records. |
| **Cross-border transfer** | Medium | Medium | Primary data stored in EU. When a cloud AI provider is configured, processing may occur outside EU under standard contractual clauses. When self-hosted models are configured, no cross-border transfer occurs. |

### 3.2 Risks from Automated Decision-Making

The matching system produces ranked suggestions but does not make legally binding or similarly significant decisions. Users retain full control over whom to connect with. Matching scores are visible (score breakdown provided in the UI).

## 4. Measures to Address Risks

1. **TLS in transit and infrastructure-level encryption at rest**: HTTPS for all client and API traffic; database/storage encryption managed at infrastructure level (e.g. RDS encryption, EBS encryption).
2. **Access control**: **OAuth2 (OIDC)** multi-provider authentication; signed session cookies; CSRF custom header check; CORS allowlist.
3. **Consent management**: granular per-purpose consent (professional_matching, embedding_processing); revocable via Privacy Dashboard. Records are stored in **`mesh.mesh_node_consent`** keyed by **`node_id`** (the **`mesh_node`** primary key). CV and OAuth provider imports are user-initiated and do not require a separate consent toggle.
4. **Data subject rights**: full data export (Art. 15/20), complete erasure (Art. 17), processing restriction (Art. 18), all accessible from Privacy Dashboard.
5. **Audit trail**: pseudonymised audit logging (SHA-256 hashed mesh node ID and IP); no profile content in logs.
6. **Maintenance security**: API key + IP allowlist for maintenance endpoints; no default credentials in production.
7. **Retention**: configurable automatic deletion of inactive profiles (inactivity determined from **`identity.user_identity.last_active_at`** relative to linked USER **`mesh_node`** rows); account deletion is immediate (hard delete, no grace period).
8. **Monitoring**: (planned) AWS CloudWatch anomaly detection + SNS alerting for breach notification workflow.
9. **Skill assessment data**: deleted on account erasure (**`ON DELETE CASCADE`** from **`mesh.mesh_node`** to **`skills.skill_assessment`**).
10. **Skill catalog management**: write operations (create, import, delete catalogs) are restricted to users with the `can_manage_skills` entitlement, enforced at API level. Read access is available to all authenticated users. Catalog definitions contain no personal data.

## 5. Consultation

This DPIA should be reviewed by:
- [ ] Data Protection Officer (DPO)
- [ ] Legal counsel
- [ ] Security team

If residual high risks remain after mitigation, consult the supervisory authority (GDPR Art. 36).

## 6. Review Schedule

This document should be reviewed:
- When new data categories or processing purposes are added
- When new sub-processors are introduced
- When the matching algorithm changes significantly
- At minimum annually
