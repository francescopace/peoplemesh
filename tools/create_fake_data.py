#!/usr/bin/env python3
"""
Build a realistic company dataset from public sources and generate dev seed SQL files.

Public sources used:
- Stack Overflow Developer Survey 2025 (skills, technologies, roles, tools by industry)
- randomuser.me API (synthetic-but-public user identities)
"""

from __future__ import annotations

import argparse
import csv
import json
import random
import re
import sys
import textwrap
import uuid
from dataclasses import dataclass, field
from pathlib import Path
from time import perf_counter, sleep
from typing import Any
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode
from urllib.request import Request, urlopen

RANDOMUSER_URL = "https://randomuser.me/api/"
UUID_NS = uuid.UUID("0b651c55-f445-4dcf-86e1-806cf2f59b1b")
TARGET_MESH_VECTOR_DIM = 1024

SO_SURVEY_CSV = Path(__file__).parent / "stack-overflow/survey_results_public.csv"

SO_TECH_COLUMNS = [
    "LanguageHaveWorkedWith",
    "DatabaseHaveWorkedWith",
    "PlatformHaveWorkedWith",
    "WebframeHaveWorkedWith",
]

COMPANY_TYPE_INDUSTRIES = {
    "it": [
        "Software Development",
        "Internet, Telecomm or Information Services",
        "Computer Systems Design and Services",
    ],
    "finance": [
        "Fintech",
        "Banking/Financial Services",
        "Insurance",
    ],
    "public_administration": [
        "Government",
    ],
    "education": [
        "Higher Education",
    ],
    "healthcare": [
        "Healthcare",
    ],
    "manufacturing": [
        "Manufacturing",
    ],
    "energy": [
        "Energy",
    ],
    "logistics": [
        "Transportation, or Supply Chain",
    ],
    "retail": [
        "Retail and Consumer Services",
    ],
    "media": [
        "Media & Advertising Services",
    ],
}

SYSTEM_OWNER_NODE_ID = "d0000000-0000-0000-0000-000000000011"
SYSTEM_OWNER_IDENTITY_ID = "d1000000-0000-0000-0000-000000000011"
SO_SKILL_CATALOG_ID = "d3000000-0000-0000-0000-000000000002"

SO_CATEGORY_MAP = {
    "LanguageHaveWorkedWith": "Language",
    "DatabaseHaveWorkedWith": "Database",
    "PlatformHaveWorkedWith": "Platform & Infrastructure",
    "WebframeHaveWorkedWith": "Framework & Library",
    "DevEnvsHaveWorkedWith": "Developer Tool",
    "OfficeStackAsyncHaveWorkedWith": "Collaboration Tool",
    "AIModelsHaveWorkedWith": "AI Model",
    "CommPlatformHaveWorkedWith": "Community Platform",
    "SOTagsHaveWorkedWith": "Emerging Technology",
    "OpSysProfessional use": "Operating System",
}


COUNTRY_FROM_NAT = {
    "US": "US",
    "GB": "GB",
    "FR": "FR",
    "DE": "DE",
    "IT": "IT",
    "ES": "ES",
    "IN": "IN",
    "BR": "BR",
    "NL": "NL",
    "DK": "DK",
    "NO": "NO",
    "SE": "SE",
    "FI": "FI",
}

DEFAULT_SOFT_SKILLS = [
    "Communication",
    "Teamwork",
    "Problem solving",
    "Stakeholder management",
    "Mentoring",
    "Adaptability",
    "Time management",
    "Critical thinking",
]

DEFAULT_HOBBIES = [
    "reading",
    "photography",
    "cooking",
    "hiking",
    "travel",
    "gaming",
    "music",
    "chess",
]

DEFAULT_SPORTS = [
    "running",
    "cycling",
    "swimming",
    "tennis",
    "yoga",
    "football",
]

DEFAULT_PROJECT_TYPES = [
    "platform modernization",
    "cloud migration",
    "internal tooling",
    "customer delivery",
    "security hardening",
    "AI/ML integration",
    "API design",
    "data pipeline",
    "infrastructure automation",
    "developer experience",
]

DEFAULT_CAUSES = ["open source", "digital inclusion", "sustainability", "education"]
DEFAULT_PERSONALITY_TAGS = ["curious", "pragmatic", "collaborative", "analytical"]
DEFAULT_BOOK_GENRES = ["technology", "business", "fiction", "history"]
DEFAULT_MUSIC_GENRES = ["rock", "jazz", "electronic", "classical"]

NAT_LANGUAGE_HINTS = {
    "US": ["English"],
    "GB": ["English"],
    "FR": ["French", "English"],
    "DE": ["German", "English"],
    "IT": ["Italian", "English"],
    "ES": ["Spanish", "English"],
    "IN": ["English", "Hindi"],
    "BR": ["Portuguese", "English"],
    "NL": ["Dutch", "English"],
    "SE": ["Swedish", "English"],
}

@dataclass
class Skill:
    name: str
    category: str


def _ranked(counts: dict[str, int]) -> list[str]:
    return [name for name, _ in sorted(counts.items(), key=lambda kv: kv[1], reverse=True)]


def _counted(counts: dict[str, int]) -> list[tuple[str, int]]:
    return sorted(counts.items(), key=lambda kv: kv[1], reverse=True)


