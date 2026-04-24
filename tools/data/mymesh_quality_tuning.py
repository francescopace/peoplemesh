#!/usr/bin/env python3
"""
Unified MyMesh quality + tuning tool.

Subcommands:
- quality: run a single evaluation
- tune: search a configurable tuning space and produce summary artifacts
- gate: validate a summary.json against regression thresholds
- tune-and-gate: run sweep then apply regression gate
"""

from __future__ import annotations

import argparse
import itertools
import json
import math
import random
import re
import sys
import time
from dataclasses import dataclass
from pathlib import Path
from typing import Any
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen


ROOT = Path(__file__).resolve().parents[2]
RESULT_DIR = ROOT / "tools" / "data" / "results"


OPTION_KEY_ORDER = [
    "weightEmbedding",
    "weightMustHave",
    "weightNiceToHave",
    "weightLanguage",
    "weightGeography",
    "weightIndustry",
    "weightSeniority",
    "weightGenericKeyword",
    "skillMatchThreshold",
    "profileNiceSkillsCap",
    "profileIncludeNiceToHave",
    "profileIncludeInterestsInEmbeddingText",
]

REQUEST_TYPE_CHOICES = ("PEOPLE", "JOB", "COMMUNITY", "EVENT", "PROJECT", "INTEREST_GROUP")

CURRENT_BASELINE_SEARCH_OPTIONS = {
    "weightEmbedding": 0.55,
    "weightMustHave": 0.50,
    "weightNiceToHave": 0.40,
    "weightLanguage": 0.10,
    "weightGeography": 0.25,
    "weightIndustry": 0.10,
    "weightSeniority": 0.01,
    "weightGenericKeyword": 0.20,
    "skillMatchThreshold": 0.80,
    "profileNiceSkillsCap": 10,
    "profileIncludeNiceToHave": True,
    "profileIncludeInterestsInEmbeddingText": True,
}

DEFAULT_TUNE_SEARCH_SPACE = {
    "weightEmbedding": [0.55, 0.60, 0.65, 0.70, 0.75, 0.80, 0.85, 0.90],
    "weightMustHave": [0.10, 0.20, 0.30, 0.40, 0.50],
    "weightNiceToHave": [0.10, 0.20, 0.30, 0.40],
    "weightLanguage": [0.00, 0.05, 0.10, 0.15, 0.20],
    "weightGeography": [0.05, 0.10, 0.15, 0.20, 0.25],
    "weightIndustry": [0.00, 0.05, 0.10, 0.15],
    "weightSeniority": [0.00, 0.01, 0.05, 0.10, 0.15],
    "weightGenericKeyword": [0.05, 0.10, 0.15, 0.20, 0.25],
    "skillMatchThreshold": [0.60, 0.65, 0.70, 0.75, 0.80],
    "profileNiceSkillsCap": [0, 10, 20, 40, 60, 80],
    "profileIncludeNiceToHave": [True, False],
    "profileIncludeInterestsInEmbeddingText": [True, False],
}


@dataclass(frozen=True)
class TuneCandidate:
    name: str
    search_options: dict[str, Any]
    source: str
    parent: str | None = None


@dataclass
class Candidate:
    node_id: str
    node_type: str
    title: str
    description: str
    tags: list[str]
    country: str | None
    structured_data: dict[str, Any]


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Unified MyMesh quality+tuning utility.")
    sub = parser.add_subparsers(dest="command", required=True)

    quality = sub.add_parser("quality", help="Run one evaluation pass.")
    _add_quality_args(quality)

    tune = sub.add_parser("tune", help="Search a tuning space for the best configurations.")
    _add_tune_args(tune)

    gate = sub.add_parser("gate", help="Run regression gate on a summary.json.")
    _add_gate_args(gate)

    tune_gate = sub.add_parser("tune-and-gate", help="Run tuning-space search then apply gate.")
    _add_tune_args(tune_gate)
    _add_gate_args(tune_gate, include_summary_json=False)

    return parser.parse_args()


def _add_quality_args(parser: argparse.ArgumentParser) -> None:
    parser.add_argument("--api-url", default="http://localhost:8080")
    parser.add_argument("--maintenance-key", default="dev-local-maintenance-key")
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument("--api-sample-size", type=int, default=20)
    parser.add_argument("--seed-probe-limit", type=int, default=100)
    parser.add_argument("--min-eligible-seeds", type=int, default=12)
    parser.add_argument("--request-retries", type=int, default=3)
    parser.add_argument("--request-retry-delay-ms", type=int, default=350)
    parser.add_argument(
        "--seed-user-ids",
        default="",
        help="Optional comma-separated USER node IDs to evaluate (bypasses random seed discovery).",
    )
    parser.add_argument(
        "--relevance-mode",
        choices=["balanced_affinity"],
        default="balanced_affinity",
    )
    parser.add_argument(
        "--use-seed-country",
        action="store_true",
        help="Filter matches to the seed user's country. Default is All Countries to mirror My Mesh web.",
    )
    parser.add_argument("--health-timeout-seconds", type=int, default=120)
    parser.add_argument("--search-options-json", default="", help="JSON object mapped to search_options")
    parser.add_argument(
        "--omit-search-options",
        action="store_true",
        help="Do not send search_options in the tuning request; rely on backend defaults.",
    )
    parser.add_argument("--output-json", default="", help="Optional path to write summary JSON")
    parser.add_argument(
        "--type",
        dest="request_type",
        choices=REQUEST_TYPE_CHOICES,
        default="PEOPLE",
        help="Target node type requested from /matches/me tuning endpoint.",
    )


def _add_tune_args(parser: argparse.ArgumentParser) -> None:
    _add_quality_args(parser)
    parser.add_argument(
        "--max-evals",
        type=int,
        default=48,
        help="Maximum number of candidate configurations to evaluate during tune.",
    )
    parser.add_argument(
        "--refine-top-k",
        type=int,
        default=5,
        help="Refine the top K successful candidates by exploring adjacent parameter values.",
    )
    parser.add_argument(
        "--refine-rounds",
        type=int,
        default=2,
        help="Number of neighbor-refinement rounds after the initial exploration pass.",
    )
    parser.add_argument(
        "--search-space-json",
        default="",
        help=(
            "Optional JSON object describing the search space. Values may be arrays, "
            "a scalar, or {min,max,step}/{values:[...]}."
        ),
    )


