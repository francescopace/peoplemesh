#!/usr/bin/env python3
"""
Evaluate search quality for synthetic PeopleMesh data.

This script measures:
1) Direct embedding retrieval quality over stored vectors.
2) Optional API end-to-end retrieval quality.
3) Divergence between Python seed embedding text and Java embedding text format.
"""

from __future__ import annotations

import argparse
import base64
import hashlib
import hmac
import json
import math
import os
import random
import re
import subprocess
import sys
import time
import uuid
from dataclasses import dataclass
from typing import Any, Callable
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode
from urllib.request import Request, urlopen

try:
    import psycopg2  # type: ignore[import-not-found]
except Exception as exc:  # noqa: BLE001
    print(
        "[error] psycopg2 is required. Install dependencies with "
        "`python -m pip install -r tools/requirements.txt`: "
        f"{exc}",
        file=sys.stderr,
    )
    raise SystemExit(2)


DEFAULT_OLLAMA_MODEL = "granite-embedding:30m"
DEFAULT_OLLAMA_URL = "http://localhost:11434"
DEFAULT_API_URL = "http://localhost:8080"
DEFAULT_SESSION_SECRET = "default-dev-session-secret-change-in-prod-32b!!"
CONSENT_SCOPE = "professional_matching"
DEFAULT_QUERY_SUITE = "default"


@dataclass
class Candidate:
    node_id: str
    node_type: str
    title: str
    description: str
    tags: list[str]
    country: str | None
    structured_data: dict[str, Any]
    embedding: list[float]


@dataclass
class QueryCase:
    key: str
    text: str
    target_types: set[str]
    relevance_fn: Callable[[Candidate], bool]


@dataclass
class QueryMetrics:
    precision_at_5: float
    precision_at_10: float
    precision_at_20: float
    recall_at_20: float
    recall_at_20_capped: float
    mrr: float
    ndcg_at_10: float
    relevant_total: int
    relevant_found_top20: int
    top3_titles: list[str]
    rel_mean_sim: float | None
    rel_median_sim: float | None
    rel_std_sim: float | None
    irr_mean_sim: float | None
    irr_median_sim: float | None
    irr_std_sim: float | None


@dataclass
class AggregateMetrics:
    precision_at_5: float
    precision_at_10: float
    precision_at_20: float
    recall_at_20: float
    recall_at_20_capped: float
    mrr: float
    ndcg_at_10: float


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Evaluate embedding and search quality on PeopleMesh synthetic data."
    )
    parser.add_argument(
        "--db-url",
        default=os.getenv("DATABASE_URL", ""),
        help="PostgreSQL URL. If omitted, auto-discovery from Docker is attempted.",
    )
    parser.add_argument(
        "--ollama-url",
        default=DEFAULT_OLLAMA_URL,
        help=f"Ollama base URL (default: {DEFAULT_OLLAMA_URL})",
    )
    parser.add_argument(
        "--ollama-model",
        default=DEFAULT_OLLAMA_MODEL,
        help=f"Ollama embedding model (default: {DEFAULT_OLLAMA_MODEL})",
    )
    parser.add_argument(
        "--api-url",
        default=DEFAULT_API_URL,
        help=f"Base API URL (default: {DEFAULT_API_URL})",
    )
    parser.add_argument(
        "--session-secret",
        default=os.getenv("PEOPLEMESH_SESSION_SECRET", DEFAULT_SESSION_SECRET),
        help="Session signing secret used for pm_session cookie.",
    )
    parser.add_argument(
        "--sample-divergence",
        type=int,
        default=10,
        help="Number of profiles sampled for text divergence check (default: 10).",
    )
    parser.add_argument(
        "--seed",
        type=int,
        default=42,
        help="Random seed for deterministic sampling.",
    )
    parser.add_argument(
        "--api-sample-size",
        type=int,
        default=12,
        help="Number of USER nodes sampled for /matches API evaluation (default: 12).",
    )
    parser.add_argument(
        "--query-prefix",
        default="",
        help="Optional prefix prepended to direct-eval query text before embedding.",
    )
    parser.add_argument(
        "--query-suite",
        choices=["default", "it-only", "it-strict"],
        default=DEFAULT_QUERY_SUITE,
        help="Query suite to evaluate (default: all queries, it-only: IT/domain-focused, it-strict: core IT role/skill queries).",
    )
    parser.add_argument(
        "--divergence-mode",
        choices=["legacy-vs-java", "aligned-vs-java"],
        default="legacy-vs-java",
        help=(
            "How to compare embedding text variants in divergence report "
            "(default: legacy-vs-java)."
        ),
    )
    parser.add_argument(
        "--eval-my-mesh",
        action="store_true",
        help="Enable /api/v1/matches/me evaluation block.",
    )
    parser.add_argument(
        "--my-mesh-sample-size",
        type=int,
        default=12,
        help="Number of USER nodes sampled for /matches/me evaluation (default: 12).",
    )
    parser.add_argument(
        "--my-mesh-relevance-mode",
        choices=["embedding", "strict", "both"],
        default="both",
        help="Relevance mode for /matches/me evaluation (default: both).",
    )
    return parser.parse_args()


def post_json(url: str, payload: dict[str, Any], timeout_seconds: int = 120) -> dict[str, Any]:
    body = json.dumps(payload).encode("utf-8")
    req = Request(url, data=body, method="POST")
    req.add_header("Content-Type", "application/json")
    req.add_header("Accept", "application/json")
    with urlopen(req, timeout=timeout_seconds) as resp:
        return json.loads(resp.read().decode("utf-8"))


def post_json_with_cookie(
    url: str, payload: dict[str, Any], cookie: str, timeout_seconds: int = 120
) -> dict[str, Any]:
    body = json.dumps(payload).encode("utf-8")
    req = Request(url, data=body, method="POST")
    req.add_header("Content-Type", "application/json")
    req.add_header("Accept", "application/json")
    req.add_header("Cookie", cookie)
    req.add_header("X-Requested-With", "XMLHttpRequest")
    with urlopen(req, timeout=timeout_seconds) as resp:
        return json.loads(resp.read().decode("utf-8"))


def get_json_with_cookie(url: str, cookie: str, timeout_seconds: int = 120) -> Any:
    req = Request(url, method="GET")
    req.add_header("Accept", "application/json")
    req.add_header("Cookie", cookie)
    req.add_header("X-Requested-With", "XMLHttpRequest")
    with urlopen(req, timeout=timeout_seconds) as resp:
        return json.loads(resp.read().decode("utf-8"))