@dataclass
class SurveyProfile:
    skills: list[Skill] = field(default_factory=list)
    occupations: list[str] = field(default_factory=list)
    dev_tools: list[str] = field(default_factory=list)
    collab_tools: list[str] = field(default_factory=list)
    ai_models: list[str] = field(default_factory=list)
    learn_methods: list[str] = field(default_factory=list)
    community_platforms: list[str] = field(default_factory=list)
    os_professional: list[str] = field(default_factory=list)
    so_tags: list[str] = field(default_factory=list)
    education_weighted: list[tuple[str, int]] = field(default_factory=list)
    remote_work_weighted: list[tuple[str, int]] = field(default_factory=list)
    org_size_weighted: list[tuple[str, int]] = field(default_factory=list)
    respondent_count: int = 0


REMOTE_WORK_MAP = {
    "Remote": "REMOTE",
    "Hybrid (some remote, leans heavy to in-person)": "HYBRID",
    "Hybrid (some in-person, leans heavy to flexibility)": "HYBRID",
    "In-person": "OFFICE",
    "Your choice (very flexible, you can come in when you want or just as needed)": "FLEXIBLE",
}


def load_survey_profile(company_type: str) -> SurveyProfile:
    industries = set(COMPANY_TYPE_INDUSTRIES[company_type])

    tech_counts: dict[str, int] = {}
    role_counts: dict[str, int] = {}
    devenv_counts: dict[str, int] = {}
    collab_counts: dict[str, int] = {}
    ai_counts: dict[str, int] = {}
    learn_counts: dict[str, int] = {}
    comm_counts: dict[str, int] = {}
    os_counts: dict[str, int] = {}
    sotag_counts: dict[str, int] = {}
    edu_counts: dict[str, int] = {}
    remote_counts: dict[str, int] = {}
    org_counts: dict[str, int] = {}
    respondent_count = 0

    col_map = {
        "DevType": role_counts,
        "DevEnvsHaveWorkedWith": devenv_counts,
        "OfficeStackAsyncHaveWorkedWith": collab_counts,
        "AIModelsHaveWorkedWith": ai_counts,
        "LearnCode": learn_counts,
        "CommPlatformHaveWorkedWith": comm_counts,
        "OpSysProfessional use": os_counts,
        "SOTagsHaveWorkedWith": sotag_counts,
    }

    with open(SO_SURVEY_CSV, encoding="utf-8") as f:
        for row in csv.DictReader(f):
            industry = (row.get("Industry") or "").strip()
            if industry not in industries:
                continue
            respondent_count += 1

            for col in SO_TECH_COLUMNS:
                val = row.get(col, "")
                if val and val != "NA":
                    for item in val.split(";"):
                        item = item.strip()
                        if item:
                            tech_counts[item] = tech_counts.get(item, 0) + 1

            for col, target in col_map.items():
                val = row.get(col, "")
                if val and val != "NA":
                    for item in val.split(";"):
                        item = item.strip()
                        if item and not item.startswith("Other"):
                            target[item] = target.get(item, 0) + 1

            edu_val = (row.get("EdLevel") or "").strip()
            if edu_val and edu_val != "NA" and not edu_val.startswith("Other"):
                edu_counts[edu_val] = edu_counts.get(edu_val, 0) + 1

            remote_val = (row.get("RemoteWork") or "").strip()
            if remote_val and remote_val != "NA":
                mapped = REMOTE_WORK_MAP.get(remote_val)
                if mapped:
                    remote_counts[mapped] = remote_counts.get(mapped, 0) + 1

            org_val = (row.get("OrgSize") or "").strip()
            if org_val and org_val != "NA" and org_val != "I don't know":
                org_counts[org_val] = org_counts.get(org_val, 0) + 1

    return SurveyProfile(
        skills=[Skill(name=n, category=pick_skill_category(n)) for n in _ranked(tech_counts)],
        occupations=_ranked(role_counts),
        dev_tools=_ranked(devenv_counts),
        collab_tools=_ranked(collab_counts),
        ai_models=_ranked(ai_counts),
        learn_methods=_ranked(learn_counts),
        community_platforms=_ranked(comm_counts),
        os_professional=_ranked(os_counts),
        so_tags=_ranked(sotag_counts),
        education_weighted=_counted(edu_counts),
        remote_work_weighted=_counted(remote_counts),
        org_size_weighted=_counted(org_counts),
        respondent_count=respondent_count,
    )


def fetch_json(url: str, timeout_seconds: int = 30) -> dict[str, Any]:
    req = Request(url, headers={"User-Agent": "peoplemesh-public-ingest/1.0"})
    with urlopen(req, timeout=timeout_seconds) as resp:
        charset = resp.headers.get_content_charset() or "utf-8"
        body = resp.read().decode(charset)
    return json.loads(body)


def post_json(url: str, payload: dict[str, Any], timeout_seconds: int = 120) -> dict[str, Any]:
    body = json.dumps(payload).encode("utf-8")
    req = Request(
        url,
        data=body,
        method="POST",
        headers={"User-Agent": "peoplemesh-public-ingest/1.0", "Content-Type": "application/json"},
    )
    with urlopen(req, timeout=timeout_seconds) as resp:
        charset = resp.headers.get_content_charset() or "utf-8"
        out = resp.read().decode(charset)
    return json.loads(out)


def sql_quote(value: str) -> str:
    return "'" + value.replace("'", "''") + "'"


def sql_nullable(value: str | None) -> str:
    return "NULL" if value is None else sql_quote(value)


def sql_text_array(values: list[str]) -> str:
    escaped = ",".join(sql_quote(v) for v in values)
    return f"ARRAY[{escaped}]"


def sql_jsonb(value: dict[str, Any]) -> str:
    return sql_quote(json.dumps(value, ensure_ascii=True)) + "::jsonb"


def sql_embedding(embedding: str | None) -> str:
    if not embedding:
        return "NULL"
    return sql_quote(embedding) + f"::vector({TARGET_MESH_VECTOR_DIM})"