def _add_gate_args(parser: argparse.ArgumentParser, include_summary_json: bool = True) -> None:
    if include_summary_json:
        parser.add_argument("--summary-json", required=True, help="Path to summary.json from tune run")
    parser.add_argument("--min-ndcg-at-10", type=float, default=0.85)
    parser.add_argument("--min-p10", type=float, default=0.80)
    parser.add_argument("--min-mrr", type=float, default=0.80)


def wait_for_health(api_url: str, timeout_seconds: int) -> bool:
    deadline = time.time() + timeout_seconds
    endpoint = api_url.rstrip("/") + "/q/health"
    while time.time() < deadline:
        try:
            req = Request(endpoint, method="GET")
            with urlopen(req, timeout=5) as resp:
                if resp.status == 200:
                    return True
        except Exception:  # noqa: BLE001
            pass
        time.sleep(1.0)
    return False


def _parse_search_options_json(raw: str) -> dict[str, Any]:
    text = (raw or "").strip()
    if not text:
        return {}
    parsed = json.loads(text)
    if not isinstance(parsed, dict):
        raise RuntimeError("--search-options-json must decode to a JSON object")
    return parsed


def _resolve_quality_search_options(args: argparse.Namespace) -> dict[str, Any] | None:
    if args.omit_search_options:
        return None
    return _parse_search_options_json(args.search_options_json) or _default_search_options()


def _resolve_request_country(args: argparse.Namespace, seed_country: str | None) -> str | None:
    if not getattr(args, "use_seed_country", False):
        return None
    value = str(seed_country or "").strip()
    return value or None


def _repository_node_type(request_type: str) -> str:
    normalized = str(request_type or "PEOPLE").strip().upper()
    return "USER" if normalized == "PEOPLE" else normalized


def _default_search_options() -> dict[str, Any]:
    return dict(CURRENT_BASELINE_SEARCH_OPTIONS)


def _default_search_space() -> dict[str, list[Any]]:
    return {key: list(values) for key, values in DEFAULT_TUNE_SEARCH_SPACE.items()}


def _canonicalize_option_value(value: Any) -> Any:
    if isinstance(value, bool):
        return value
    if isinstance(value, float):
        return round(value, 6)
    return value


def _dedupe_values(values: list[Any]) -> list[Any]:
    out: list[Any] = []
    seen: set[str] = set()
    for value in values:
        normalized = _canonicalize_option_value(value)
        marker = json.dumps(normalized, sort_keys=True, separators=(",", ":"))
        if marker in seen:
            continue
        seen.add(marker)
        out.append(normalized)
    return out


def _expand_search_space_values(raw: Any) -> list[Any]:
    if isinstance(raw, dict):
        if isinstance(raw.get("values"), list):
            return _dedupe_values(raw["values"])
        if {"min", "max", "step"} <= set(raw.keys()):
            min_value = raw["min"]
            max_value = raw["max"]
            step = raw["step"]
            if step == 0:
                raise RuntimeError("search-space range step must be non-zero")
            is_integral = all(isinstance(v, int) and not isinstance(v, bool) for v in (min_value, max_value, step))
            values: list[Any] = []
            current = min_value
            epsilon = abs(step) / 1_000_000.0
            while current <= max_value + epsilon:
                values.append(int(current) if is_integral else round(float(current), 6))
                current += step
            return _dedupe_values(values)
        raise RuntimeError("search-space dict values must use either 'values' or 'min'/'max'/'step'")
    if isinstance(raw, list):
        return _dedupe_values(raw)
    return [_canonicalize_option_value(raw)]


def _parse_search_space_json(raw: str) -> dict[str, list[Any]]:
    text = (raw or "").strip()
    if not text:
        return {}
    parsed = json.loads(text)
    if not isinstance(parsed, dict):
        raise RuntimeError("--search-space-json must decode to a JSON object")
    return {
        str(key): _expand_search_space_values(value)
        for key, value in parsed.items()
    }


def _option_key_order(search_space: dict[str, list[Any]], anchor_options: dict[str, Any]) -> list[str]:
    ordered: list[str] = []
    seen: set[str] = set()
    for key in OPTION_KEY_ORDER + list(search_space.keys()) + list(anchor_options.keys()):
        if key in seen:
            continue
        seen.add(key)
        ordered.append(key)
    return ordered


def _build_search_space(anchor_options: dict[str, Any], override_search_space: dict[str, list[Any]]) -> dict[str, list[Any]]:
    search_space = _default_search_space()
    search_space.update(override_search_space)
    for key, value in anchor_options.items():
        if value is None:
            continue
        values = search_space.setdefault(key, [])
        values.append(value)
    return {key: _dedupe_values(list(values)) for key, values in search_space.items()}


def _candidate_fingerprint(search_options: dict[str, Any]) -> str:
    normalized = {
        key: _canonicalize_option_value(value)
        for key, value in sorted(search_options.items())
        if value is not None
    }
    return json.dumps(normalized, sort_keys=True, separators=(",", ":"))


def _search_space_size(search_space: dict[str, list[Any]], option_keys: list[str]) -> int:
    size = 1
    for key in option_keys:
        values = search_space.get(key) or []
        if not values:
            continue
        size *= len(values)
    return size


def _candidate_from_values(name: str, values_by_key: dict[str, Any], source: str, parent: str | None = None) -> TuneCandidate:
    search_options = {
        key: _canonicalize_option_value(value)
        for key, value in values_by_key.items()
        if value is not None
    }
    return TuneCandidate(name=name, search_options=search_options, source=source, parent=parent)


def _enumerate_search_space(search_space: dict[str, list[Any]], option_keys: list[str]) -> list[dict[str, Any]]:
    value_lists = [search_space.get(key, []) or [None] for key in option_keys]
    out: list[dict[str, Any]] = []
    for combo in itertools.product(*value_lists):
        out.append({key: value for key, value in zip(option_keys, combo, strict=True)})
    return out