def discover_db_url() -> str:
    """
    Try to discover DevServices pgvector connection from Docker.
    Fallback host/port guess if discovery fails.
    """
    try:
        out = subprocess.check_output(
            ["docker", "ps", "--format", "{{.ID}}\t{{.Image}}\t{{.Ports}}"],
            text=True,
            stderr=subprocess.STDOUT,
        )
    except Exception:
        return "postgresql://postgres:postgres@localhost:5432/postgres"

    for line in out.splitlines():
        parts = line.split("\t", 2)
        if len(parts) < 3:
            continue
        container_id, image, ports = parts[0], parts[1], parts[2]
        if "pgvector/pgvector" not in image:
            continue
        m = re.search(r"0\.0\.0\.0:(\d+)->5432/tcp", ports)
        if not m:
            m = re.search(r"\[::\]:(\d+)->5432/tcp", ports)
        if m:
            port = m.group(1)
            user = "postgres"
            password = "postgres"
            database = "postgres"
            try:
                env_out = subprocess.check_output(
                    [
                        "docker",
                        "inspect",
                        container_id,
                        "--format",
                        "{{range .Config.Env}}{{println .}}{{end}}",
                    ],
                    text=True,
                    stderr=subprocess.STDOUT,
                )
                for env_line in env_out.splitlines():
                    if env_line.startswith("POSTGRES_USER="):
                        user = env_line.split("=", 1)[1] or user
                    elif env_line.startswith("POSTGRES_PASSWORD="):
                        password = env_line.split("=", 1)[1] or password
                    elif env_line.startswith("POSTGRES_DB="):
                        database = env_line.split("=", 1)[1] or database
            except Exception:
                # keep defaults if inspection fails
                pass
            return f"postgresql://{user}:{password}@localhost:{port}/{database}"

    return "postgresql://postgres:postgres@localhost:5432/postgres"


def normalize_list(values: list[str] | None) -> list[str]:
    if not values:
        return []
    out: list[str] = []
    for val in values:
        if val is None:
            continue
        s = str(val).strip()
        if s:
            out.append(s)
    return out


def parse_embedding(value: Any) -> list[float]:
    if value is None:
        return []
    if isinstance(value, list):
        return [float(x) for x in value]
    if isinstance(value, tuple):
        return [float(x) for x in value]
    if isinstance(value, str):
        text = value.strip()
        if text.startswith("[") and text.endswith("]"):
            inner = text[1:-1].strip()
            if not inner:
                return []
            return [float(x.strip()) for x in inner.split(",")]
        return []
    return []


def connect_db(db_url: str):
    return psycopg2.connect(db_url)


def fetch_candidates(conn) -> list[Candidate]:
    with conn.cursor() as cur:
        cur.execute(
            """
            SELECT
                n.id::text,
                n.node_type,
                COALESCE(n.title, ''),
                COALESCE(n.description, ''),
                COALESCE(n.tags, ARRAY[]::text[]),
                n.country,
                COALESCE(n.structured_data, '{}'::jsonb)::text,
                n.embedding::text
            FROM mesh.mesh_node n
            WHERE n.searchable = true
              AND n.embedding IS NOT NULL
            """
        )
        rows = cur.fetchall()

    out: list[Candidate] = []
    for row in rows:
        try:
            sd = json.loads(row[6]) if row[6] else {}
        except Exception:
            sd = {}
        emb = parse_embedding(row[7])
        if not emb:
            continue
        out.append(
            Candidate(
                node_id=row[0],
                node_type=row[1],
                title=row[2],
                description=row[3],
                tags=normalize_list(row[4]),
                country=row[5],
                structured_data=sd if isinstance(sd, dict) else {},
                embedding=emb,
            )
        )
    return out


def embed_text(ollama_url: str, model: str, text: str) -> list[float]:
    payload = {"model": model, "prompt": text}
    resp = post_json(ollama_url.rstrip("/") + "/api/embeddings", payload)
    emb = resp.get("embedding")
    if not isinstance(emb, list) or not emb:
        return []
    return [float(x) for x in emb]


def apply_query_prefix(text: str, query_prefix: str) -> str:
    if not query_prefix:
        return text
    return f"{query_prefix}{text}"


def dot(a: list[float], b: list[float]) -> float:
    return sum(x * y for x, y in zip(a, b))


def norm(a: list[float]) -> float:
    return math.sqrt(sum(x * x for x in a))


def cosine(a: list[float], b: list[float]) -> float:
    na = norm(a)
    nb = norm(b)
    if na == 0.0 or nb == 0.0:
        return 0.0
    return dot(a, b) / (na * nb)


def lower_set(items: list[str]) -> set[str]:
    return {x.strip().lower() for x in items if x and str(x).strip()}


def dedupe_terms(items: list[str]) -> list[str]:
    out: list[str] = []
    seen: set[str] = set()
    for item in items:
        val = str(item).strip()
        if not val:
            continue
        key = val.lower()
        if key in seen:
            continue
        seen.add(key)
        out.append(val)
    return out


def sd_list(c: Candidate, key: str) -> list[str]:
    val = c.structured_data.get(key)
    if not isinstance(val, list):
        return []
    return normalize_list(val)


def has_any_term(text: str, terms: list[str]) -> bool:
    t = text.lower()
    return any(term.lower() in t for term in terms)


def matches_skill_any(c: Candidate, terms: list[str]) -> bool:
    pool = lower_set(c.tags + sd_list(c, "tools_and_tech") + sd_list(c, "skills_soft"))
    for term in terms:
        lt = term.lower()
        if any(lt in p for p in pool):
            return True
    return False


def matches_skill_all(c: Candidate, terms: list[str]) -> bool:
    pool = lower_set(c.tags + sd_list(c, "tools_and_tech") + sd_list(c, "skills_soft"))
    for term in terms:
        lt = term.lower()
        if not any(lt in p for p in pool):
            return False
    return True


def matches_language(c: Candidate, terms: list[str]) -> bool:
    langs = lower_set(sd_list(c, "languages_spoken"))
    for term in terms:
        lt = term.lower()
        if any(lt in l for l in langs):
            return True
    return False


def split_roles(description: str) -> list[str]:
    if not description:
        return []
    return [part.strip().lower() for part in description.split(",") if part.strip()]


def strict_profile_overlap_relevant(seed: Candidate, cand: Candidate) -> bool:
    if cand.node_type != "USER" or cand.node_id == seed.node_id:
        return False

    seed_skills = lower_set(seed.tags + sd_list(seed, "tools_and_tech") + sd_list(seed, "skills_soft"))
    cand_skills = lower_set(cand.tags + sd_list(cand, "tools_and_tech") + sd_list(cand, "skills_soft"))
    skill_overlap = len(seed_skills.intersection(cand_skills))

    seed_langs = lower_set(sd_list(seed, "languages_spoken"))
    cand_langs = lower_set(sd_list(cand, "languages_spoken"))
    language_ok = True if not seed_langs else bool(seed_langs.intersection(cand_langs))

    seed_roles = split_roles(seed.description)
    cand_role_text = (cand.description or "").lower()
    role_ok = True
    if seed_roles:
        role_ok = any(role in cand_role_text for role in seed_roles)

    # Strict benchmark: require language compatibility (when specified) and clear skill overlap.
    if skill_overlap >= 2 and language_ok:
        return True
    if skill_overlap >= 1 and language_ok and role_ok:
        return True
    return False


def build_relevant_ids(seed: Candidate, candidates: list[Candidate], relevance_mode: str) -> set[str]:
    if relevance_mode == "strict":
        return {c.node_id for c in candidates if strict_profile_overlap_relevant(seed, c)}

    neighbors: list[tuple[str, float]] = []
    for cand in candidates:
        if cand.node_id == seed.node_id:
            continue
        neighbors.append((cand.node_id, cosine(seed.embedding, cand.embedding)))
    neighbors.sort(key=lambda x: x[1], reverse=True)
    return {node_id for node_id, _ in neighbors[:20]}