def pick_skill_category(name: str) -> str:
    lowered = name.lower()
    if lowered in {
        "python", "java", "c#", "c++", "c", "go", "rust", "javascript",
        "typescript", "kotlin", "swift", "dart", "ruby", "php", "lua",
        "assembly", "r", "scala", "perl", "haskell", "elixir", "clojure",
        "bash/shell (all shells)", "powershell",
    }:
        return "languages"
    if any(k in lowered for k in (
        "kubernetes", "docker", "podman", "ansible", "terraform",
        "make", "gradle", "maven", "cargo", "npm", "pip", "yarn",
        "pnpm", "nuget", "apt", "homebrew", "chocolatey", "pacman",
        "composer", "msbuild", "poetry", "vite", "webpack",
    )):
        return "devops"
    if any(k in lowered for k in (
        "aws", "amazon", "azure", "google cloud", "cloudflare",
        "digital ocean", "vercel", "firebase", "supabase",
        "heroku", "oracle cloud", "openshift",
    )):
        return "cloud"
    if any(k in lowered for k in ("security", "oauth", "auth", "encryption", "jwt")):
        return "security"
    if any(k in lowered for k in (
        "sql", "postgres", "mysql", "mariadb", "mongodb", "redis",
        "sqlite", "dynamodb", "elasticsearch", "oracle", "microsoft sql",
        "cloud firestore", "cassandra", "neo4j", "couchdb", "influxdb",
    )):
        return "data"
    if any(k in lowered for k in (
        "react", "angular", "vue", "svelte", "next.js", "nuxt",
        "jquery", "html", "css", "tailwind", "bootstrap",
        "wordpress", "django", "flask", "fastapi", "express",
        "spring boot", "asp.net", "laravel", "rails",
    )):
        return "frontend"
    if any(k in lowered for k in ("prometheus", "datadog", "grafana")):
        return "observability"
    return "general"


def collect_public_users(limit: int) -> list[dict[str, Any]]:
    params = urlencode({"results": str(limit), "nat": "us,gb,fr,de,it,es,in,br,nl,se"})
    payload = fetch_json(f"{RANDOMUSER_URL}?{params}")
    return payload.get("results", [])


def derive_seniority(age: int | None, index: int) -> str:
    if age is None:
        return ["JUNIOR", "MID", "SENIOR", "LEAD"][index % 4]
    if age <= 30:
        return "JUNIOR"
    if age <= 40:
        return "MID"
    if age <= 50:
        return "SENIOR"
    return "LEAD"


def split_skill_pool(skills: list[Skill]) -> tuple[list[str], list[str]]:
    hard_categories = {"languages", "devops", "cloud", "security", "data", "frontend", "observability", "general"}
    hard = [s.name for s in skills if s.category in hard_categories]
    soft = [s.name for s in skills if s.category not in hard_categories]
    return hard, soft


def safe_sample(rng: random.Random, values: list[str], k: int) -> list[str]:
    if not values:
        return []
    return rng.sample(values, k=min(k, len(values)))


def weighted_choice(rng: random.Random, options: list[tuple[str, int]]) -> str:
    population = [v for v, _ in options]
    weights = [w for _, w in options]
    return rng.choices(population, weights=weights, k=1)[0]


def normalize_handle(seed: str) -> str:
    cleaned = re.sub(r"[^a-z0-9_]", "_", (seed or "").lower())
    cleaned = re.sub(r"_+", "_", cleaned).strip("_")
    return cleaned or "peoplemesh"


def normalize_phone(raw_phone: str | None, fallback_index: int) -> str:
    if raw_phone:
        digits = "".join(ch for ch in raw_phone if ch.isdigit())
        if digits:
            return "+" + digits
    return f"+39000{fallback_index:06d}"


