# Deploy on OpenShift with Helm

Deploy PeopleMesh on OpenShift using the Helm chart maintained in this repository.

## Why this matters

Deploying via Helm on OpenShift improves repeatability, makes environment changes explicit, and helps enforce consistent security controls for credentials and network exposure.

Relevant references:

- [GDPR Art. 25](https://eur-lex.europa.eu/eli/reg/2016/679/oj): data protection by design and by default.
- [GDPR Art. 32](https://eur-lex.europa.eu/eli/reg/2016/679/oj): confidentiality and integrity controls.

## Audience

- Platform administrators
- Operators and SRE
- Security and IAM maintainers

## Prerequisites

- OpenShift access (`oc login`) with permissions to create resources in the target namespace
- Helm 3 installed
- At least one configured OIDC login provider (Google or Microsoft)
- Optional: GitHub OIDC client credentials (`OIDC_GITHUB_CLIENT_ID` / `OIDC_GITHUB_CLIENT_SECRET`) only if you want to enable profile import from GitHub
- If both `ollama` and `docling` run on GPU, plan at least 2 GPU units in the namespace quota (each pod requests `nvidia.com/gpu: 1`)

## Procedure

### 1) Review chart files

Chart path:

- `tools/helm`

Main files:

- `Chart.yaml`
- `values.yaml`
- `templates/deployment.yaml`
- `templates/service.yaml`
- `templates/route.yaml`
- `templates/configmap.yaml`
- `templates/secret.yaml`
- `templates/postgresql-service.yaml`
- `templates/postgresql-statefulset.yaml`
- `templates/docling-service.yaml`
- `templates/docling-deployment.yaml`
- `templates/ollama-service.yaml`
- `templates/ollama-statefulset.yaml`

```bash
cd tools/helm
```

### 2) Create target namespace

```bash
oc new-project peoplemesh
```

If the namespace already exists:

```bash
oc project peoplemesh
```

### 3) Prepare minimal OpenShift values

Create a local override file (example: `values-openshift.yaml`):

```yaml
image:
  tag: main # or "latest" or specifig tag

postgresql:
  enabled: true

docling:
  enabled: true

ollama:
  enabled: true

config:
  data:
    CORS_ORIGINS: https://peoplemesh.apps.example.com

secret:
  stringData:
    OIDC_GOOGLE_CLIENT_ID: replace-me
    OIDC_GOOGLE_CLIENT_SECRET: replace-me
    # Optional, required only for GitHub profile import:
    # OIDC_GITHUB_CLIENT_ID: replace-me
    # OIDC_GITHUB_CLIENT_SECRET: replace-me

route:
  host: peoplemesh.apps.example.com
```

GPU quota note:

- `ollama` and `docling` each request one GPU by default, so a full GPU setup requires at least 2 available GPU units.
- If your namespace has only 1 GPU unit, keep GPU for `ollama` and run `docling` on CPU with this override:

```yaml
docling:
  enabled: true
  image:
    repository: ghcr.io/docling-project/docling-serve-cpu
    tag: latest
  tolerations: []
```

### 4) (Optional) Use custom TLS certificate on the Route

If your hostname is outside the cluster wildcard domain (for example `redhat.peoplemesh.org`), modify route as follow:

```yaml
route:
  host: redhat.peoplemesh.org
  tls:
    termination: edge
    insecureEdgeTerminationPolicy: Redirect
    certificate: |
      -----BEGIN CERTIFICATE-----
      ...
      -----END CERTIFICATE-----
    key: |
      -----BEGIN PRIVATE KEY-----
      ...
      -----END PRIVATE KEY-----
    caCertificate: |
      -----BEGIN CERTIFICATE-----
      ...
      -----END CERTIFICATE-----
```

### 5) Install or upgrade release

```bash
helm upgrade --install peoplemesh . -f values-openshift.yaml
```

## Verification

Check rollout:

```bash
oc get pods -n peoplemesh
oc rollout status deploy/peoplemesh -n peoplemesh
```

Check route:

```bash
oc get route -n peoplemesh
```

Check health and auth providers:

```bash
curl -f https://<route-host>/q/health
curl -f https://<route-host>/api/v1/auth/providers
```

## Models donwload 

For Ollama, pull required models after first startup:

```bash
oc exec -n peoplemesh statefulset/peoplemesh-ollama -- ollama pull granite4:3b
oc exec -n peoplemesh statefulset/peoplemesh-ollama -- ollama pull granite-embedding:30m
```

## Troubleshooting

- Pod crash on startup: verify `DB_URL`, `DB_USER`, `DB_PASSWORD` and PostgreSQL reachability
- 401/403 on maintenance endpoints: verify `MAINTENANCE_API_KEY` and caller IP allowlist (`MAINTENANCE_ALLOWED_CIDRS`) if configured
- OIDC providers missing: verify at least one provider pair is set (`OIDC_GOOGLE_*` or `OIDC_MICROSOFT_*`)
- CV import errors: verify `DOCLING_BASE_URL` connectivity from the PeopleMesh pod
- AI/matching errors: verify `OLLAMA_BASE_URL` connectivity and model availability

## Related docs

- Production baseline: [`../getting-started/production-baseline.md`](../getting-started/production-baseline.md)
- Configuration reference: [`../reference/configuration.md`](../reference/configuration.md)
- OIDC configuration: [`configure-oidc.md`](configure-oidc.md)