def build_query_cases() -> list[QueryCase]:
    return [
        QueryCase(
            key="python_developer",
            text="Python developer",
            target_types={"USER"},
            relevance_fn=lambda c: c.node_type == "USER" and matches_skill_any(c, ["python"]),
        ),
        QueryCase(
            key="react_frontend",
            text="React frontend developer",
            target_types={"USER"},
            relevance_fn=lambda c: c.node_type == "USER"
            and matches_skill_any(c, ["react", "javascript", "typescript"]),
        ),
        QueryCase(
            key="data_scientist",
            text="data scientist",
            target_types={"USER"},
            relevance_fn=lambda c: c.node_type == "USER"
            and (
                has_any_term(c.description, ["data scientist", "ml", "machine learning", "ai"])
                or matches_skill_any(c, ["python", "pandas", "numpy", "tensorflow", "pytorch"])
            ),
        ),
        QueryCase(
            key="rest_api_backend",
            text="someone who can build REST APIs",
            target_types={"USER"},
            relevance_fn=lambda c: c.node_type == "USER"
            and matches_skill_any(c, ["spring", "django", "express", "fastapi", "flask", "node.js"]),
        ),
        QueryCase(
            key="java_kubernetes",
            text="Java and Kubernetes experience",
            target_types={"USER"},
            relevance_fn=lambda c: c.node_type == "USER" and matches_skill_all(c, ["java", "kubernetes"]),
        ),
        QueryCase(
            key="italian_speaker",
            text="developer who speaks Italian",
            target_types={"USER"},
            relevance_fn=lambda c: c.node_type == "USER" and matches_language(c, ["italian"]),
        ),
        QueryCase(
            key="devops_job",
            text="DevOps position",
            target_types={"JOB"},
            relevance_fn=lambda c: c.node_type == "JOB"
            and (
                matches_skill_any(c, ["devops", "kubernetes", "docker", "terraform", "ci/cd"])
                or has_any_term(c.title + " " + c.description, ["devops", "site reliability", "platform"])
            ),
        ),
        QueryCase(
            key="chef_negative",
            text="professional chef",
            target_types={"USER", "JOB", "COMMUNITY", "EVENT", "PROJECT", "INTEREST_GROUP"},
            relevance_fn=lambda c: False,
        ),
        QueryCase(
            key="golang_backend",
            text="golang backend engineer",
            target_types={"USER"},
            relevance_fn=lambda c: c.node_type == "USER" and matches_skill_any(c, ["go", "golang"]),
        ),
        QueryCase(
            key="cloud_aws",
            text="AWS cloud engineer",
            target_types={"USER", "JOB"},
            relevance_fn=lambda c: matches_skill_any(c, ["aws", "amazon web services", "cloud"]),
        ),
        QueryCase(
            key="community_python",
            text="python community event",
            target_types={"COMMUNITY", "EVENT"},
            relevance_fn=lambda c: c.node_type in {"COMMUNITY", "EVENT"}
            and (
                matches_skill_any(c, ["python"])
                or has_any_term(c.title + " " + c.description, ["python", "developer", "coding"])
            ),
        ),
    ]


def select_query_suite(all_cases: list[QueryCase], suite: str) -> list[QueryCase]:
    if suite == "default":
        return all_cases
    if suite == "it-only":
        excluded = {"chef_negative", "community_python"}
        return [case for case in all_cases if case.key not in excluded]
    if suite == "it-strict":
        included = {
            "python_developer",
            "react_frontend",
            "data_scientist",
            "rest_api_backend",
            "java_kubernetes",
            "devops_job",
            "golang_backend",
            "cloud_aws",
        }
        return [case for case in all_cases if case.key in included]
    raise ValueError(f"Unsupported query suite: {suite}")


def precision_at_k(ranked_relevance: list[int], k: int) -> float:
    if k <= 0:
        return 0.0
    top = ranked_relevance[:k]
    if not top:
        return 0.0
    return sum(top) / len(top)


def recall_at_k(ranked_relevance: list[int], total_relevant: int, k: int) -> float:
    if total_relevant <= 0:
        return 0.0
    top = ranked_relevance[:k]
    return sum(top) / total_relevant


def recall_at_k_capped(ranked_relevance: list[int], total_relevant: int, k: int) -> float:
    if total_relevant <= 0 or k <= 0:
        return 0.0
    top = ranked_relevance[:k]
    denominator = min(total_relevant, k)
    if denominator <= 0:
        return 0.0
    return sum(top) / denominator


def reciprocal_rank(ranked_relevance: list[int]) -> float:
    for idx, rel in enumerate(ranked_relevance, start=1):
        if rel:
            return 1.0 / idx
    return 0.0


def dcg_at_k(ranked_relevance: list[int], k: int) -> float:
    score = 0.0
    for i, rel in enumerate(ranked_relevance[:k], start=1):
        if rel:
            score += 1.0 / math.log2(i + 1)
    return score


def ndcg_at_k(ranked_relevance: list[int], total_relevant: int, k: int) -> float:
    if total_relevant <= 0:
        return 0.0
    ideal = [1] * min(total_relevant, k)
    idcg = dcg_at_k(ideal, k)
    if idcg == 0:
        return 0.0
    return dcg_at_k(ranked_relevance, k) / idcg


def mean(values: list[float]) -> float | None:
    if not values:
        return None
    return sum(values) / len(values)


def median(values: list[float]) -> float | None:
    if not values:
        return None
    arr = sorted(values)
    n = len(arr)
    mid = n // 2
    if n % 2:
        return arr[mid]
    return (arr[mid - 1] + arr[mid]) / 2


def stddev(values: list[float]) -> float | None:
    if not values:
        return None
    m = mean(values)
    if m is None:
        return None
    return math.sqrt(sum((x - m) ** 2 for x in values) / len(values))


def evaluate_direct(
    candidates: list[Candidate],
    cases: list[QueryCase],
    ollama_url: str,
    model: str,
    query_prefix: str,
) -> dict[str, QueryMetrics]:
    results: dict[str, QueryMetrics] = {}
    for case in cases:
        filtered = [c for c in candidates if c.node_type in case.target_types]
        if not filtered:
            results[case.key] = QueryMetrics(
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                [],
                None,
                None,
                None,
                None,
                None,
                None,
            )
            continue

        query_text = apply_query_prefix(case.text, query_prefix)
        q_emb = embed_text(ollama_url, model, query_text)
        scored: list[tuple[Candidate, float]] = []
        for c in filtered:
            sim = cosine(q_emb, c.embedding)
            scored.append((c, sim))
        scored.sort(key=lambda x: x[1], reverse=True)

        ranked_relevance = [1 if case.relevance_fn(c) else 0 for c, _ in scored]
        total_relevant = sum(ranked_relevance)
        top20 = ranked_relevance[:20]
        rel_sims = [sim for (c, sim) in scored if case.relevance_fn(c)]
        irr_sims = [sim for (c, sim) in scored if not case.relevance_fn(c)]

        metrics = QueryMetrics(
            precision_at_5=precision_at_k(ranked_relevance, 5),
            precision_at_10=precision_at_k(ranked_relevance, 10),
            precision_at_20=precision_at_k(ranked_relevance, 20),
            recall_at_20=recall_at_k(ranked_relevance, total_relevant, 20),
            recall_at_20_capped=recall_at_k_capped(ranked_relevance, total_relevant, 20),
            mrr=reciprocal_rank(ranked_relevance),
            ndcg_at_10=ndcg_at_k(ranked_relevance, total_relevant, 10),
            relevant_total=total_relevant,
            relevant_found_top20=sum(top20),
            top3_titles=[c.title for c, _ in scored[:3]],
            rel_mean_sim=mean(rel_sims),
            rel_median_sim=median(rel_sims),
            rel_std_sim=stddev(rel_sims),
            irr_mean_sim=mean(irr_sims),
            irr_median_sim=median(irr_sims),
            irr_std_sim=stddev(irr_sims),
        )
        results[case.key] = metrics
    return results