def _sample_search_space(
    search_space: dict[str, list[Any]],
    option_keys: list[str],
    sample_count: int,
    rng: random.Random,
) -> list[dict[str, Any]]:
    out: list[dict[str, Any]] = []
    seen: set[str] = set()
    max_attempts = max(sample_count * 30, 100)
    attempts = 0
    while len(out) < sample_count and attempts < max_attempts:
        attempts += 1
        candidate = {
            key: rng.choice(search_space.get(key, []) or [None])
            for key in option_keys
        }
        fingerprint = _candidate_fingerprint(candidate)
        if fingerprint in seen:
            continue
        seen.add(fingerprint)
        out.append(candidate)
    return out


def _additional_random_candidates(
    search_space: dict[str, list[Any]],
    option_keys: list[str],
    seen_fingerprints: set[str],
    sample_count: int,
    rng: random.Random,
    start_index: int,
) -> list[TuneCandidate]:
    raw_candidates = _sample_search_space(search_space, option_keys, sample_count, rng)
    out: list[TuneCandidate] = []
    next_index = start_index
    for values in raw_candidates:
        fingerprint = _candidate_fingerprint(values)
        if fingerprint in seen_fingerprints:
            continue
        seen_fingerprints.add(fingerprint)
        out.append(_candidate_from_values(f"candidate_{next_index:03d}_random", values, source="random"))
        next_index += 1
    return out


def _sort_results(results: list[dict[str, Any]]) -> list[dict[str, Any]]:
    successful = [r for r in results if r.get("status") == "ok"]
    successful.sort(
        key=lambda r: (
            r["my_mesh"]["ndcg_at_10"],
            r["my_mesh"]["precision_at_10"],
            r["my_mesh"]["mrr"],
            r["my_mesh"].get("precision_at_20", 0.0),
            r["my_mesh"].get("hit_rate_at_10", 0.0),
        ),
        reverse=True,
    )
    return successful


def _normalize_list(values: Any) -> list[str]:
    if not isinstance(values, list):
        return []
    out: list[str] = []
    for v in values:
        s = str(v).strip()
        if s:
            out.append(s)
    return out


def _dedupe_case_insensitive(values: list[str]) -> list[str]:
    out: list[str] = []
    seen: set[str] = set()
    for value in values:
        normalized = value.strip()
        if not normalized:
            continue
        marker = normalized.lower()
        if marker in seen:
            continue
        seen.add(marker)
        out.append(normalized)
    return out


def _split_comma_separated(text: str | None) -> list[str]:
    if not text:
        return []
    return _dedupe_case_insensitive([part.strip() for part in text.split(",")])


def _normalize_seniority(raw: Any) -> str:
    value = str(raw or "").strip().lower()
    if not value:
        return "unknown"
    return value


def _cap_list(values: list[str], max_size: int) -> list[str]:
    if max_size <= 0:
        return []
    if len(values) <= max_size:
        return values
    return values[:max_size]


def _parse_seed_user_ids(raw: str) -> list[str]:
    if not raw.strip():
        return []
    out: list[str] = []
    seen: set[str] = set()
    for token in raw.split(","):
        value = token.strip()
        if not value:
            continue
        if value in seen:
            continue
        seen.add(value)
        out.append(value)
    return out


def _post_json(url: str, payload: dict[str, Any], headers: dict[str, str] | None = None) -> Any:
    return _request_json(url, "POST", payload, headers, retries=1, retry_delay_ms=0)


def _get_json(url: str, headers: dict[str, str] | None = None) -> Any:
    return _request_json(url, "GET", None, headers, retries=1, retry_delay_ms=0)


def _request_json(
    url: str,
    method: str,
    payload: dict[str, Any] | None,
    headers: dict[str, str] | None,
    retries: int,
    retry_delay_ms: int,
) -> Any:
    attempts = max(1, retries)
    delay_seconds = max(0, retry_delay_ms) / 1000.0
    last_error: Exception | None = None
    for attempt in range(1, attempts + 1):
        try:
            body = json.dumps(payload).encode("utf-8") if payload is not None else None
            req = Request(url, data=body, method=method)
            req.add_header("Accept", "application/json")
            if payload is not None:
                req.add_header("Content-Type", "application/json")
            if headers:
                for k, v in headers.items():
                    req.add_header(k, v)
            with urlopen(req, timeout=120) as resp:
                return json.loads(resp.read().decode("utf-8"))
        except HTTPError as exc:
            if exc.code < 500 or attempt >= attempts:
                raise
            last_error = exc
        except (URLError, TimeoutError) as exc:
            if attempt >= attempts:
                raise
            last_error = exc
        if delay_seconds > 0:
            time.sleep(delay_seconds * attempt)
    if last_error is not None:
        raise last_error
    raise RuntimeError(f"request failed without explicit error: {method} {url}")


def _fetch_node_candidates(
    api_url: str,
    maintenance_key: str,
    node_type: str | None = None,
    page_size: int = 500,
) -> list[Candidate]:
    out: list[Candidate] = []
    page = 0
    while True:
        params = [f"searchable=true", f"page={page}", f"size={page_size}"]
        if node_type:
            params.append(f"type={node_type}")
        url = f"{api_url.rstrip('/')}/api/v1/maintenance/nodes?{'&'.join(params)}"
        payload = _get_json(url, headers={"X-Maintenance-Key": maintenance_key})
        if not isinstance(payload, list) or not payload:
            break
        for item in payload:
            if not isinstance(item, dict):
                continue
            candidate_node_type = str(item.get("nodeType", "")).upper()
            if not candidate_node_type:
                continue
            node_id = str(item.get("id", "")).strip()
            if not node_id:
                continue
            out.append(
                Candidate(
                    node_id=node_id,
                    node_type=candidate_node_type,
                    title=str(item.get("title", "")).strip(),
                    description=str(item.get("description", "")).strip(),
                    tags=_normalize_list(item.get("tags")),
                    country=item.get("country"),
                    structured_data=item.get("structuredData") if isinstance(item.get("structuredData"), dict) else {},
                )
            )
        if len(payload) < page_size:
            break
        page += 1
    return out