def build_user_records(
    raw_users: list[dict[str, Any]],
    survey: SurveyProfile,
    company_name: str,
    rng: random.Random,
) -> list[dict[str, Any]]:
    hard_pool, soft_pool = split_skill_pool(survey.skills)
    if not hard_pool:
        hard_pool = [s.name for s in survey.skills]
    if not soft_pool:
        soft_pool = DEFAULT_SOFT_SKILLS

    out: list[dict[str, Any]] = []
    seen_user_ids: set[str] = set()
    for idx, item in enumerate(raw_users, start=1):
        first = item.get("name", {}).get("first", "Dev")
        last = item.get("name", {}).get("last", f"User{idx}")
        email = item.get("email", f"dev.user.{idx}@example.local").lower()
        nat = item.get("nat", "US")
        country = COUNTRY_FROM_NAT.get(nat, "US")
        location = item.get("location", {}) if isinstance(item.get("location"), dict) else {}
        city = str(location.get("city", "Unknown"))
        tz = str(location.get("timezone", {}).get("description", "UTC")) if isinstance(location.get("timezone"), dict) else "UTC"
        role = survey.occupations[(idx - 1) % len(survey.occupations)]
        age_raw = item.get("dob", {}).get("age")
        age = age_raw if isinstance(age_raw, int) else None
        seniority = derive_seniority(age, idx)
        professional_skills = safe_sample(rng, hard_pool, rng.randint(4, 8))
        soft_skills = safe_sample(rng, soft_pool, rng.randint(2, 5))
        dev_tools = safe_sample(rng, survey.dev_tools, rng.randint(2, 4))
        collab_tools = safe_sample(rng, survey.collab_tools, rng.randint(2, 4))
        ai_models = safe_sample(rng, survey.ai_models, rng.randint(1, 3))
        learn_methods = safe_sample(rng, survey.learn_methods, rng.randint(2, 4))
        community = safe_sample(rng, survey.community_platforms, rng.randint(2, 4))
        os_used = safe_sample(rng, survey.os_professional, rng.randint(1, 2))
        learning_areas = safe_sample(rng, survey.so_tags, rng.randint(1, 3)) if survey.so_tags else []
        hobbies = safe_sample(rng, DEFAULT_HOBBIES, rng.randint(2, 4))
        sports = safe_sample(rng, DEFAULT_SPORTS, rng.randint(1, 3))
        education = weighted_choice(rng, survey.education_weighted) if survey.education_weighted else "Bachelor's degree"
        org_size = weighted_choice(rng, survey.org_size_weighted) if survey.org_size_weighted else "100 to 499 employees"
        causes = safe_sample(rng, DEFAULT_CAUSES, 2)
        personality_tags = safe_sample(rng, DEFAULT_PERSONALITY_TAGS, 2)
        project_types = safe_sample(rng, DEFAULT_PROJECT_TYPES, rng.randint(2, 3))
        topics_frequent = safe_sample(rng, professional_skills, rng.randint(2, 3))
        if not topics_frequent:
            topics_frequent = [role]
        languages = NAT_LANGUAGE_HINTS.get(nat, ["English"])
        work_mode = weighted_choice(rng, survey.remote_work_weighted) if survey.remote_work_weighted else "HYBRID"
        employment_type = "EMPLOYED"
        avatar_url = str(item.get("picture", {}).get("large", "")) if isinstance(item.get("picture"), dict) else ""
        dob_raw = item.get("dob", {}).get("date") if isinstance(item.get("dob"), dict) else None
        birth_date = str(dob_raw)[:10] if dob_raw else None
        cell_raw = item.get("cell")
        phone = normalize_phone(str(cell_raw) if cell_raw else None, idx)
        base_handle = normalize_handle(email.split("@", 1)[0])
        slack_handle = f"@{base_handle}"
        telegram_handle = f"@{normalize_handle(f'{first}_{last}')}"
        linkedin_url = f"https://www.linkedin.com/in/{base_handle}"
        uid = str(uuid.uuid5(UUID_NS, f"user:{email}"))
        if uid in seen_user_ids:
            continue
        seen_user_ids.add(uid)
        out.append(
            {
                "id": uid,
                "title": f"{first} {last}",
                "description": f"{role} at {company_name}.",
                "tags": professional_skills[:4] if professional_skills else ["software"],
                "country": country,
                "external_id": f"public-user-{idx:04d}",
                "structured_data": {
                    "email": email,
                    "first_name": first,
                    "last_name": last,
                    "birth_date": birth_date,
                    "company": company_name,
                    "role": role,
                    "seniority": seniority,
                    "skills_professional": professional_skills,
                    "tools_and_tech": dev_tools,
                    "collab_tools": collab_tools,
                    "ai_models": ai_models,
                    "community_platforms": community,
                    "os_professional": os_used,
                    "skills_soft": soft_skills,
                    "languages_spoken": languages,
                    "work_mode": work_mode,
                    "employment_type": employment_type,
                    "org_size": org_size,
                    "topics_frequent": topics_frequent,
                    "learning_areas": learning_areas,
                    "learn_methods": learn_methods,
                    "project_types": project_types,
                    "hobbies": hobbies,
                    "sports": sports,
                    "education": education,
                    "causes": causes,
                    "personality_tags": personality_tags,
                    "book_genres": safe_sample(rng, DEFAULT_BOOK_GENRES, 2),
                    "music_genres": safe_sample(rng, DEFAULT_MUSIC_GENRES, 2),
                    "slack_handle": slack_handle,
                    "telegram_handle": telegram_handle,
                    "mobile_phone": phone,
                    "linkedin_url": linkedin_url,
                    "city": city,
                    "timezone": tz,
                    "avatar_url": avatar_url,
                    "field_provenance": {
                        "identity.display_name": "randomuser",
                        "identity.first_name": "randomuser",
                        "identity.last_name": "randomuser",
                        "identity.email": "randomuser",
                        "identity.photo_url": "randomuser",
                        "identity.birth_date": "randomuser",
                        "identity.company": "synthetic",
                        "professional.roles": "so-survey-2025",
                        "professional.skills_technical": "so-survey-2025",
                        "professional.skills_soft": "synthetic",
                        "professional.tools_and_tech": "so-survey-2025",
                        "professional.collab_tools": "so-survey-2025",
                        "professional.ai_models": "so-survey-2025",
                        "professional.community_platforms": "so-survey-2025",
                        "professional.os_professional": "so-survey-2025",
                        "professional.languages_spoken": "randomuser",
                        "professional.work_mode_preference": "so-survey-2025",
                        "professional.employment_type": "synthetic",
                        "professional.org_size": "so-survey-2025",
                        "contacts.slack_handle": "synthetic",
                        "contacts.telegram_handle": "synthetic",
                        "contacts.mobile_phone": "randomuser",
                        "contacts.linkedin_url": "synthetic",
                        "interests_professional.topics_frequent": "so-survey-2025",
                        "interests_professional.learning_areas": "so-survey-2025",
                        "interests_professional.learn_methods": "so-survey-2025",
                        "interests_professional.project_types": "synthetic",
                        "personal.hobbies": "synthetic",
                        "personal.sports": "synthetic",
                        "personal.education": "so-survey-2025",
                        "personal.causes": "synthetic",
                        "personal.personality_tags": "synthetic",
                        "personal.music_genres": "synthetic",
                        "personal.book_genres": "synthetic",
                        "geography.country": "randomuser",
                        "geography.city": "randomuser",
                        "geography.timezone": "randomuser",
                    },
                    "profile_version": "2.0",
                    "source": "randomuser.me + so-survey-2025",
                },
            }
        )
    return out