def hmac_hex(data: str, secret: str) -> str:
    digest = hmac.new(secret.encode("utf-8"), data.encode("utf-8"), hashlib.sha256).hexdigest()
    return digest


def encode_pm_session(user_id: str, provider: str, secret: str) -> str:
    exp = int(time.time()) + 60 * 60 * 24 * 7
    raw = f"{user_id}|{provider}|{exp}"
    payload = base64.urlsafe_b64encode(raw.encode("utf-8")).decode("ascii").rstrip("=")
    signature = hmac_hex(payload, secret)
    return f"{payload}.{signature}"


def ensure_api_user_and_consent(
    conn,
    user_id: str,
    identity_id: str,
    provider: str,
    is_admin: bool = False,
    ensure_node: bool = False,
) -> None:
    with conn.cursor() as cur:
        if ensure_node:
            cur.execute(
                """
                INSERT INTO mesh.mesh_node (id, created_by, node_type, title, description, searchable, created_at, updated_at)
                VALUES (%s::uuid, %s::uuid, 'USER', 'Eval Admin', 'Synthetic admin for evaluation', false, now(), now())
                ON CONFLICT (id) DO NOTHING
                """,
                (user_id, user_id),
            )
        cur.execute(
            """
            INSERT INTO identity.user_identity (id, oauth_provider, oauth_subject, node_id, is_admin)
            VALUES (%s::uuid, %s, %s, %s::uuid, false)
            ON CONFLICT (oauth_provider, oauth_subject) DO NOTHING
            """,
            (identity_id, provider, f"eval-{identity_id}", user_id) if not is_admin
            else (identity_id, provider, f"eval-{identity_id}", user_id),
        )
        if is_admin:
            cur.execute(
                """
                UPDATE identity.user_identity
                SET is_admin = true
                WHERE id = %s::uuid
                """,
                (identity_id,),
            )
        cur.execute(
            """
            INSERT INTO mesh.mesh_node_consent (node_id, scope, granted_at, ip_hash, policy_version, revoked_at)
            VALUES (%s::uuid, %s, now(), %s, 'eval-script', NULL)
            """,
            (user_id, CONSENT_SCOPE, hashlib.sha256(b"127.0.0.1").hexdigest()),
        )
    conn.commit()


def cleanup_api_user(conn, user_id: str, identity_id: str, delete_node: bool = False) -> None:
    with conn.cursor() as cur:
        cur.execute(
            "DELETE FROM mesh.mesh_node_consent WHERE node_id = %s::uuid AND policy_version = 'eval-script'",
            (user_id,),
        )
        cur.execute("DELETE FROM identity.user_identity WHERE id = %s::uuid", (identity_id,))
        if delete_node:
            cur.execute("DELETE FROM mesh.mesh_node WHERE id = %s::uuid", (user_id,))
    conn.commit()


def build_search_query_from_candidate(c: Candidate) -> dict[str, Any]:
    sd = c.structured_data or {}
    profile_skills = dedupe_terms(c.tags + sd_list(c, "tools_and_tech") + sd_list(c, "skills_soft"))
    roles = dedupe_terms(split_roles(c.description))
    languages = dedupe_terms(sd_list(c, "languages_spoken"))
    industries = dedupe_terms(sd_list(c, "industries"))
    nice_skills = dedupe_terms(profile_skills + sd_list(c, "topics_frequent") + sd_list(c, "learning_areas"))[:20]
    keywords = dedupe_terms(profile_skills + roles + nice_skills)

    seniority_raw = str(sd.get("seniority", "")).strip().lower()
    seniority = seniority_raw if seniority_raw in {"junior", "mid", "senior", "lead"} else "unknown"

    return {
        "must_have": {
            "skills": [],
            "roles": roles,
            "languages": languages,
            "location": [],
            "industries": industries,
        },
        "nice_to_have": {
            "skills": nice_skills,
            "industries": [],
            "experience": [],
        },
        "seniority": seniority,
        "negative_filters": {
            "seniority": None,
            "skills": [],
            "location": [],
        },
        "keywords": keywords,
        "embedding_text": " ".join(keywords) if keywords else "search",
        "result_scope": "all",
    }


def evaluate_api_matches(
    candidates: list[Candidate],
    api_url: str,
    session_cookie: str,
    sample_size: int,
    rng: random.Random,
    relevance_mode: str = "embedding",
) -> tuple[list[QueryCase], dict[str, QueryMetrics]]:
    endpoint = api_url.rstrip("/") + "/api/v1/matches"
    out: dict[str, QueryMetrics] = {}
    user_candidates = [c for c in candidates if c.node_type == "USER"]
    sampled = user_candidates if len(user_candidates) <= sample_size else rng.sample(user_candidates, sample_size)
    api_cases: list[QueryCase] = []

    for seed in sampled:
        case_key = f"matches_{seed.node_id[:8]}"
        api_cases.append(
            QueryCase(
                key=case_key,
                text=seed.title,
                target_types=set(),
                relevance_fn=lambda _: False,
            )
        )

        relevant_ids = build_relevant_ids(seed, candidates, relevance_mode)
        try:
            payload = build_search_query_from_candidate(seed)
            data = post_json_with_cookie(endpoint, payload, session_cookie)
        except (HTTPError, URLError) as exc:
            print(f"[warn] API call failed for {case_key}: {exc}", file=sys.stderr)
            out[case_key] = QueryMetrics(
                0, 0, 0, 0, 0, 0, 0, len(relevant_ids), 0, [], None, None, None, None, None, None
            )
            continue

        ranked_ids, top_titles = extract_ranked_ids_and_top_titles(data, seed.node_id)
        out[case_key] = metrics_from_ranked_ids(ranked_ids, relevant_ids, top_titles)
    return api_cases, out


def extract_ranked_ids_and_top_titles(raw_data: Any, seed_node_id: str) -> tuple[list[str], list[str]]:
    raw_results = raw_data if isinstance(raw_data, list) else []
    ranked_ids: list[str] = []
    top_titles: list[str] = []
    for item in raw_results:
        if not isinstance(item, dict):
            continue
        node_id = str(item.get("id", "")).strip()
        if node_id == seed_node_id:
            continue
        if node_id:
            ranked_ids.append(node_id)
        title = str(item.get("title", "")).strip()
        if title and len(top_titles) < 3:
            top_titles.append(title)
    return ranked_ids, top_titles