def _pool(candidate: Candidate, key: str) -> set[str]:
    vals = _normalize_list(candidate.structured_data.get(key))
    return {v.lower() for v in vals}


def _text_terms(parts: list[str]) -> set[str]:
    joined = " ".join(parts).lower()
    return {token for token in re.split(r"[^a-z0-9_+#.-]+", joined) if len(token) >= 2}


def _effective_profile_signals(candidate: Candidate, search_options: dict[str, Any], request_type: str) -> dict[str, bool]:
    profile_skills = _dedupe_case_insensitive(
        _normalize_list(candidate.tags)
        + _normalize_list(candidate.structured_data.get("tools_and_tech"))
        + _normalize_list(candidate.structured_data.get("skills_soft"))
    )
    roles = _split_comma_separated(candidate.description)
    industries = _dedupe_case_insensitive(_normalize_list(candidate.structured_data.get("industries")))
    interests = _dedupe_case_insensitive(
        _normalize_list(candidate.structured_data.get("learning_areas"))
        + _normalize_list(candidate.structured_data.get("project_types"))
        + _normalize_list(candidate.structured_data.get("hobbies"))
        + _normalize_list(candidate.structured_data.get("sports"))
        + _normalize_list(candidate.structured_data.get("causes"))
    )
    include_nice = bool(search_options.get("profileIncludeNiceToHave", True))
    nice_cap = int(search_options.get("profileNiceSkillsCap", CURRENT_BASELINE_SEARCH_OPTIONS["profileNiceSkillsCap"]))
    nice_skills = _cap_list(_dedupe_case_insensitive(profile_skills + interests), nice_cap)
    effective_nice = nice_skills if include_nice else []
    keywords = _dedupe_case_insensitive(profile_skills + roles + effective_nice)
    seniority = _normalize_seniority(candidate.structured_data.get("seniority"))
    normalized_type = str(request_type or "PEOPLE").strip().upper()
    if normalized_type == "PEOPLE":
        return {
            "weightEmbedding": bool(profile_skills or roles or effective_nice or industries),
            # Current ProfileSearchQueryBuilder sets must_have.skills and must_have.languages empty for My Mesh web.
            "weightMustHave": False,
            "weightNiceToHave": bool(effective_nice),
            "weightLanguage": False,
            "weightGeography": True,
            "weightIndustry": bool(industries),
            "weightSeniority": seniority != "unknown",
            "weightGenericKeyword": False,
            "skillMatchThreshold": bool(profile_skills or effective_nice),
            "profileNiceSkillsCap": bool(profile_skills or interests),
            "profileIncludeNiceToHave": bool(profile_skills or interests),
            "profileIncludeInterestsInEmbeddingText": bool(interests),
        }
    return {
        "weightEmbedding": bool(profile_skills or roles or effective_nice),
        # Current ProfileSearchQueryBuilder sets must_have.skills empty for My Mesh web profile query.
        "weightMustHave": False,
        "weightNiceToHave": bool(effective_nice),
        "weightLanguage": False,
        "weightGeography": True,
        "weightIndustry": False,
        "weightSeniority": False,
        "weightGenericKeyword": bool(keywords),
        "skillMatchThreshold": bool(profile_skills or effective_nice),
        "profileNiceSkillsCap": bool(profile_skills or interests),
        "profileIncludeNiceToHave": bool(profile_skills or interests),
        "profileIncludeInterestsInEmbeddingText": bool(interests),
    }


def _tailor_search_space_for_seed_sample(
    search_space: dict[str, list[Any]],
    seed_sample: list[Candidate],
    anchor_options: dict[str, Any],
    explicit_override_keys: set[str],
    request_type: str,
) -> tuple[dict[str, list[Any]], list[str], dict[str, int]]:
    activity_counts = {key: 0 for key in search_space.keys()}
    for seed in seed_sample:
        signals = _effective_profile_signals(seed, anchor_options, request_type)
        for key, active in signals.items():
            if active and key in activity_counts:
                activity_counts[key] += 1

    tailored = {key: list(values) for key, values in search_space.items()}
    inactive_keys: list[str] = []
    for key in list(tailored.keys()):
        if key in explicit_override_keys:
            continue
        if activity_counts.get(key, 0) > 0:
            continue
        # Keep always-on anchor dimensions if there is no signal model for them.
        if key not in activity_counts:
            continue
        inactive_keys.append(key)
        tailored.pop(key, None)
    return tailored, sorted(inactive_keys), activity_counts


def _relevance(seed: Candidate, cand: Candidate, request_type: str) -> bool:
    if seed.node_id == cand.node_id:
        return False
    normalized_type = str(request_type or "PEOPLE").strip().upper()
    if normalized_type != "PEOPLE":
        return _relevance_non_user(seed, cand)

    seed_skills = {s.lower() for s in seed.tags}.union(_pool(seed, "tools_and_tech"))
    cand_skills = {s.lower() for s in cand.tags}.union(_pool(cand, "tools_and_tech"))
    seed_interests = _pool(seed, "learning_areas").union(_pool(seed, "project_types"))
    cand_interests = _pool(cand, "learning_areas").union(_pool(cand, "project_types"))
    seed_soft = _pool(seed, "skills_soft")
    cand_soft = _pool(cand, "skills_soft")
    seed_lang = _pool(seed, "languages_spoken")
    cand_lang = _pool(cand, "languages_spoken")

    skill_overlap = len(seed_skills.intersection(cand_skills))
    interest_overlap = len(seed_interests.intersection(cand_interests))
    soft_overlap = len(seed_soft.intersection(cand_soft))
    lang_ok = not seed_lang or bool(seed_lang.intersection(cand_lang))
    country_ok = not seed.country or not cand.country or seed.country.lower() == str(cand.country).lower()
    return (skill_overlap >= 1 and lang_ok) or (interest_overlap >= 1 and soft_overlap >= 1 and country_ok)


