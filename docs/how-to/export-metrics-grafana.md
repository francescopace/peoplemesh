# How to Export Metrics to Grafana and OTLP Backends

This guide shows how to export PeopleMesh Micrometer metrics to a monitoring stack.

Primary path:

- Quarkus Micrometer -> Prometheus (scrape `/q/metrics`) -> Grafana

Alternative path:

- Quarkus Micrometer -> OTLP collector (for Datadog, New Relic, Grafana Cloud, and similar platforms)

## Why this matters

Observability on latency and throughput helps detect regressions in embedding, LLM, and search performance before they affect users.

## Audience

- Platform operators and SRE
- Developers validating runtime behavior
- Observability engineers building dashboards and alerts

## Prerequisites

- Running PeopleMesh instance
- Network access to `/q/metrics` from your monitoring stack
- Prometheus + Grafana (or an OTLP-capable observability backend)

## Procedure

### What PeopleMesh exposes

PeopleMesh publishes application timers for AI and vector search:

- `peoplemesh_llm_inference_seconds`
- `peoplemesh_embedding_inference_seconds`
- `peoplemesh_hnsw_search_seconds`

In the admin page, these are summarized and shown through `GET /api/v1/system/statistics` as:

- `sampleCount`
- `avgMs`
- `p95Ms`
- `maxMs`

### Option A: Prometheus + Grafana

### 1) Add Prometheus registry dependency

If not already present, add the Quarkus extension:

```bash
mvn quarkus:add-extension -Dextensions="micrometer-registry-prometheus"
```

Equivalent Maven dependency:

```xml
<dependency>
  <groupId>io.quarkus</groupId>
  <artifactId>quarkus-micrometer-registry-prometheus</artifactId>
</dependency>
```

### 2) Enable Prometheus export

Set in configuration (for example `application.properties` or env-specific profile):

```properties
quarkus.micrometer.enabled=true
quarkus.micrometer.export.prometheus.enabled=true
```

### 3) Verify endpoint

Check locally:

```bash
curl -s http://localhost:8080/q/metrics | rg "peoplemesh_(llm_inference|embedding_inference|hnsw_search)_seconds"
```

You should see `_count`, `_sum`, and bucket/time-series lines.

### 4) Configure Prometheus scrape

Example `prometheus.yml` job:

```yaml
scrape_configs:
  - job_name: "peoplemesh"
    metrics_path: /q/metrics
    static_configs:
      - targets: ["peoplemesh:8080"]
```

### 5) Build Grafana panels

Example PromQL queries:

- Average latency (5m):
  - `rate(peoplemesh_llm_inference_seconds_sum[5m]) / rate(peoplemesh_llm_inference_seconds_count[5m])`
  - `rate(peoplemesh_embedding_inference_seconds_sum[5m]) / rate(peoplemesh_embedding_inference_seconds_count[5m])`
  - `rate(peoplemesh_hnsw_search_seconds_sum[5m]) / rate(peoplemesh_hnsw_search_seconds_count[5m])`
- P95 latency (histogram buckets required):
  - `histogram_quantile(0.95, sum by (le) (rate(peoplemesh_llm_inference_seconds_bucket[5m])))`
  - `histogram_quantile(0.95, sum by (le) (rate(peoplemesh_embedding_inference_seconds_bucket[5m])))`
  - `histogram_quantile(0.95, sum by (le) (rate(peoplemesh_hnsw_search_seconds_bucket[5m])))`
- Throughput:
  - `rate(peoplemesh_llm_inference_seconds_count[5m])`
  - `rate(peoplemesh_embedding_inference_seconds_count[5m])`
  - `rate(peoplemesh_hnsw_search_seconds_count[5m])`

### Option B: OTLP export (other observability backends)

If your platform ingests OTLP metrics (via OpenTelemetry Collector or managed endpoint):

1. Add the relevant Quarkus Micrometer OTLP registry extension.
2. Enable OTLP export keys (`quarkus.micrometer.export.otlp.*`) for endpoint and credentials.
3. Route to your collector/backend and build equivalent dashboards there.

Refer to your backend vendor docs for exact OTLP endpoint/auth settings.

### Security and access notes

- Treat `/q/metrics` as an operational endpoint:
  - expose only on internal networks, or
  - protect with reverse proxy/authn controls.
- Keep maintenance and admin APIs private; do not expose them publicly.

## Verification

- `curl -s http://localhost:8080/q/metrics` returns Micrometer output.
- Prometheus target for PeopleMesh is `UP`.
- Dashboard queries return non-empty data for count/latency after traffic is generated.

## Troubleshooting

- No `peoplemesh_*` metrics visible:
  - ensure traffic is hitting features that invoke LLM, embedding, and HNSW search paths;
  - verify Micrometer is enabled and app restarted after dependency/config changes.
- P95 query returns no data:
  - verify histogram/buckets are being exported;
  - ensure enough samples exist in the selected time window.