def metrics_from_ranked_ids(ranked_ids: list[str], relevant_ids: set[str], top_titles: list[str]) -> QueryMetrics:
    ranked_relevance = [1 if node_id in relevant_ids else 0 for node_id in ranked_ids]
    return QueryMetrics(
        precision_at_5=precision_at_k(ranked_relevance, 5),
        precision_at_10=precision_at_k(ranked_relevance, 10),
        precision_at_20=precision_at_k(ranked_relevance, 20),
        recall_at_20=recall_at_k(ranked_relevance, len(relevant_ids), 20),
        recall_at_20_capped=recall_at_k_capped(ranked_relevance, len(relevant_ids), 20),
        mrr=reciprocal_rank(ranked_relevance),
        ndcg_at_10=ndcg_at_k(ranked_relevance, len(relevant_ids), 10),
        relevant_total=len(relevant_ids),
        relevant_found_top20=sum(ranked_relevance[:20]),
        top3_titles=top_titles,
        rel_mean_sim=None,
        rel_median_sim=None,
        rel_std_sim=None,
        irr_mean_sim=None,
        irr_median_sim=None,
        irr_std_sim=None,
    )


def evaluate_api_matches_me(
    conn,
    candidates: list[Candidate],
    api_url: str,
    session_secret: str,
    sample_size: int,
    rng: random.Random,
    relevance_mode: str = "strict",
) -> tuple[list[QueryCase], dict[str, QueryMetrics]]:
    endpoint = api_url.rstrip("/") + "/api/v1/matches/me"
    out: dict[str, QueryMetrics] = {}
    user_candidates = [c for c in candidates if c.node_type == "USER"]
    sampled = user_candidates if len(user_candidates) <= sample_size else rng.sample(user_candidates, sample_size)
    api_cases: list[QueryCase] = []

    for seed in sampled:
        case_key = f"my_mesh_{seed.node_id[:8]}"
        api_cases.append(
            QueryCase(
                key=case_key,
                text=seed.title,
                target_types=set(),
                relevance_fn=lambda _: False,
            )
        )
        relevant_ids = build_relevant_ids(seed, candidates, relevance_mode)
        identity_id = str(uuid.uuid4())
        provider = "eval-my-mesh"
        try:
            ensure_api_user_and_consent(
                conn,
                seed.node_id,
                identity_id,
                provider,
                is_admin=False,
                ensure_node=False,
            )
            token = encode_pm_session(seed.node_id, provider, session_secret)
            cookie = f"pm_session={token}"
            data = get_json_with_cookie(endpoint, cookie)
        except Exception as exc:  # noqa: BLE001
            print(f"[warn] /matches/me call failed for {case_key}: {exc}", file=sys.stderr)
            out[case_key] = QueryMetrics(
                0, 0, 0, 0, 0, 0, 0, len(relevant_ids), 0, [], None, None, None, None, None, None
            )
            continue
        finally:
            cleanup_api_user(conn, seed.node_id, identity_id, delete_node=False)

        ranked_ids, top_titles = extract_ranked_ids_and_top_titles(data, seed.node_id)
        out[case_key] = metrics_from_ranked_ids(ranked_ids, relevant_ids, top_titles)
    return api_cases, out


def python_embedding_text_legacy(c: Candidate, max_chars: int | None = None) -> str:
    # Legacy Python-side seed format kept for divergence diagnostics.
    parts = [
        f"type={c.node_type}",
        f"title={c.title}" if c.title else None,
        f"description={c.description}" if c.description else None,
        f"tags={', '.join(c.tags)}" if c.tags else None,
        f"country={c.country}" if c.country else None,
    ]
    sd = c.structured_data or {}
    for key in sorted(sd.keys()):
        value = sd.get(key)
        if value is None:
            continue
        if isinstance(value, list):
            values = [str(x).strip() for x in value if str(x).strip()]
            if values:
                parts.append(f"{key}={', '.join(values)}")
            continue
        text = str(value).strip()
        if text and text != "[]":
            parts.append(f"{key}={text}")
    text = " | ".join([p for p in parts if p])
    if max_chars is not None and max_chars > 0 and len(text) > max_chars:
        return text[:max_chars]
    return text


def python_embedding_text_aligned(c: Candidate, max_chars: int | None = None) -> str:
    text = java_embedding_text(c)
    if max_chars is not None and max_chars > 0 and len(text) > max_chars:
        return text[:max_chars]
    return text


def list_field(label: str, items: list[str]) -> str | None:
    items = [x for x in items if x]
    if not items:
        return None
    return f"{label}: {', '.join(items)}"


def join_with_optional(parts: list[str], optional_parts: list[str], max_chars: int) -> str:
    selected = list(parts)
    current_length = len(". ".join(selected))
    for section in optional_parts:
        candidate_length = len(section) if current_length == 0 else current_length + 2 + len(section)
        if candidate_length > max_chars:
            break
        selected.append(section)
        current_length = candidate_length
    return ". ".join([p for p in selected if p])


def java_embedding_text(c: Candidate) -> str:
    sd = c.structured_data or {}
    if c.node_type == "USER":
        primary_parts = [
            f"Roles: {c.description}" if c.description else None,
            list_field("Technical Skills", c.tags),
            list_field("Tools", normalize_list(sd.get("tools_and_tech")) if isinstance(sd.get("tools_and_tech"), list) else []),
            f"Industries: {sd.get('industries')}" if sd.get("industries") else None,
            f"Seniority: {sd.get('seniority')}" if sd.get("seniority") else None,
            list_field("Languages", normalize_list(sd.get("languages_spoken")) if isinstance(sd.get("languages_spoken"), list) else []),
            list_field("Education", normalize_list(sd.get("education")) if isinstance(sd.get("education"), list) else []),
            f"Country: {c.country}" if c.country else None,
        ]
        optional_parts = [
            list_field("Topics", normalize_list(sd.get("topics_frequent")) if isinstance(sd.get("topics_frequent"), list) else []),
            list_field("Learning", normalize_list(sd.get("learning_areas")) if isinstance(sd.get("learning_areas"), list) else []),
            list_field("Projects", normalize_list(sd.get("project_types")) if isinstance(sd.get("project_types"), list) else []),
            f"Work Mode: {sd.get('work_mode')}" if sd.get("work_mode") else None,
            f"Employment: {sd.get('employment_type')}" if sd.get("employment_type") else None,
            list_field("Soft Skills", normalize_list(sd.get("skills_soft")) if isinstance(sd.get("skills_soft"), list) else []),
        ]
        primary = [p for p in primary_parts if p]
        optional = [p for p in optional_parts if p]
        return join_with_optional(primary, optional, 1200)

    if c.node_type == "JOB":
        sd = c.structured_data or {}
        req = sd.get("requirements_text")
        skills_required = sd.get("skills_required") if isinstance(sd.get("skills_required"), list) else []
        parts = [
            f"Title: {c.title}" if c.title else None,
            f"Description: {c.description}" if c.description else None,
            f"Requirements: {req}" if req else None,
            list_field("Required Skills", normalize_list(skills_required)),
            f"Work Mode: {sd.get('work_mode')}" if sd.get("work_mode") else None,
            f"Employment: {sd.get('employment_type')}" if sd.get("employment_type") else None,
            f"Country: {c.country}" if c.country else None,
        ]
        return ". ".join([p for p in parts if p])

    parts = [
        f"Type: {c.node_type}",
        f"Title: {c.title}" if c.title else None,
        f"Description: {c.description}" if c.description else None,
        list_field("Tags", c.tags),
        f"Country: {c.country}" if c.country else None,
    ]
    for k, v in (c.structured_data or {}).items():
        s = str(v).strip()
        if s and s != "[]":
            parts.append(f"{k.replace('_', ' ')}: {s}")
    return ". ".join([p for p in parts if p])


