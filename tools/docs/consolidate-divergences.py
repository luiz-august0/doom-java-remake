#!/usr/bin/env python3
"""Consolidates docs/divergences/*.md (one table per agent) into docs/divergences.md (one table, IDs D-006...).

    python3 tools/docs/consolidate-divergences.py            # rewrites docs/divergences.md
    python3 tools/docs/consolidate-divergences.py --check    # exit 1 when docs/divergences.md is out of date

The first IDs (D-001...D-005) are the hand-written decisions recorded before the implementation started; they are kept
verbatim in the "Design decisions" section. Every other row gets the next free ID, in file order, so IDs are stable
while only new rows are appended at the end of a file (a new agent file sorts after the existing ones).
Rows that carried a local ID (for example W3-C's D-C01, cited by a code comment) show it as "(was D-C01)".
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SRC = ROOT / "docs" / "divergences"
OUT = ROOT / "docs" / "divergences.md"

DESIGN = """\
| ID | Area | Original (linuxdoom-1.10) | Remake | Reason | Reference |
|----|------|---------------------------|--------|--------|-----------|
| D-001 | Demos | Records and accepts only version `110` | Accepts `109` and `110`; records `109` | Compatibility with the demos in the IWADs | ADR-005 |
| D-002 | Render | Fixed limits on visplanes/drawsegs/vissprites (fails or corrupts the image) | Growable structures | Does not affect the simulation; needed at high resolution | ADR-008 |
| D-003 | Render | Medusa effect on multi-patch midtextures | Not reproduced | Memory artifact | 04 §4 |
| D-004 | Demos | Recording stops when the buffer fills up | No limit | Does not affect the simulation | 08 §1.1 |
| D-005 | Save | Does not save `target`/`tracer` or the RNG | Saves them | Saves are outside demo sync | ADR-013 |
"""

AGENT_TITLES = {
    "W1-A": "WAD format layer and level geometry loader",
    "W1-B": "Math foundation and test tooling",
    "W1-C": "Game definitions and DEHACKED",
    "W1-D": "Audio: formats, OPL3, music driver, mixer, Java Sound",
    "W1-E": "2D foundation and LWJGL platform",
    "W2-A": "Simulation core, session skeleton, trace",
    "W2-B": "3D renderer and wipe",
    "W2-C": "UI: menus, HUD, status bar, automap, finale",
    "W2-D": "Engine, main loop, sound system, app",
    "W3-A": "Monster AI",
    "W3-B": "Combat and weapons",
    "W3-C": "Map specials",
    "W3-D": "Session flow",
}


def cells(line):
    parts = [c.strip() for c in line.strip().strip("|").split("|")]
    return parts


def rows_of(path):
    """Yields the data rows (5 cells) of every table in the file."""
    out = []
    header_seen = False
    for line in path.read_text(encoding="utf-8").splitlines():
        if not line.startswith("|"):
            header_seen = False
            continue
        c = cells(line)
        if set("".join(c)) <= set("-: "):
            continue  # separator
        if not header_seen:
            header_seen = True
            continue  # header
        if len(c) != 5:
            raise SystemExit(f"{path.name}: expected 5 cells, got {len(c)}: {line[:100]}")
        out.append(c)
    return out


def notes_of(path):
    """Text after a '## Notes' heading, if any."""
    text = path.read_text(encoding="utf-8")
    m = re.search(r"^## Notes.*?\n(.*)$", text, re.S | re.M)
    return m.group(1).strip() if m else ""


def build():
    next_id = 6
    sections = []
    for agent in sorted(AGENT_TITLES):
        path = SRC / f"{agent}.md"
        if not path.exists():
            continue
        lines = []
        for area, original, ours, reason, ref in rows_of(path):
            local = re.match(r"(D-[A-Z]\d+)\s+(.*)", area)
            alias = ""
            if local:
                alias = f" (was {local.group(1)})"
                area = local.group(2)
            lines.append(f"| D-{next_id:03d}{alias} | {area} | {original} | {ours} | {reason} | {ref} |")
            next_id += 1
        sections.append((agent, AGENT_TITLES[agent], lines, notes_of(path)))
    # Agent files not in AGENT_TITLES (added later) go at the end with their file name as the title.
    known = {f"{a}.md" for a in AGENT_TITLES} | {"W0-OR.md"}
    for path in sorted(SRC.glob("*.md")):
        if path.name in known:
            continue
        agent = path.stem
        lines = []
        for area, original, ours, reason, ref in rows_of(path):
            lines.append(f"| D-{next_id:03d} | {area} | {original} | {ours} | {reason} | {ref} |")
            next_id += 1
        sections.append((agent, "see the source file", lines, notes_of(path)))

    out = []
    out.append("# Recorded divergences\n")
    out.append("Every point where the remake (1) follows DOOM.EXE v1.9 / Chocolate Doom instead of linuxdoom-1.10, or "
               "(2) intentionally diverges from the original (`// DIVERGE:` in the code, ADR-005 and ADR-007), or "
               "(3) replaces undefined behaviour of the original by a defined one.\n")
    out.append("This file is generated: the source of truth is one file per agent in [`docs/divergences/`](divergences/). "
               "Run `python3 tools/docs/consolidate-divergences.py` after editing one (`--check` verifies that this file is "
               "current). Columns: ID, area, original behaviour, ours, reason, reference. Rows citing an older local ID "
               "(`D-C01`) show it as `(was D-C01)`.\n")
    out.append("## Design decisions (D-001 to D-005)\n")
    out.append(DESIGN)
    for agent, title, lines, notes in sections:
        out.append(f"## {agent}: {title}\n")
        out.append("| ID | Area | Original | Ours | Reason | Reference |")
        out.append("|----|------|----------|------|--------|-----------|")
        out.extend(lines)
        out.append("")
        if notes:
            out.append(f"Notes from {agent}:\n")
            out.append(notes)
            out.append("")
    oracle = SRC / "W0-OR.md"
    if oracle.exists():
        body = oracle.read_text(encoding="utf-8").split("\n", 1)[1].strip()
        out.append("## W0-OR: oracle notes\n")
        out.append(body)
        out.append("")
    out.append("## Accepted demo sync exceptions\n")
    out.append("The synthetic demos `F2M29A` and `F2M29B` differ from the oracle in the `a` hash only (a dangling Arch-vile "
               "`tracer`, see the W3-A and W3-B rows above). They are listed with their justification in "
               "`testdata/demos/exceptions.tsv`, which `DemoSyncIT` reads; every other demo (399 of 401, including all 14 "
               "IWAD demos) is identical to the oracle.\n")
    return "\n".join(out)


def main():
    text = build()
    if "--check" in sys.argv:
        if not OUT.exists() or OUT.read_text(encoding="utf-8") != text:
            print("docs/divergences.md is out of date", file=sys.stderr)
            return 1
        return 0
    OUT.write_text(text, encoding="utf-8")
    print(f"wrote {OUT} ({text.count(chr(10))} lines)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