def _relevance_non_user(seed: Candidate, cand: Candidate) -> bool:
    seed_skills = _dedupe_case_insensitive(
        _normalize_list(seed.tags)
        + _normalize_list(seed.structured_data.get("tools_and_tech"))
        + _normalize_list(seed.structured_data.get("skills_soft"))
    )
    seed_roles = _split_comma_separated(seed.description)
    seed_interests = _dedupe_case_insensitive(
        _normalize_list(seed.structured_data.get("learning_areas"))
        + _normalize_list(seed.structured_data.get("project_types"))
    )
    seed_skill_terms = {s.lower() for s in seed_skills + seed_roles}
    seed_interest_terms = {s.lower() for s in seed_interests}
    cand_terms = _text_terms([cand.title, cand.description] + _normalize_list(cand.tags))
    skill_hits = len(seed_skill_terms.intersection(cand_terms))
    interest_hits = len(seed_interest_terms.intersection(cand_terms))
    country_ok = not seed.country or not cand.country or seed.country.lower() == str(cand.country).lower()
    return skill_hits >= 1 or (interest_hits >= 1 and country_ok)


def _metrics_from_ranked_ids(ranked_ids: list[str], relevant_ids: set[str]) -> dict[str, float]:
    ranked_relevance = [1 if rid in relevant_ids else 0 for rid in ranked_ids]
    top10 = ranked_relevance[:10]
    top20 = ranked_relevance[:20]
    p10 = (sum(top10) / len(top10)) if top10 else 0.0
    p20 = (sum(top20) / len(top20)) if top20 else 0.0
    rr = 0.0
    for i, rel in enumerate(ranked_relevance, start=1):
        if rel:
            rr = 1.0 / i
            break
    ideal_len = min(len(relevant_ids), 10)
    idcg = sum(1.0 / math.log2(i + 1) for i in range(1, ideal_len + 1))
    dcg = 0.0
    for i, rel in enumerate(top10, start=1):
        if rel:
            dcg += 1.0 / math.log2(i + 1)
    ndcg10 = (dcg / idcg) if idcg > 0 else 0.0
    hit_rate_10 = 1.0 if any(top10) else 0.0
    return {
        "precision_at_10": p10,
        "precision_at_20": p20,
        "mrr": rr,
        "ndcg_at_10": ndcg10,
        "hit_rate_at_10": hit_rate_10,
    }


def _build_tuning_payload(request_type: str, country: str | None, search_options: dict[str, Any] | None) -> dict[str, Any]:
    payload = {
        "search_query": {
            "must_have": {"skills": [], "roles": [], "languages": [], "location": [], "industries": []},
            "nice_to_have": {"skills": [], "industries": [], "experience": []},
            "seniority": "unknown",
            "negative_filters": {"seniority": None, "skills": [], "location": []},
            "keywords": [],
            "embedding_text": "search",
            "result_scope": "all",
        },
        "type": str(request_type or "PEOPLE").strip().upper(),
        "limit": 20,
        "offset": 0,
    }
    if country:
        payload["country"] = country
    if search_options is not None:
        payload["search_options"] = search_options
    return payload


def _extract_ranked_ids(raw_data: Any, seed_id: str) -> list[str]:
    raw_results = raw_data if isinstance(raw_data, list) else (raw_data.get("results") if isinstance(raw_data, dict) else [])
    ranked_ids: list[str] = []
    if not isinstance(raw_results, list):
        return ranked_ids
    for item in raw_results:
        if not isinstance(item, dict):
            continue
        rid = str(item.get("id", "")).strip()
        if rid and rid != seed_id:
            ranked_ids.append(rid)
    return ranked_ids


def _pick_seed_sample(
    args: argparse.Namespace,
    candidates: list[Candidate],
    probe_search_options: dict[str, Any] | None,
) -> list[Candidate]:
    explicit_ids = _parse_seed_user_ids(args.seed_user_ids)
    if explicit_ids:
        by_id = {c.node_id: c for c in candidates}
        explicit = [by_id[x] for x in explicit_ids if x in by_id]
        if len(explicit) < args.min_eligible_seeds:
            raise RuntimeError(
                f"seed-user-ids provided {len(explicit_ids)}, matched {len(explicit)} candidates; "
                f"require at least {args.min_eligible_seeds} candidates in maintenance nodes list"
            )
        return explicit[: args.api_sample_size]

    rng = random.Random(args.seed)
    shuffled = list(candidates)
    rng.shuffle(shuffled)
    probe_limit = min(len(shuffled), max(args.api_sample_size, args.seed_probe_limit))
    eligible: list[Candidate] = []
    for cand in shuffled[:probe_limit]:
        endpoint = f"{args.api_url.rstrip('/')}/api/v1/maintenance/tuning/matches/{cand.node_id}"
        payload = _build_tuning_payload(
            args.request_type,
            _resolve_request_country(args, cand.country),
            probe_search_options,
        )
        try:
            data = _request_json(
                endpoint,
                "POST",
                payload,
                headers={"X-Maintenance-Key": args.maintenance_key},
                retries=args.request_retries,
                retry_delay_ms=args.request_retry_delay_ms,
            )
        except Exception:  # noqa: BLE001
            continue
        ranked_ids = _extract_ranked_ids(data, cand.node_id)
        if ranked_ids:
            eligible.append(cand)
        if len(eligible) >= max(args.api_sample_size, args.min_eligible_seeds):
            break
    if len(eligible) < args.min_eligible_seeds:
        raise RuntimeError(
            f"eligible seed users too low: got {len(eligible)}, require at least {args.min_eligible_seeds}"
        )
    if len(eligible) <= args.api_sample_size:
        return eligible
    return rng.sample(eligible, args.api_sample_size)