def build_group_records(survey: SurveyProfile, company_name: str, count: int, rng: random.Random) -> list[dict[str, Any]]:
    topics = [s.name for s in survey.skills] + survey.so_tags
    seen: set[str] = set()
    unique_topics: list[str] = []
    for t in topics:
        low = t.lower()
        if low not in seen:
            seen.add(low)
            unique_topics.append(t)

    out: list[dict[str, Any]] = []
    for idx in range(1, count + 1):
        topic = unique_topics[(idx - 1) % len(unique_topics)] if unique_topics else f"topic-{idx}"
        node_type = "COMMUNITY" if idx % 3 else "EVENT"
        gid = str(uuid.uuid5(UUID_NS, f"group:{idx}:{topic}"))
        out.append(
            {
                "id": gid,
                "node_type": node_type,
                "title": f"{topic} {'Guild' if node_type == 'COMMUNITY' else 'Summit'} #{idx:03d}",
                "description": f"{company_name} internal {node_type.lower()} focused on {topic}.",
                "tags": [topic.lower(), survey.skills[0].category if survey.skills else "tech", "community"],
                "country": ["US", "DE", "IT", "FR", "IN", "GB"][idx % 6],
                "structured_data": {
                    "topic": topic,
                    "member_count": 40 + ((idx * 29) % 1400),
                    "source": "so-survey-2025",
                },
            }
        )
    return out


def build_job_records(survey: SurveyProfile, company_name: str, count: int, rng: random.Random) -> list[dict[str, Any]]:
    skill_pool = [s.name for s in survey.skills] or ["Python", "JavaScript", "SQL"]
    seniority_labels = ["Junior", "Mid-Level", "Senior", "Staff", "Lead"]

    out: list[dict[str, Any]] = []
    for idx in range(1, count + 1):
        role = survey.occupations[(idx - 1) % len(survey.occupations)]
        seniority = seniority_labels[(idx - 1) % len(seniority_labels)]
        title = f"{seniority} {role}"
        required = safe_sample(rng, skill_pool, rng.randint(3, 6))
        tools = safe_sample(rng, survey.dev_tools, rng.randint(1, 3))
        work_mode = weighted_choice(rng, survey.remote_work_weighted) if survey.remote_work_weighted else "HYBRID"
        desc_skills = ", ".join(required[:4])
        desc_tools = ", ".join(tools) if tools else "modern tooling"
        external_id = f"so-job-{idx:05d}"
        out.append(
            {
                "external_id": external_id,
                "title": title,
                "description": f"{title} at {company_name}.",
                "requirements_text": f"{company_name} is looking for a {seniority.lower()} {role.lower()} "
                f"with experience in {desc_skills}. "
                f"Tools: {desc_tools}.",
                "skills_required": required,
                "work_mode": work_mode,
                "employment_type": ["EMPLOYED", "FREELANCE"][idx % 2],
                "country": ["US", "DE", "IT", "FR", "GB", "IN"][idx % 6],
                "status": "published",
                "external_url": "",
            }
        )
    return out


def build_embedding_text(
    title: str,
    description: str,
    tags: list[str],
    structured_data: dict[str, Any],
    max_chars: int | None = None,
) -> str:
    parts = [title.strip(), description.strip()]
    if tags:
        parts.append("tags: " + ", ".join(tags))
    if structured_data:
        compact = json.dumps(structured_data, ensure_ascii=True, separators=(",", ":"))
        parts.append("metadata: " + compact)
    text = "\n".join(p for p in parts if p)
    if max_chars is not None and max_chars > 0 and len(text) > max_chars:
        return text[:max_chars]
    return text


def format_embedding_vector(values: list[float]) -> str:
    return "[" + ",".join(f"{float(v):.8f}" for v in values) + "]"


def normalize_vector_dimensions(values: list[float], target_dim: int) -> list[float]:
    if len(values) == target_dim:
        return values
    if len(values) > target_dim:
        return values[:target_dim]
    return values + [0.0] * (target_dim - len(values))


def generate_embeddings_with_ollama(
    users: list[dict[str, Any]],
    groups: list[dict[str, Any]],
    jobs: list[dict[str, Any]],
    ollama_base_url: str,
    ollama_model: str,
) -> None:
    endpoint = ollama_base_url.rstrip("/") + "/api/embeddings"

    if ollama_model.startswith("granite-embedding:"):
        prompt_limits = [1200, 900, 700, 500, 350]
    else:
        prompt_limits = [3000, 2000, 1400, 1000, 700]

    def should_retry_status(status_code: int) -> bool:
        return status_code in {429, 500, 502, 503, 504}

    def embed_record(title: str, description: str, tags: list[str], structured_data: dict[str, Any]) -> str | None:
        for limit in prompt_limits:
            prompt = build_embedding_text(title, description, tags, structured_data, max_chars=limit)
            payload = {"model": ollama_model, "prompt": prompt}

            for attempt in range(3):
                try:
                    response = post_json(endpoint, payload, timeout_seconds=120)
                    vector = response.get("embedding")
                    if isinstance(vector, list) and vector:
                        normalized = normalize_vector_dimensions(vector, TARGET_MESH_VECTOR_DIM)
                        return format_embedding_vector(normalized)
                    break
                except HTTPError as exc:
                    if should_retry_status(exc.code) and attempt < 2:
                        sleep(0.25 * (2**attempt))
                        continue
                    break
                except URLError:
                    if attempt < 2:
                        sleep(0.25 * (2**attempt))
                        continue
                    break

        return None

    for u in users:
        u["embedding"] = embed_record(u["title"], u["description"], u["tags"], u["structured_data"])
    for g in groups:
        g["embedding"] = embed_record(g["title"], g["description"], g["tags"], g["structured_data"])
    for j in jobs:
        sd = {
            "external_id": j["external_id"],
            "requirements_text": j["requirements_text"],
            "skills_required": j["skills_required"],
            "work_mode": j["work_mode"],
            "employment_type": j["employment_type"],
            "external_url": j["external_url"],
        }
        j["embedding"] = embed_record(j["title"], j["description"], j["skills_required"], sd)


