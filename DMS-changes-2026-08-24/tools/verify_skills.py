#!/usr/bin/env python3
"""Verify DMS-required Codex skills are installed and project rules reference them.

Run: python tools/verify_skills.py
Exit code 0 means all required skills and rule references are present.
"""
from __future__ import annotations

import os
import re
import sys
from pathlib import Path

HOME = Path(os.path.expanduser("~"))
SKILLS_DIR = HOME / ".codex" / "skills"
REPO = Path(__file__).resolve().parent.parent

REQUIRED = {
    "dms-project": "DMS project main entrypoint",
    "dms-requirement-intake": "Stage A requirement intake",
    "dms-ux-functional-audit": "Stage C UX/functional audit",
    "qa-skills": "Deep QA/UX/adversarial testing",
    "computer-use": "Verified desktop/browser automation",
    "playwright": "Browser automation from terminal",
    "screenshot": "Evidence screenshots",
    "review-loop": "Review-fix-review loop",
}

OPTIONAL_REFERENCES = {
    "superpowers": HOME / ".codex" / "skill-evaluation" / "superpowers",
    "sdd": HOME / ".codex" / "skill-evaluation" / "sdd",
}

RULE_FILES = [
    REPO / "AGENTS.md",
    REPO / ".memory" / "layers" / "layer1-rules.md",
    REPO / ".memory" / "layers" / "layer4-decisions.md",
    REPO / ".memory" / "layers" / "layer5-context.md",
    REPO / ".memory" / "index.md",
    REPO / ".memory" / "requirement-closure.md",
]


def parse_frontmatter(text: str) -> dict[str, str]:
    if not text.startswith("---"):
        return {}
    end = text.find("\n---", 3)
    if end == -1:
        return {}
    result: dict[str, str] = {}
    for line in text[3:end].splitlines():
        m = re.match(r"^([A-Za-z0-9_-]+):\s*(.*)$", line.strip())
        if m:
            result[m.group(1)] = m.group(2).strip().strip('"')
    return result


def main() -> int:
    failures: list[str] = []
    print("== DMS required skills ==")
    for folder, purpose in REQUIRED.items():
        skill_md = SKILLS_DIR / folder / "SKILL.md"
        if not skill_md.is_file():
            failures.append(f"missing SKILL.md: {skill_md}")
            print(f"FAIL {folder}: SKILL.md not found ({purpose})")
            continue
        text = skill_md.read_text(encoding="utf-8")
        fm = parse_frontmatter(text)
        ok = bool(fm.get("name") and fm.get("description") and "\ufffd" not in text)
        if not ok:
            failures.append(f"invalid skill: {folder}")
        print(f"{'OK  ' if ok else 'FAIL'} {folder} -> {fm.get('name','?')} ({purpose})")

    print("\n== QA skill core resources ==")
    qa_base = SKILLS_DIR / "qa-skills"
    qa_resources = [
        "references/ux-auditor.md",
        "references/adversarial-breaker.md",
        "references/mobile-ux-auditor.md",
        "agents/validation-subagent.md",
        "skills/playwright-runner",
    ]
    for rel in qa_resources:
        path = qa_base / rel
        exists = path.exists()
        if not exists:
            failures.append(f"missing qa resource: {rel}")
        print(f"{'OK  ' if exists else 'FAIL'} {rel}")

    print("\n== Evaluation references (not active skills) ==")
    for name, path in OPTIONAL_REFERENCES.items():
        exists = path.exists()
        print(f"{'OK  ' if exists else 'MISS'} {name}: {path}")

    print("\n== Project rule references ==")
    expected_tokens = ["dms-project", "dms-requirement-intake", "dms-ux-functional-audit", "qa-skills"]
    for path in RULE_FILES:
        if not path.is_file():
            failures.append(f"missing rule file: {path}")
            print(f"FAIL {path}: file missing")
            continue
        text = path.read_text(encoding="utf-8")
        missing = [token for token in expected_tokens if token not in text]
        if missing:
            failures.append(f"{path} missing tokens: {', '.join(missing)}")
            print(f"FAIL {path}: missing {', '.join(missing)}")
        else:
            print(f"OK   {path}")

    print("\n== Result ==")
    if failures:
        print(f"FAILED ({len(failures)} issue(s))")
        for item in failures:
            print(" -", item)
        return 1
    print("PASS: all DMS skills are installed and wired into project rules.")
    print("Note: a new Codex session loads the latest skill list; type $skill-name to invoke one explicitly.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