def evaluate_text_divergence(
    candidates: list[Candidate],
    ollama_url: str,
    model: str,
    sample_size: int,
    rng: random.Random,
    divergence_mode: str,
) -> dict[str, Any]:
    users = [c for c in candidates if c.node_type == "USER"]
    if not users:
        return {"count": 0}
    sample = users if len(users) <= sample_size else rng.sample(users, sample_size)

    sims: list[float] = []
    rows: list[dict[str, Any]] = []
    py_builder = (
        python_embedding_text_aligned
        if divergence_mode == "aligned-vs-java"
        else python_embedding_text_legacy
    )
    for c in sample:
        py_text = py_builder(c, max_chars=1200)
        java_text = java_embedding_text(c)
        py_emb = embed_text(ollama_url, model, py_text)
        java_emb = embed_text(ollama_url, model, java_text)
        sim = cosine(py_emb, java_emb)
        sims.append(sim)
        rows.append(
            {
                "id": c.node_id,
                "title": c.title,
                "similarity": sim,
                "mode": divergence_mode,
            }
        )

    rows.sort(key=lambda x: x["similarity"])
    return {
        "count": len(rows),
        "mean": mean(sims),
        "median": median(sims),
        "min": min(sims) if sims else None,
        "max": max(sims) if sims else None,
        "lowest": rows[:3],
    }


def fmt(v: float | None) -> str:
    if v is None:
        return "-"
    return f"{v:.3f}"


def print_query_table(title: str, cases: list[QueryCase], metrics_by_case: dict[str, QueryMetrics]) -> None:
    print(f"\n{title}")
    print("-" * len(title))
    print(
        "query".ljust(26)
        + "P@5   P@10  P@20  R@20  Rc@20 MRR   NDCG@10 rel/top20 totalRel  top3"
    )
    for case in cases:
        m = metrics_by_case[case.key]
        tops = " | ".join(m.top3_titles) if m.top3_titles else "-"
        print(
            case.key.ljust(26)
            + f"{m.precision_at_5:0.3f} "
            + f"{m.precision_at_10:0.3f} "
            + f"{m.precision_at_20:0.3f} "
            + f"{m.recall_at_20:0.3f} "
            + f"{m.recall_at_20_capped:0.3f} "
            + f"{m.mrr:0.3f} "
            + f"{m.ndcg_at_10:0.3f} "
            + f"{str(m.relevant_found_top20).rjust(3)}/{str(20).ljust(5)} "
            + f"{str(m.relevant_total).rjust(3)}  "
            + tops[:60]
        )


def print_aggregate(title: str, cases: list[QueryCase], metrics_by_case: dict[str, QueryMetrics]) -> None:
    agg = compute_aggregate(cases, metrics_by_case)
    if agg is None:
        return
    vals = [metrics_by_case[c.key] for c in cases]
    if not vals:
        return
    print(f"\n{title}")
    print("-" * len(title))
    print(f"mean P@5:    {fmt(agg.precision_at_5)}")
    print(f"mean P@10:   {fmt(agg.precision_at_10)}")
    print(f"mean P@20:   {fmt(agg.precision_at_20)}")
    print(f"mean Recall@20: {fmt(agg.recall_at_20)}")
    print(f"mean Recall@20(capped): {fmt(agg.recall_at_20_capped)}")
    print(f"mean MRR:    {fmt(agg.mrr)}")
    print(f"mean NDCG@10:{fmt(agg.ndcg_at_10)}")


def relevant_only_cases(cases: list[QueryCase], metrics_by_case: dict[str, QueryMetrics]) -> list[QueryCase]:
    return [case for case in cases if metrics_by_case[case.key].relevant_total > 0]


def print_aggregate_relevant_only(
    title: str, cases: list[QueryCase], metrics_by_case: dict[str, QueryMetrics]
) -> None:
    rel_cases = relevant_only_cases(cases, metrics_by_case)
    if not rel_cases:
        return
    print_aggregate(f"{title} (queries with relevant items only)", rel_cases, metrics_by_case)


def compute_aggregate(
    cases: list[QueryCase], metrics_by_case: dict[str, QueryMetrics]
) -> AggregateMetrics | None:
    vals = [metrics_by_case[c.key] for c in cases]
    if not vals:
        return None
    return AggregateMetrics(
        precision_at_5=float(mean([x.precision_at_5 for x in vals]) or 0.0),
        precision_at_10=float(mean([x.precision_at_10 for x in vals]) or 0.0),
        precision_at_20=float(mean([x.precision_at_20 for x in vals]) or 0.0),
        recall_at_20=float(mean([x.recall_at_20 for x in vals]) or 0.0),
        recall_at_20_capped=float(mean([x.recall_at_20_capped for x in vals]) or 0.0),
        mrr=float(mean([x.mrr for x in vals]) or 0.0),
        ndcg_at_10=float(mean([x.ndcg_at_10 for x in vals]) or 0.0),
    )


def traffic_light_status(value: float, green: float, yellow: float) -> str:
    if value >= green:
        return "GREEN"
    if value >= yellow:
        return "YELLOW"
    return "RED"


def combine_status(left: str, right: str) -> str:
    order = {"RED": 0, "YELLOW": 1, "GREEN": 2}
    left_score = order.get(left, 0)
    right_score = order.get(right, 0)
    worst = min(left_score, right_score)
    for key, score in order.items():
        if score == worst:
            return key
    return "RED"


def summarize_gate(p10_status: str, ndcg_status: str, mrr_status: str) -> str:
    # Conservative summary: keep at least YELLOW/GREEN consistency on rank quality,
    # then cap by the worst of the three core metrics.
    rank_pair = combine_status(p10_status, ndcg_status)
    return combine_status(rank_pair, mrr_status)