def write_seed_users_sql(path: Path, users: list[dict[str, Any]]) -> None:
    header = textwrap.dedent(
        f"""\
        -- Dev-only repeatable seed data for quick matching tests.
        -- Loaded via %dev Flyway location: classpath:db/dev
        -- Source: randomuser.me + Stack Overflow Developer Survey 2025

        INSERT INTO mesh.mesh_node (id, created_by, node_type, title, description, searchable, created_at, updated_at)
        VALUES ({sql_quote(SYSTEM_OWNER_NODE_ID)}, {sql_quote(SYSTEM_OWNER_NODE_ID)}, 'USER', 'System: dev-seed-owner', 'System owner for synthetic dev seeds.', false, now(), now())
        ON CONFLICT (id) DO UPDATE SET searchable = false, updated_at = now();

        INSERT INTO identity.user_identity (id, oauth_provider, oauth_subject, node_id, is_admin)
        VALUES ({sql_quote(SYSTEM_OWNER_IDENTITY_ID)}, 'dev-seed', 'system-owner', {sql_quote(SYSTEM_OWNER_NODE_ID)}, false)
        ON CONFLICT (id) DO NOTHING;

        DELETE FROM mesh.mesh_node
        WHERE created_by = {sql_quote(SYSTEM_OWNER_NODE_ID)} AND node_type = 'USER' AND id <> {sql_quote(SYSTEM_OWNER_NODE_ID)};
        """
    )
    values: list[str] = []
    for u in users:
        values.append(
            "("
            f"{sql_quote(u['id'])}, {sql_quote(u['id'])}, 'USER', {sql_quote(u['title'])}, {sql_quote(u['description'])}, "
            f"{sql_text_array(u['tags'])}, {sql_jsonb(u['structured_data'])}, {sql_quote(u['country'])}, true, "
            f"{sql_quote(u['external_id'])}, {sql_embedding(u.get('embedding'))}, now(), now()"
            ")"
        )
    body = (
        "INSERT INTO mesh.mesh_node (id, created_by, node_type, title, description, tags, structured_data, country, searchable, external_id, embedding, created_at, updated_at)\nVALUES\n"
        + ",\n".join(values)
        + ";\n"
    )
    path.write_text(header + "\n" + body, encoding="utf-8")


def write_seed_groups_sql(path: Path, groups: list[dict[str, Any]]) -> None:
    header = textwrap.dedent(
        f"""\
        -- Dev-only repeatable seed data for quick matching tests.
        -- Loaded via %dev Flyway location: classpath:db/dev
        -- Source: Stack Overflow survey + job tags derived communities/events

        INSERT INTO mesh.mesh_node (id, created_by, node_type, title, description, searchable, created_at, updated_at)
        VALUES ({sql_quote(SYSTEM_OWNER_NODE_ID)}, {sql_quote(SYSTEM_OWNER_NODE_ID)}, 'USER', 'System: dev-seed-owner', 'System owner for synthetic dev seeds.', false, now(), now())
        ON CONFLICT (id) DO UPDATE SET searchable = false, updated_at = now();

        INSERT INTO identity.user_identity (id, oauth_provider, oauth_subject, node_id, is_admin)
        VALUES ({sql_quote(SYSTEM_OWNER_IDENTITY_ID)}, 'dev-seed', 'system-owner', {sql_quote(SYSTEM_OWNER_NODE_ID)}, false)
        ON CONFLICT (id) DO NOTHING;

        DELETE FROM mesh.mesh_node
        WHERE created_by = {sql_quote(SYSTEM_OWNER_NODE_ID)} AND node_type IN ('COMMUNITY', 'EVENT', 'INTEREST_GROUP');
        """
    )
    values: list[str] = []
    for g in groups:
        values.append(
            "("
            f"{sql_quote(g['id'])}, {sql_quote(SYSTEM_OWNER_NODE_ID)}, {sql_quote(g['node_type'])}, {sql_quote(g['title'])}, {sql_quote(g['description'])}, "
            f"{sql_text_array(g['tags'])}, {sql_jsonb(g['structured_data'])}, {sql_quote(g['country'])}, true, NULL, {sql_embedding(g.get('embedding'))}, now(), now()"
            ")"
        )
    body = (
        "INSERT INTO mesh.mesh_node (id, created_by, node_type, title, description, tags, structured_data, country, searchable, external_id, embedding, created_at, updated_at)\nVALUES\n"
        + ",\n".join(values)
        + ";\n"
    )
    path.write_text(header + "\n" + body, encoding="utf-8")


