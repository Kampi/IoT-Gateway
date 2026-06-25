#!/usr/bin/env python3
"""Update the Configuration Variables section in README.md.

Scans all recipes in the layer for @USER_CONFIG markers:

    # @USER_CONFIG: Human-readable description
    VARIABLE_NAME ?= "default_value"

The script replaces everything between the HTML markers in README.md:

    <!-- USER_CONFIG_START -->
    ...generated table...
    <!-- USER_CONFIG_END -->

Run from anywhere inside the layer:
    python3 scripts/update-vars-doc.py
"""

import re
import sys
from pathlib import Path

LAYER_DIR = Path(__file__).resolve().parent.parent
README = LAYER_DIR / "README.md"
MARKER_START = "<!-- USER_CONFIG_START -->"
MARKER_END = "<!-- USER_CONFIG_END -->"

_VAR_RE = re.compile(r'^([A-Z][A-Z0-9_]+)\s*\?=\s*"(.*)"')
_DOC_RE = re.compile(r'^#\s*@USER_CONFIG:\s*(.*)')


def collect_vars():
    rows = []
    bb_files = sorted(
        list(LAYER_DIR.rglob("recipes-*/**/*.bb"))
        + list(LAYER_DIR.rglob("recipes-*/**/*.bbappend"))
    )
    for bb in bb_files:
        lines = bb.read_text().splitlines()
        pending_desc = None
        for line in lines:
            doc_m = _DOC_RE.match(line)
            if doc_m:
                pending_desc = doc_m.group(1).strip()
                continue
            if pending_desc is not None:
                var_m = _VAR_RE.match(line)
                if var_m:
                    rows.append(
                        {
                            "var": var_m.group(1),
                            "default": var_m.group(2),
                            "desc": pending_desc,
                            "recipe": bb.name,
                        }
                    )
                pending_desc = None
    return rows


def build_table(rows):
    if not rows:
        return "_No configurable variables found._\n"
    lines = [
        "| Variable | Default | Description | Recipe |",
        "| --- | --- | --- | --- |",
    ]
    for r in rows:
        lines.append(
            f"| `{r['var']}` | `{r['default']}` | {r['desc']} | `{r['recipe']}` |"
        )
    return "\n".join(lines) + "\n"


def update_readme(table):
    content = README.read_text()
    pattern = re.compile(
        re.escape(MARKER_START) + r".*?" + re.escape(MARKER_END),
        re.DOTALL,
    )
    replacement = f"{MARKER_START}\n\n{table}\n{MARKER_END}"
    new_content, count = pattern.subn(replacement, content)
    if count == 0:
        print(f"ERROR: markers not found in {README}", file=sys.stderr)
        print(f"  Add  {MARKER_START}  and  {MARKER_END}  to README.md", file=sys.stderr)
        sys.exit(1)
    README.write_text(new_content)


if __name__ == "__main__":
    rows = collect_vars()
    table = build_table(rows)
    update_readme(table)
    print(f"README.md updated ({len(rows)} variable(s) documented).")