def _run_quality_once(
    args: argparse.Namespace,
    search_options: dict[str, Any] | None,
    seed_candidates: list[Candidate] | None = None,
    relevance_candidates: list[Candidate] | None = None,
    seed_sample: list[Candidate] | None = None,
) -> dict[str, Any]:
    seed_candidates = seed_candidates or _fetch_node_candidates(args.api_url, args.maintenance_key, "USER")
    if not seed_candidates:
        raise RuntimeError("No USER candidates available from maintenance API")
    relevance_node_type = _repository_node_type(args.request_type)
    relevance_candidates = relevance_candidates or _fetch_node_candidates(
        args.api_url,
        args.maintenance_key,
        relevance_node_type,
    )
    if not relevance_candidates:
        raise RuntimeError(f"No {relevance_node_type} candidates available from maintenance API")

    sample = seed_sample or _pick_seed_sample(
        args,
        seed_candidates,
        search_options if search_options is not None else None,
    )

    per_seed: list[dict[str, float]] = []
    skipped_seeds: list[str] = []
    for seed in sample:
        endpoint = f"{args.api_url.rstrip('/')}/api/v1/maintenance/tuning/matches/{seed.node_id}"
        payload = _build_tuning_payload(
            args.request_type,
            _resolve_request_country(args, seed.country),
            search_options,
        )
        try:
            data = _request_json(
                endpoint,
                "POST",
                payload,
                headers={"X-Maintenance-Key": args.maintenance_key},
                retries=args.request_retries,
                retry_delay_ms=args.request_retry_delay_ms,
            )
        except Exception:  # noqa: BLE001
            skipped_seeds.append(seed.node_id)
            continue
        ranked_ids = _extract_ranked_ids(data, seed.node_id)
        relevant_ids = {c.node_id for c in relevance_candidates if _relevance(seed, c, args.request_type)}
        if not relevant_ids:
            skipped_seeds.append(seed.node_id)
            continue
        per_seed.append(_metrics_from_ranked_ids(ranked_ids, relevant_ids))

    if not per_seed:
        agg = {
            "precision_at_10": 0.0,
            "precision_at_20": 0.0,
            "mrr": 0.0,
            "ndcg_at_10": 0.0,
            "hit_rate_at_10": 0.0,
        }
    else:
        keys = sorted(per_seed[0].keys())
        agg = {
            k: sum(x[k] for x in per_seed) / len(per_seed) for k in keys
        }

    return {
        "seed": args.seed,
        "api_url": args.api_url,
        "relevance_mode": args.relevance_mode,
        "request_type": args.request_type,
        "use_seed_country": bool(args.use_seed_country),
        "omit_search_options": search_options is None,
        "search_options": search_options,
        "seed_user_ids": [s.node_id for s in sample],
        "skipped_seed_user_ids": skipped_seeds,
        "sample_size": len(sample),
        "successful_seed_count": len(per_seed),
        "my_mesh_aggregates": {args.relevance_mode: agg},
    }


def extract_score(summary: dict[str, Any], relevance_mode: str) -> tuple[float, float, float]:
    my_mesh = summary.get("my_mesh_aggregates", {}) or {}
    mode_metrics = my_mesh.get(relevance_mode) or {}
    ndcg = float(mode_metrics.get("ndcg_at_10") or 0.0)
    p10 = float(mode_metrics.get("precision_at_10") or 0.0)
    mrr = float(mode_metrics.get("mrr") or 0.0)
    return ndcg, p10, mrr


def _evaluate_tune_candidate(
    args: argparse.Namespace,
    run_dir: Path,
    candidate: TuneCandidate,
    seed_candidates: list[Candidate],
    relevance_candidates: list[Candidate],
    seed_sample: list[Candidate],
) -> dict[str, Any]:
    output_json = run_dir / f"{candidate.name}.json"
    log_path = run_dir / f"{candidate.name}.log"
    exit_code = 2
    output_text = ""
    max_attempts = 2
    for attempt in range(1, max_attempts + 1):
        try:
            summary = _run_quality_once(
                args,
                candidate.search_options,
                seed_candidates=seed_candidates,
                relevance_candidates=relevance_candidates,
                seed_sample=seed_sample,
            )
            output_json.write_text(json.dumps(summary, ensure_ascii=True, indent=2) + "\n", encoding="utf-8")
            output_text = json.dumps(summary, ensure_ascii=True)
            exit_code = 0
            break
        except Exception as exc:  # noqa: BLE001
            output_text = str(exc)
            exit_code = 2
        print(f"[warn] eval attempt {attempt}/{max_attempts} failed for {candidate.name} (exit={exit_code})")
        if attempt < max_attempts:
            wait_for_health(args.api_url, args.health_timeout_seconds)

    log_path.write_text(output_text + "\n", encoding="utf-8")
    result: dict[str, Any] = {
        "variant": candidate.name,
        "source": candidate.source,
        "search_options": candidate.search_options,
        "log_path": str(log_path.relative_to(ROOT)),
    }
    if candidate.parent:
        result["parent"] = candidate.parent

    if exit_code != 0 or not output_json.exists():
        print(f"[warn] eval failed for {candidate.name} (exit={exit_code})")
        result["status"] = "eval_failed"
        result["exit_code"] = exit_code
        return result

    summary = json.loads(output_json.read_text(encoding="utf-8"))
    ndcg, p10, mrr = extract_score(summary, args.relevance_mode)
    print(f"[result] ndcg@10={ndcg:.3f} p@10={p10:.3f} mrr={mrr:.3f}")
    result.update(
        {
            "status": "ok",
            "summary_path": str(output_json.relative_to(ROOT)),
            "my_mesh": {
                "ndcg_at_10": ndcg,
                "precision_at_10": p10,
                "mrr": mrr,
                "precision_at_20": float(summary["my_mesh_aggregates"][args.relevance_mode].get("precision_at_20") or 0.0),
                "hit_rate_at_10": float(summary["my_mesh_aggregates"][args.relevance_mode].get("hit_rate_at_10") or 0.0),
            },
        }
    )
    return result