def print_quality_gate(
    direct_agg: AggregateMetrics,
    api_agg: AggregateMetrics | None,
    my_mesh_aggs: dict[str, AggregateMetrics] | None = None,
) -> tuple[str, str | None, dict[str, str], str]:
    print("\nQuality Gate (Traffic Light)")
    print("-----------------------")
    d_p10 = traffic_light_status(direct_agg.precision_at_10, green=0.45, yellow=0.35)
    d_ndcg = traffic_light_status(direct_agg.ndcg_at_10, green=0.60, yellow=0.52)
    d_mrr = traffic_light_status(direct_agg.mrr, green=0.75, yellow=0.60)
    print(
        f"direct: P@10={fmt(direct_agg.precision_at_10)} [{d_p10}], "
        f"NDCG@10={fmt(direct_agg.ndcg_at_10)} [{d_ndcg}], "
        f"MRR={fmt(direct_agg.mrr)} [{d_mrr}]"
    )
    direct_overall = summarize_gate(d_p10, d_ndcg, d_mrr)
    print(f"direct_overall: {direct_overall}")

    api_overall: str | None = None
    if api_agg is not None:
        a_p10 = traffic_light_status(api_agg.precision_at_10, green=0.70, yellow=0.55)
        a_ndcg = traffic_light_status(api_agg.ndcg_at_10, green=0.65, yellow=0.55)
        a_mrr = traffic_light_status(api_agg.mrr, green=0.50, yellow=0.40)
        print(
            f"api:    P@10={fmt(api_agg.precision_at_10)} [{a_p10}], "
            f"NDCG@10={fmt(api_agg.ndcg_at_10)} [{a_ndcg}], "
            f"MRR={fmt(api_agg.mrr)} [{a_mrr}]"
        )
        api_overall = summarize_gate(a_p10, a_ndcg, a_mrr)
        print(f"api_overall: {api_overall}")

    my_mesh_overalls: dict[str, str] = {}
    if my_mesh_aggs:
        for mode in sorted(my_mesh_aggs.keys()):
            agg = my_mesh_aggs[mode]
            m_p10 = traffic_light_status(agg.precision_at_10, green=0.70, yellow=0.55)
            m_ndcg = traffic_light_status(agg.ndcg_at_10, green=0.65, yellow=0.55)
            m_mrr = traffic_light_status(agg.mrr, green=0.50, yellow=0.40)
            print(
                f"my_mesh[{mode}]: P@10={fmt(agg.precision_at_10)} [{m_p10}], "
                f"NDCG@10={fmt(agg.ndcg_at_10)} [{m_ndcg}], "
                f"MRR={fmt(agg.mrr)} [{m_mrr}]"
            )
            mode_overall = summarize_gate(m_p10, m_ndcg, m_mrr)
            my_mesh_overalls[mode] = mode_overall
            print(f"my_mesh_overall[{mode}]: {mode_overall}")

    overall_global = combine_status(direct_overall, api_overall) if api_overall is not None else direct_overall
    for mode_overall in my_mesh_overalls.values():
        overall_global = combine_status(overall_global, mode_overall)
    print(f"overall_global: {overall_global}")
    return direct_overall, api_overall, my_mesh_overalls, overall_global


def print_action_hints(
    direct_agg: AggregateMetrics,
    api_agg: AggregateMetrics | None,
    my_mesh_aggs: dict[str, AggregateMetrics] | None,
    direct_overall: str,
    api_overall: str | None,
    my_mesh_overalls: dict[str, str] | None,
    overall_global: str,
) -> None:
    if overall_global == "GREEN":
        print("\nAction Hints")
        print("------------")
        print("- All checks are GREEN. Keep configuration as-is and monitor trend across runs.")
        return

    hints: list[str] = []
    if overall_global == "YELLOW":
        hints.append("Borderline quality. Track the weakest metric and re-run with the same seed to confirm stability.")
    else:
        hints.append("Global quality is RED. Prioritize fixes on the failing block before tuning secondary metrics.")

    if direct_overall != "GREEN":
        if direct_agg.precision_at_10 < 0.35:
            hints.append(
                "Direct P@10 is low: validate embedding model choice and query text construction before rank-weight tuning."
            )
        if direct_agg.ndcg_at_10 < 0.55:
            hints.append(
                "Direct NDCG@10 is weak: inspect top-10 ordering and candidate embedding text quality; this block bypasses SearchService scoring."
            )
        if direct_agg.mrr < 0.60:
            hints.append(
                "Direct MRR is weak: investigate first-hit embedding relevance (query wording, profile text builder, and embedding refresh)."
            )

    if api_agg is not None and api_overall is not None and api_overall != "GREEN":
        if api_agg.precision_at_10 < 0.55:
            hints.append("API P@10 is weak: verify /matches payload construction and seeded profile completeness.")
        if api_agg.ndcg_at_10 < 0.55:
            hints.append("API NDCG@10 is weak: inspect reranking behavior and reason-code distribution in top results.")
        if api_agg.mrr < 0.40:
            hints.append("API MRR is weak: ensure relevant items appear in the first ranks (check self-match filtering).")

    if api_agg is not None and api_agg.recall_at_20 < 0.10 and api_agg.recall_at_20_capped >= 0.70:
        hints.append(
            "API strict relevance: low Recall@20 with high Recall@20(capped) indicates a very large relevant pool; prefer capped recall and ranking metrics."
        )

    if my_mesh_aggs is not None and my_mesh_overalls is not None:
        for mode, status in my_mesh_overalls.items():
            if status == "GREEN":
                continue
            agg = my_mesh_aggs.get(mode)
            if agg is None:
                continue
            if agg.precision_at_10 < 0.55:
                hints.append(
                    f"My Mesh ({mode}) P@10 is weak: inspect /matches/me ranking and self-profile query extraction."
                )
            if agg.ndcg_at_10 < 0.55:
                hints.append(
                    f"My Mesh ({mode}) NDCG@10 is weak: tune top-rank ordering behavior for /matches/me results."
                )
            if agg.mrr < 0.40:
                hints.append(
                    f"My Mesh ({mode}) MRR is weak: ensure the best neighbors appear in the first results."
                )

    print("\nAction Hints")
    print("------------")
    for hint in hints:
        print(f"- {hint}")


def print_similarity_separation(cases: list[QueryCase], metrics_by_case: dict[str, QueryMetrics]) -> None:
    print("\nSimilarity separation (direct eval)")
    print("----------------------------------")
    print("query".ljust(26) + "rel_mean rel_med rel_std irr_mean irr_med irr_std")
    for case in cases:
        m = metrics_by_case[case.key]
        print(
            case.key.ljust(26)
            + f"{fmt(m.rel_mean_sim).rjust(7)} "
            + f"{fmt(m.rel_median_sim).rjust(7)} "
            + f"{fmt(m.rel_std_sim).rjust(7)} "
            + f"{fmt(m.irr_mean_sim).rjust(8)} "
            + f"{fmt(m.irr_median_sim).rjust(7)} "
            + f"{fmt(m.irr_std_sim).rjust(7)}"
        )


def print_divergence_report(report: dict[str, Any]) -> None:
    print("\nEmbedding text divergence")
    print("-------------------------")
    if report.get("count", 0) == 0:
        print("No USER profiles available for divergence check.")
        return
    print(f"sample size: {report['count']}")
    print(f"mean cosine(py_text, java_text): {fmt(report.get('mean'))}")
    print(f"median cosine(py_text, java_text): {fmt(report.get('median'))}")
    print(f"min cosine(py_text, java_text): {fmt(report.get('min'))}")
    print(f"max cosine(py_text, java_text): {fmt(report.get('max'))}")
    low = report.get("lowest", [])
    if low:
        print("lowest 3 examples:")
        for row in low:
            print(f"  - {row['title']} ({row['id']}): {row['similarity']:.3f}")