def write_seed_jobs_sql(path: Path, jobs: list[dict[str, Any]]) -> None:
    header = textwrap.dedent(
        f"""\
        -- Dev-only repeatable seed data for quick matching tests.
        -- Loaded via %dev Flyway location: classpath:db/dev
        -- Source: Stack Overflow Developer Survey 2025

        INSERT INTO mesh.mesh_node (id, created_by, node_type, title, description, searchable, created_at, updated_at)
        VALUES ({sql_quote(SYSTEM_OWNER_NODE_ID)}, {sql_quote(SYSTEM_OWNER_NODE_ID)}, 'USER', 'System: dev-seed-owner', 'System owner for synthetic dev seeds.', false, now(), now())
        ON CONFLICT (id) DO UPDATE SET searchable = false, updated_at = now();

        INSERT INTO identity.user_identity (id, oauth_provider, oauth_subject, node_id, is_admin)
        VALUES ({sql_quote(SYSTEM_OWNER_IDENTITY_ID)}, 'dev-seed', 'system-owner', {sql_quote(SYSTEM_OWNER_NODE_ID)}, false)
        ON CONFLICT (id) DO NOTHING;

        DELETE FROM mesh.mesh_node
        WHERE created_by = {sql_quote(SYSTEM_OWNER_NODE_ID)} AND node_type = 'JOB';
        """
    )
    values: list[str] = []
    for j in jobs:
        sd = {
            "external_id": j["external_id"],
            "requirements_text": j["requirements_text"],
            "skills_required": j["skills_required"],
            "work_mode": j["work_mode"],
            "employment_type": j["employment_type"],
            "external_url": j["external_url"],
        }
        values.append(
            "("
            f"{sql_quote(SYSTEM_OWNER_NODE_ID)}, 'JOB', {sql_quote(j['title'])}, {sql_quote(j['description'])}, "
            f"{sql_text_array(j['skills_required'])}, {sql_jsonb(sd)}, {sql_quote(j['country'])}, true, {sql_embedding(j.get('embedding'))}, now(), now()"
            ")"
        )
    body = (
        "INSERT INTO mesh.mesh_node (created_by, node_type, title, description, tags, structured_data, country, searchable, embedding, created_at, updated_at)\nVALUES\n"
        + ",\n".join(values)
        + ";\n"
    )
    path.write_text(header + "\n" + body, encoding="utf-8")


def collect_skill_catalog() -> list[tuple[str, str]]:
    """Read all SO survey tech columns across ALL respondents and return unique (category, name) pairs."""
    seen: set[str] = set()
    items: list[tuple[str, str]] = []

    with open(SO_SURVEY_CSV, encoding="utf-8") as f:
        for row in csv.DictReader(f):
            for col, category in SO_CATEGORY_MAP.items():
                val = row.get(col, "")
                if not val or val == "NA":
                    continue
                for item in val.split(";"):
                    item = item.strip()
                    if not item or item.startswith("Other") or item in seen:
                        continue
                    seen.add(item)
                    items.append((category, item))

    items.sort(key=lambda x: (x[0], x[1]))
    return items


def write_seed_skill_catalog_sql(path: Path, skills: list[tuple[str, str]]) -> None:
    catalog_id = sql_quote(SO_SKILL_CATALOG_ID)
    header = textwrap.dedent(
        f"""\
        -- Dev-only repeatable SO skill catalog seed.
        -- Loaded via %dev Flyway location: classpath:db/dev
        -- Source: Stack Overflow Developer Survey 2025

        INSERT INTO skills.skill_catalog (id, name, description, level_scale, source)
        VALUES (
            {catalog_id},
            'Stack Overflow Developer Survey 2025',
            'Technology catalog derived from the Stack Overflow 2025 Developer Survey (49k respondents, 314 technologies).',
            '{{"0":"None","1":"Aware","2":"Beginner","3":"Practitioner","4":"Advanced","5":"Expert"}}'::jsonb,
            'SO Survey 2025'
        )
        ON CONFLICT (id) DO UPDATE
        SET name = EXCLUDED.name,
            description = EXCLUDED.description,
            level_scale = EXCLUDED.level_scale,
            source = EXCLUDED.source,
            updated_at = now();

        DELETE FROM skills.skill_definition WHERE catalog_id = {catalog_id};
        """
    )
    values: list[str] = []
    for category, name in skills:
        values.append(
            f"    ({catalog_id}, {sql_quote(category)}, {sql_quote(name)}, 'cross-sector')"
        )
    body = (
        "INSERT INTO skills.skill_definition (catalog_id, category, name, lxp_recommendation)\nVALUES\n"
        + ",\n".join(values)
        + ";\n"
    )
    path.write_text(header + "\n" + body, encoding="utf-8")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Generate public-source company data focused by company type and write dev seed SQL files."
    )
    parser.add_argument("--workspace", default=".", help="PeopleMesh workspace root")
    parser.add_argument(
        "--company-type",
        choices=sorted(COMPANY_TYPE_INDUSTRIES.keys()),
        default="it",
        help="Company profile used to filter SO survey skills and occupations (default: it)",
    )
    parser.add_argument("--company-name", default="Acme Corp", help="Company name used across all generated data (default: Acme Corp)")
    parser.add_argument("--users", type=int, default=500, help="Number of users to generate (dev default: 500)")
    parser.add_argument("--jobs", type=int, default=50, help="Number of internal job postings to generate (dev default: 50)")
    parser.add_argument("--groups", type=int, default=100, help="Number of internal groups/events to generate (dev default: 100)")
    parser.add_argument("--ollama-base-url", default="http://localhost:11434", help="Ollama base URL")
    parser.add_argument("--ollama-model", default="granite-embedding:30m", help="Ollama embedding model")
    parser.add_argument("--seed", type=int, default=42, help="Random seed for deterministic enrichment")
    return parser.parse_args()