def _generate_initial_candidates(
    anchor_options: dict[str, Any],
    search_space: dict[str, list[Any]],
    option_keys: list[str],
    max_evals: int,
    rng: random.Random,
) -> tuple[list[TuneCandidate], int, str]:
    anchor = _candidate_from_values("candidate_000_anchor", anchor_options, source="anchor")
    space_size = _search_space_size(search_space, option_keys)
    if space_size <= max_evals:
        exhaustive = _enumerate_search_space(search_space, option_keys)
        candidates: list[TuneCandidate] = [anchor]
        seen = {_candidate_fingerprint(anchor.search_options)}
        for idx, values in enumerate(exhaustive, start=1):
            candidate = _candidate_from_values(f"candidate_{idx:03d}_grid", values, source="grid")
            fingerprint = _candidate_fingerprint(candidate.search_options)
            if fingerprint in seen:
                continue
            seen.add(fingerprint)
            candidates.append(candidate)
        return candidates[:max_evals], space_size, "exhaustive"

    exploration_budget = max(1, max_evals // 2)
    sampled = _sample_search_space(search_space, option_keys, exploration_budget, rng)
    candidates = [anchor]
    seen = {_candidate_fingerprint(anchor.search_options)}
    for idx, values in enumerate(sampled, start=1):
        candidate = _candidate_from_values(f"candidate_{idx:03d}_random", values, source="random")
        fingerprint = _candidate_fingerprint(candidate.search_options)
        if fingerprint in seen:
            continue
        seen.add(fingerprint)
        candidates.append(candidate)
    return candidates[:max_evals], space_size, "adaptive_random_refine"


def _neighbor_candidates(
    ranked_results: list[dict[str, Any]],
    search_space: dict[str, list[Any]],
    option_keys: list[str],
    seen_fingerprints: set[str],
    refine_top_k: int,
    round_idx: int,
) -> list[TuneCandidate]:
    out: list[TuneCandidate] = []
    for rank, row in enumerate(ranked_results[: max(0, refine_top_k)], start=1):
        base_options = row.get("search_options") or {}
        base_name = str(row.get("variant") or f"rank{rank}")
        for key in option_keys:
            values = search_space.get(key) or []
            if len(values) < 2:
                continue
            current_value = _canonicalize_option_value(base_options.get(key))
            try:
                current_index = values.index(current_value)
            except ValueError:
                continue
            for delta in (-1, 1):
                neighbor_index = current_index + delta
                if neighbor_index < 0 or neighbor_index >= len(values):
                    continue
                neighbor_options = dict(base_options)
                neighbor_options[key] = values[neighbor_index]
                fingerprint = _candidate_fingerprint(neighbor_options)
                if fingerprint in seen_fingerprints:
                    continue
                seen_fingerprints.add(fingerprint)
                direction = "down" if delta < 0 else "up"
                out.append(
                    _candidate_from_values(
                        f"candidate_refine_r{round_idx}_k{rank}_{key}_{direction}",
                        neighbor_options,
                        source="neighbor",
                        parent=base_name,
                    )
                )
    return out


def run_quality(args: argparse.Namespace) -> int:
    if not args.maintenance_key.strip():
        print("[error] --maintenance-key is required", file=sys.stderr)
        return 2
    search_options = _resolve_quality_search_options(args)
    if not wait_for_health(args.api_url, args.health_timeout_seconds):
        print("[error] API health check failed", file=sys.stderr)
        return 2
    summary = _run_quality_once(args, search_options)
    agg = summary["my_mesh_aggregates"][args.relevance_mode]
    print(f"[result] ndcg@10={agg['ndcg_at_10']:.3f} p@10={agg['precision_at_10']:.3f} mrr={agg['mrr']:.3f}")
    if args.output_json:
        out = Path(args.output_json)
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_text(json.dumps(summary, ensure_ascii=True, indent=2) + "\n", encoding="utf-8")
        print(f"[info] wrote summary JSON to {out}")
    return 0


def run_tune(args: argparse.Namespace) -> Path:
    if args.omit_search_options:
        raise RuntimeError("--omit-search-options is only supported with the quality command")
    RESULT_DIR.mkdir(parents=True, exist_ok=True)
    run_dir = RESULT_DIR / f"mymesh_tuning_seed{args.seed}_{int(time.time())}"
    run_dir.mkdir(parents=True, exist_ok=True)

    rng = random.Random(args.seed)
    results: list[dict[str, Any]] = []
    seed_candidates = _fetch_node_candidates(args.api_url, args.maintenance_key, "USER")
    if not seed_candidates:
        raise RuntimeError("No USER candidates available from maintenance API")
    relevance_node_type = _repository_node_type(args.request_type)
    relevance_candidates = _fetch_node_candidates(args.api_url, args.maintenance_key, relevance_node_type)
    if not relevance_candidates:
        raise RuntimeError(f"No {relevance_node_type} candidates available from maintenance API")

    seed_sample = _pick_seed_sample(args, seed_candidates, _default_search_options())
    anchor_options = _parse_search_options_json(args.search_options_json) or _default_search_options()
    override_search_space = _parse_search_space_json(args.search_space_json)
    search_space = _build_search_space(anchor_options, override_search_space)
    search_space, inactive_keys, signal_activity = _tailor_search_space_for_seed_sample(
        search_space,
        seed_sample,
        anchor_options,
        set(override_search_space.keys()),
        args.request_type,
    )
    option_keys = _option_key_order(search_space, anchor_options)
    initial_candidates, search_space_size, strategy = _generate_initial_candidates(
        anchor_options,
        search_space,
        option_keys,
        max(1, args.max_evals),
        rng,
    )
    print(f"[info] eligible_seed_sample={len(seed_sample)}")
    print(f"[info] strategy={strategy}")
    print(f"[info] search_space_size={search_space_size}")
    print(f"[info] max_evals={args.max_evals}")
    print(f"[info] initial_candidates={len(initial_candidates)}")
    if inactive_keys:
        print(f"[info] inactive_keys_pruned={','.join(inactive_keys)}")
    print(f"[info] output_dir={run_dir}")

    evaluated_fingerprints: set[str] = set()
    for candidate in initial_candidates:
        fingerprint = _candidate_fingerprint(candidate.search_options)
        if fingerprint in evaluated_fingerprints:
            continue
        evaluated_fingerprints.add(fingerprint)
        print(f"\n[candidate] {candidate.name} ({candidate.source})")
        results.append(
            _evaluate_tune_candidate(
                args,
                run_dir,
                candidate,
                seed_candidates,
                relevance_candidates,
                seed_sample,
            )
        )
        if len(results) >= args.max_evals:
            break

    if strategy != "exhaustive" and args.refine_top_k > 0:
        for round_idx in range(1, max(0, args.refine_rounds) + 1):
            if len(results) >= args.max_evals:
                break
            ranked = _sort_results(results)
            neighbors = _neighbor_candidates(
                ranked,
                search_space,
                option_keys,
                evaluated_fingerprints,
                args.refine_top_k,
                round_idx,
            )
            if not neighbors:
                break
            for candidate in neighbors:
                if len(results) >= args.max_evals:
                    break
                print(f"\n[candidate] {candidate.name} ({candidate.source})")
                results.append(
                    _evaluate_tune_candidate(
                        args,
                        run_dir,
                        candidate,
                        seed_candidates,
                        relevance_candidates,
                        seed_sample,
                    )
                )

    if strategy != "exhaustive" and len(results) < args.max_evals:
        remaining = args.max_evals - len(results)
        extra_candidates = _additional_random_candidates(
            search_space,
            option_keys,
            evaluated_fingerprints,
            remaining * 2,
            rng,
            start_index=len(results) + 1,
        )
        for candidate in extra_candidates:
            if len(results) >= args.max_evals:
                break
            print(f"\n[candidate] {candidate.name} ({candidate.source})")
            results.append(
                _evaluate_tune_candidate(
                    args,
                    run_dir,
                    candidate,
                    seed_candidates,
                    relevance_candidates,
                    seed_sample,
                )
            )

    successful = _sort_results(results)
    shortlist = successful[:3]

    summary_json = {
        "seed": args.seed,
        "relevance_mode": args.relevance_mode,
        "request_type": args.request_type,
        "strategy": strategy,
        "max_evals": args.max_evals,
        "search_space_size": search_space_size,
        "search_space": search_space,
        "anchor_search_options": anchor_options,
        "inactive_keys_pruned": inactive_keys,
        "seed_signal_activity": signal_activity,
        "results": results,
        "shortlist_top3": shortlist,
    }
    summary_json_path = run_dir / "summary.json"
    summary_json_path.write_text(json.dumps(summary_json, ensure_ascii=True, indent=2) + "\n", encoding="utf-8")

    md_lines = [
        "# MyMesh tuning summary",
        "",
        f"- seed: `{args.seed}`",
        f"- relevance_mode: `{args.relevance_mode}`",
        f"- request_type: `{args.request_type}`",
        f"- strategy: `{strategy}`",
        f"- max_evals: `{args.max_evals}`",
        f"- search_space_size: `{search_space_size}`",
    ]
    if inactive_keys:
        md_lines.append(f"- inactive_keys_pruned: `{', '.join(inactive_keys)}`")
    md_lines.extend([
        "",
        "## Seed signal activity",
    ])
    for key in sorted(signal_activity.keys()):
        md_lines.append(f"- `{key}`: active for `{signal_activity[key]}` seeds")
    md_lines.extend([
        "",
        "## Top 3 configurations",
    ])
    if not shortlist:
        md_lines.append("- No successful runs.")
    else:
        for idx, row in enumerate(shortlist, start=1):
            m = row["my_mesh"]
            md_lines.append(
                f"- {idx}. `{row['variant']}`: NDCG@10={m['ndcg_at_10']:.3f}, "
                f"P@10={m['precision_at_10']:.3f}, MRR={m['mrr']:.3f}"
            )
    md_lines.extend(["", "## All runs"])
    for row in results:
        if row.get("status") == "ok":
            m = row["my_mesh"]
            md_lines.append(
                f"- `{row['variant']}` OK: NDCG@10={m['ndcg_at_10']:.3f}, "
                f"P@10={m['precision_at_10']:.3f}, MRR={m['mrr']:.3f}"
            )
        else:
            md_lines.append(f"- `{row['variant']}` {row.get('status')}")

    summary_md_path = run_dir / "summary.md"
    summary_md_path.write_text("\n".join(md_lines) + "\n", encoding="utf-8")

    print(f"\n[done] summary_json={summary_json_path}")
    print(f"[done] summary_md={summary_md_path}")
    return summary_json_path


def run_gate(summary_json_path: Path, min_ndcg_at_10: float, min_p10: float, min_mrr: float) -> int:
    if not summary_json_path.exists():
        print(f"[error] summary file not found: {summary_json_path}", file=sys.stderr)
        return 2

    payload = json.loads(summary_json_path.read_text(encoding="utf-8"))
    shortlist = payload.get("shortlist_top3") or []
    if not shortlist:
        print("[error] shortlist_top3 is empty", file=sys.stderr)
        return 2

    best = shortlist[0]
    metrics = best.get("my_mesh") or {}
    ndcg = float(metrics.get("ndcg_at_10") or 0.0)
    p10 = float(metrics.get("precision_at_10") or 0.0)
    mrr = float(metrics.get("mrr") or 0.0)

    print(f"[info] best_variant={best.get('variant')}")
    print(f"[info] ndcg_at_10={ndcg:.3f} p10={p10:.3f} mrr={mrr:.3f}")

    failures: list[str] = []
    if ndcg < min_ndcg_at_10:
        failures.append(f"ndcg_at_10<{min_ndcg_at_10:.3f}")
    if p10 < min_p10:
        failures.append(f"precision_at_10<{min_p10:.3f}")
    if mrr < min_mrr:
        failures.append(f"mrr<{min_mrr:.3f}")

    if failures:
        print("[error] regression gate failed: " + ", ".join(failures), file=sys.stderr)
        return 1

    print("[ok] regression gate passed")
    return 0


def main() -> int:
    args = parse_args()
    if args.command == "quality":
        return run_quality(args)

    if args.command == "tune":
        run_tune(args)
        return 0

    if args.command == "gate":
        return run_gate(
            Path(args.summary_json),
            args.min_ndcg_at_10,
            args.min_p10,
            args.min_mrr,
        )

    if args.command == "tune-and-gate":
        summary_json_path = run_tune(args)
        return run_gate(
            summary_json_path,
            args.min_ndcg_at_10,
            args.min_p10,
            args.min_mrr,
        )

    print(f"[error] unknown command: {args.command}", file=sys.stderr)
    return 2


if __name__ == "__main__":
    raise SystemExit(main())