def main() -> int:
    args = parse_args()
    rng = random.Random(args.seed)

    db_url = args.db_url.strip() or discover_db_url()
    print("[info] Search Quality Evaluation Battery")
    print(f"[info] db_url={db_url}")
    print(f"[info] ollama_url={args.ollama_url}")
    print(f"[info] ollama_model={args.ollama_model}")
    print(f"[info] query_prefix={args.query_prefix!r}")
    print(f"[info] query_suite={args.query_suite}")
    print(f"[info] divergence_mode={args.divergence_mode}")
    print(f"[info] eval_my_mesh={args.eval_my_mesh}")
    print(f"[info] my_mesh_relevance_mode={args.my_mesh_relevance_mode}")
    print(f"[info] api_url={args.api_url}")

    try:
        conn = connect_db(db_url)
    except Exception as exc:  # noqa: BLE001
        print(f"[error] DB connection failed: {exc}", file=sys.stderr)
        return 2

    try:
        candidates = fetch_candidates(conn)
        if not candidates:
            print("[error] No searchable candidates with embeddings found in DB.", file=sys.stderr)
            return 2
        print(f"[info] loaded candidates={len(candidates)}")
        counts: dict[str, int] = {}
        for c in candidates:
            counts[c.node_type] = counts.get(c.node_type, 0) + 1
        print("[info] by node_type:", ", ".join(f"{k}={v}" for k, v in sorted(counts.items())))

        all_cases = build_query_cases()
        cases = select_query_suite(all_cases, args.query_suite)
        print(f"[info] query cases={len(cases)}")

        start = time.perf_counter()
        direct_metrics = evaluate_direct(
            candidates, cases, args.ollama_url, args.ollama_model, args.query_prefix
        )
        print(f"[info] direct eval elapsed={time.perf_counter() - start:.2f}s")

        print_query_table("Direct embedding retrieval results", cases, direct_metrics)
        print_aggregate("Direct embedding aggregate", cases, direct_metrics)
        print_aggregate_relevant_only("Direct embedding aggregate", cases, direct_metrics)
        print_similarity_separation(cases, direct_metrics)
        direct_agg = compute_aggregate(cases, direct_metrics)

        divergence = evaluate_text_divergence(
            candidates,
            args.ollama_url,
            args.ollama_model,
            args.sample_divergence,
            rng,
            args.divergence_mode,
        )
        print_divergence_report(divergence)

        api_agg_for_gate: AggregateMetrics | None = None
        my_mesh_aggs_for_gate: dict[str, AggregateMetrics] = {}
        user_samples = [c for c in candidates if c.node_type == "USER"]
        if not user_samples:
            print("[warn] No USER candidates found, skipping /matches API evaluation.")
        else:
            admin_user_id = str(uuid.uuid4())
            admin_identity_id = str(uuid.uuid4())
            provider = "eval-admin"
            cleanup_needed = False
            try:
                ensure_api_user_and_consent(
                    conn,
                    admin_user_id,
                    admin_identity_id,
                    provider,
                    is_admin=True,
                    ensure_node=True,
                )
                cleanup_needed = True
                admin_token = encode_pm_session(admin_user_id, provider, args.session_secret)
                admin_cookie = f"pm_session={admin_token}"
                api_cases_embedding, api_metrics_embedding = evaluate_api_matches(
                    candidates,
                    args.api_url,
                    admin_cookie,
                    args.api_sample_size,
                    rng,
                    relevance_mode="embedding",
                )
                print_query_table(
                    "API /matches schema-input results (embedding-neighbors relevance)",
                    api_cases_embedding,
                    api_metrics_embedding,
                )
                print_aggregate(
                    "API /matches aggregate (embedding-neighbors relevance)",
                    api_cases_embedding,
                    api_metrics_embedding,
                )
                print_aggregate_relevant_only(
                    "API /matches aggregate (embedding-neighbors relevance)",
                    api_cases_embedding,
                    api_metrics_embedding,
                )

                api_cases_strict, api_metrics_strict = evaluate_api_matches(
                    candidates,
                    args.api_url,
                    admin_cookie,
                    args.api_sample_size,
                    rng,
                    relevance_mode="strict",
                )
                print_query_table(
                    "API /matches schema-input results (strict profile-overlap relevance)",
                    api_cases_strict,
                    api_metrics_strict,
                )
                print_aggregate(
                    "API /matches aggregate (strict profile-overlap relevance)",
                    api_cases_strict,
                    api_metrics_strict,
                )
                print_aggregate_relevant_only(
                    "API /matches aggregate (strict profile-overlap relevance)",
                    api_cases_strict,
                    api_metrics_strict,
                )
                api_agg_for_gate = compute_aggregate(api_cases_strict, api_metrics_strict)

                if args.eval_my_mesh:
                    my_mesh_modes = (
                        ["embedding", "strict"]
                        if args.my_mesh_relevance_mode == "both"
                        else [args.my_mesh_relevance_mode]
                    )
                    for mode in my_mesh_modes:
                        my_mesh_cases, my_mesh_metrics = evaluate_api_matches_me(
                            conn,
                            candidates,
                            args.api_url,
                            args.session_secret,
                            args.my_mesh_sample_size,
                            rng,
                            relevance_mode=mode,
                        )
                        print_query_table(
                            f"API /matches/me results ({mode} relevance)",
                            my_mesh_cases,
                            my_mesh_metrics,
                        )
                        print_aggregate(
                            f"API /matches/me aggregate ({mode} relevance)",
                            my_mesh_cases,
                            my_mesh_metrics,
                        )
                        print_aggregate_relevant_only(
                            f"API /matches/me aggregate ({mode} relevance)",
                            my_mesh_cases,
                            my_mesh_metrics,
                        )
                        my_mesh_agg = compute_aggregate(my_mesh_cases, my_mesh_metrics)
                        if my_mesh_agg is not None:
                            my_mesh_aggs_for_gate[mode] = my_mesh_agg
            finally:
                if cleanup_needed:
                    cleanup_api_user(conn, admin_user_id, admin_identity_id, delete_node=True)

        if direct_agg is not None:
            direct_overall, api_overall, my_mesh_overalls, overall_global = print_quality_gate(
                direct_agg,
                api_agg_for_gate,
                my_mesh_aggs_for_gate if my_mesh_aggs_for_gate else None,
            )
            print_action_hints(
                direct_agg,
                api_agg_for_gate,
                my_mesh_aggs_for_gate if my_mesh_aggs_for_gate else None,
                direct_overall,
                api_overall,
                my_mesh_overalls if my_mesh_overalls else None,
                overall_global,
            )

        print("\n[done] Evaluation completed.")
        print("[next] If divergence cosine is low, regenerate embeddings in Java text format.")
        print("[next] If direct P@10/MRR are weak, tune query/profile embedding text and regenerate vectors.")
        print("[next] If API ranking is weak, tune SearchService weighting and parser extraction.")
        return 0
    finally:
        conn.close()


if __name__ == "__main__":
    raise SystemExit(main())
