# PeopleMesh Product Roadmap

PeopleMesh is an open-source, AI-powered discovery layer for organizations.
It replaces fragmented tools (spreadsheets, chat threads, siloed directories) with one semantic search surface across people, jobs, communities, projects, and events.

---

## Open-Source Commitment

PeopleMesh is and will remain open source under the Apache 2.0 license.

- **Open core, always.** Search, matching, profiles, and integration surfaces are shipped in the OSS project.
- **API-first by design.** Core capabilities are available through REST and MCP, not only through the web UI.
- **Integration freedom.** Organizations can build their own connectors and automations without forking PeopleMesh.
- **Community-driven evolution.** Product direction is shaped by real usage, contributors, and integration needs.

---

## Principles

1. **Matching quality above all.** Every release should improve relevance and trust in results.
2. **Privacy and security by default.** Opt-in processing, transparent controls, GDPR-aligned behavior.
3. **Integration parity.** If a workflow exists in the product, it should be available through API surfaces.
4. **Operational simplicity.** Easy to run locally, in cloud, and on-premise without hidden complexity.
5. **Profile quality over profile volume.** Better structured data beats larger low-signal datasets.

---

## Current Baseline (Shipped)

The core platform is already usable and production-ready for single-organization deployments.

### Search and Profiles
- Natural-language search with hybrid ranking (semantic + metadata signals).
- Unified mesh model across people, jobs, communities, projects, events, and groups.
- Manual profile editing, CV import, and OAuth-based profile enrichment.
- Public read-only profile pages and global skills dictionary normalization.

### APIs and Integrations
- REST API (`/api/v1`) for auth, profile, matching, nodes, skills, system, and maintenance flows.
- MCP endpoint with read tools already available (`peoplemesh_get_my_profile`, `peoplemesh_match`, `peoplemesh_match_me`, `peoplemesh_match_node`).
- Maintenance ingest APIs for users and nodes, with idempotent external ingestion patterns.

### Trust, Security, and Compliance
- GDPR rights flows (export, deletion, consent management, processing controls).
- OAuth login providers and entitlement-based admin controls.
- Security hardening (headers, CSRF controls, CORS controls, maintenance key and CIDR restrictions).
- Pseudonymized audit trail with no profile content in logs.

### Deployment and Operations
- Local quickstart and dev profile ready.
- Docker Compose stack for full dependency startup.
- Helm chart for OpenShift/Kubernetes deployments.
- Health and metrics endpoints available out of the box.

---

## Phase 1 (M0-M3): Validate Adoption

**Goal:** Prove daily product value with measurable adoption and relevance improvements.

- [ ] **Product funnel analytics** - Track search -> result interaction -> contact outcomes.
- [ ] **Match feedback loop** - Collect explicit good/bad match signals and close the tuning loop.
- [ ] **Relevance iteration cycle** - Improve synonym handling, must-have behavior, and ranking explainability.
- [ ] **Outbound integration events** - Emit webhook/event notifications for external workflows.
- [ ] **MCP reliability baseline** - Improve MCP observability, error quality, and developer examples on existing tools.
- [ ] **Adoption-ready demo flow** - Harden demo setup and repeatable data-driven product walkthroughs.

---

## Phase 2 (M3-M6): Build Integration Platform

**Goal:** Make PeopleMesh the easiest internal discovery platform to integrate.

- [ ] **API contract stability** - Define compatibility and deprecation policy for REST and MCP contracts.
- [ ] **Core Integration Kit (engine-agnostic)** - Provide canonical API contracts, mapping guides, retries/checkpoint patterns, and conformance tests.
- [ ] **Preassembled Agent Packs** - Publish ready-to-run integration agents for selected engines (for example OpenClaw), including full-sync and delta-sync workflows.
- [ ] **Reference connectors (OSS)** - Publish first open-source connectors and agent-pack examples as implementation references.
- [ ] **Embeddable search** - Add widget/embed options for intranet and internal portal use cases.
- [ ] **MCP capability maturity** - Expand MCP read capabilities with stronger parity (filters, pagination, docs, examples).
- [ ] **Access control v2** - Move beyond a single admin entitlement to a richer role model.

---

## Phase 3 (M6-M9): Enterprise Scale

**Goal:** Support complex organizations at scale with clear governance.

- [ ] **Full RBAC** - Introduce role-based access control with delegated administration.
- [ ] **Multi-organization support** - Single deployment, isolated data and administration per organization (holding/group model).
- [ ] **Business adoption dashboards** - Expose time-to-find, quality trends, and adoption KPIs for operators.
- [ ] **Matching engine v2** - Domain-aware ranking improvements with stronger explainability.
- [ ] **MCP governance controls** - Add policy controls and enterprise-grade usage oversight for MCP access.
- [ ] **Agent-pack certification** - Add compatibility tests and quality gates for community and partner agent packs.
- [ ] **Organization configuration UI** - Persist organization/legal settings through managed admin workflows.

---

## Phase 4 (M9-M12+): Ecosystem Leadership

**Goal:** Build the default ecosystem for internal people discovery integrations.

- [ ] **Connector and Agent-Pack Registry** - Curated catalog of community and partner connectors and preassembled agent packs.
- [ ] **Profile portability** - Controlled export/import across organizations with explicit consent and auditability.
- [ ] **Governance and compliance packs** - Policy templates and reporting bundles for regulated environments.
- [ ] **MCP ecosystem growth** - Community contribution model for MCP integration packs and examples.

---

## Integration Strategy (REST + MCP)

PeopleMesh is designed to connect existing systems, not replace them.

REST and MCP are both first-class extension surfaces:
- REST is the operational backbone for application and ingest workflows.
- MCP is the AI-assistant integration surface for discovery and matching scenarios.

On top of these surfaces, PeopleMesh will provide a two-layer integration model:
- **Core Integration Kit** for engine-agnostic standards and compatibility.
- **Preassembled Agent Packs** for fast adoption on selected automation and agent engines.

MCP is already live for read workflows today. Future MCP expansion will keep the same principles: explicit permissions, privacy by default, and full auditability.