def resolve_workspace(workspace_arg: str) -> Path:
    """
    Resolve the PeopleMesh workspace root.

    - If --workspace is explicitly provided, use it.
    - If default "." is used, walk up from cwd until a directory
      containing pom.xml is found (repo root heuristic).
    """
    candidate = Path(workspace_arg).resolve()
    if workspace_arg != ".":
        return candidate

    probe = candidate
    for _ in range(8):
        if (probe / "pom.xml").exists():
            return probe
        if probe.parent == probe:
            break
        probe = probe.parent

    return candidate


def fmt_seconds(value: float) -> str:
    return f"{value:.2f}s"


def main() -> int:
    run_start = perf_counter()
    args = parse_args()
    workspace = resolve_workspace(args.workspace)

    csv.field_size_limit(sys.maxsize)
    rng = random.Random(args.seed)

    if not SO_SURVEY_CSV.exists():
        print(f"[error] SO survey CSV not found at '{SO_SURVEY_CSV}'.", file=sys.stderr)
        print("[error] Download from https://insights.stackoverflow.com/survey", file=sys.stderr)
        return 2

    print("[1/4] Loading SO survey + downloading public user data...")
    phase_start = perf_counter()
    try:
        survey = load_survey_profile(args.company_type)
        users_raw = collect_public_users(args.users)
    except Exception as exc:  # noqa: BLE001
        print(f"[error] Failed to load datasets: {exc}", file=sys.stderr)
        return 2

    if not survey.skills or not survey.occupations or not users_raw:
        print("[error] One or more datasets returned empty results.", file=sys.stderr)
        return 2
    print(f"[1/4] Loaded {survey.respondent_count} SO respondents, {len(survey.skills)} skills, {len(users_raw)} users.")
    download_seconds = perf_counter() - phase_start

    print("[2/5] Building company data from SO survey profile...")
    phase_start = perf_counter()
    company = args.company_name
    users = build_user_records(users_raw, survey, company, rng)
    groups = build_group_records(survey, company, args.groups, rng)
    jobs = build_job_records(survey, company, args.jobs, rng)
    skill_catalog = collect_skill_catalog()
    build_seconds = perf_counter() - phase_start
    print(f"[2/5] Build completed: {len(skill_catalog)} skill definitions.")

    print("[3/5] Generating embeddings with Ollama...")
    phase_start = perf_counter()
    generate_embeddings_with_ollama(
        users=users,
        groups=groups,
        jobs=jobs,
        ollama_base_url=args.ollama_base_url,
        ollama_model=args.ollama_model,
    )
    embedding_seconds = perf_counter() - phase_start
    print(f"[3/5] Embeddings completed in {fmt_seconds(embedding_seconds)}.")

    db_dev = workspace / "src/main/resources/db/dev"
    if not db_dev.exists():
        print(
            f"[error] Workspace seems incorrect: '{db_dev}' does not exist. "
            "Run from repo root or pass --workspace /path/to/peoplemesh.",
            file=sys.stderr,
        )
        return 2

    print("[4/5] Writing dev seed SQL files...")
    phase_start = perf_counter()
    write_users_start = perf_counter()
    write_seed_users_sql(db_dev / "R__dev_seed_users.sql", users)
    write_users_seconds = perf_counter() - write_users_start
    write_groups_start = perf_counter()
    write_seed_groups_sql(db_dev / "R__dev_seed_groups.sql", groups)
    write_groups_seconds = perf_counter() - write_groups_start
    write_jobs_start = perf_counter()
    write_seed_jobs_sql(db_dev / "R__dev_seed_jobs.sql", jobs)
    write_jobs_seconds = perf_counter() - write_jobs_start

    print("[5/5] Writing skill catalog SQL...")
    write_catalog_start = perf_counter()
    write_seed_skill_catalog_sql(db_dev / "R__dev_seed_skill_catalog_so.sql", skill_catalog)
    write_catalog_seconds = perf_counter() - write_catalog_start
    write_seconds = perf_counter() - phase_start
    print("[done] SQL generation completed.")

    total_nodes = len(users) + len(groups) + len(jobs)
    avg_embedding_ms = (embedding_seconds / total_nodes * 1000.0) if total_nodes else 0.0
    total_seconds = perf_counter() - run_start

    print("[done] Completed successfully.")
    print(f"- company_name: {company}")
    print(f"- company_type: {args.company_type}")
    print(f"- so_respondents: {survey.respondent_count}")
    print(f"- skills: {len(survey.skills)}")
    print(f"- occupations: {len(survey.occupations)}")
    print(f"- skill_catalog: {len(skill_catalog)}")
    print(f"- users: {len(users)}")
    print(f"- groups/events: {len(groups)}")
    print(f"- jobs: {len(jobs)}")
    print(f"- ollama_model: {args.ollama_model}")
    print("- timings:")
    print(f"  - total: {fmt_seconds(total_seconds)}")
    print(f"  - load_datasets: {fmt_seconds(download_seconds)}")
    print(f"  - build_company_data: {fmt_seconds(build_seconds)}")
    print(f"  - generate_embeddings: {fmt_seconds(embedding_seconds)}")
    print(f"  - write_seed_sql_total: {fmt_seconds(write_seconds)}")
    print(f"  - write_seed_users_sql: {fmt_seconds(write_users_seconds)}")
    print(f"  - write_seed_groups_sql: {fmt_seconds(write_groups_seconds)}")
    print(f"  - write_seed_jobs_sql: {fmt_seconds(write_jobs_seconds)}")
    print(f"  - write_seed_skill_catalog_sql: {fmt_seconds(write_catalog_seconds)}")
    print(f"  - avg_embedding_per_node: {avg_embedding_ms:.1f}ms")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
