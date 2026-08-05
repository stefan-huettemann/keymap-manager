#!/usr/bin/env python3
"""Validate 'MacBook Pro DE.xml' against this repo's keymap invariants.

    python3 keymaps/validate_keymap.py [path-to-keymap.xml]

Checks the declared XML (structure, ids, sorting) AND the *effective* keymap it
produces once the parent chain is resolved with the platform's Ctrl<->Meta
conversion. The effective view is the one that matters: the reason this keymap
once shipped Ctrl-V for Paste is that no check ever looked at it.

Exit status 0 = all invariants hold. Warnings never fail the run.
"""
import os
import sys
import xml.etree.ElementTree as ET
from collections import defaultdict

KM = os.path.dirname(os.path.abspath(__file__))
TARGET = sys.argv[1] if len(sys.argv) > 1 else os.path.join(KM, "MacBook Pro DE.xml")

# No physical key for these on a German T1 MacBook Pro (see keys.md "Keys").
UNREACHABLE = {
    "SLASH", "BACK_SLASH", "OPEN_BRACKET", "CLOSE_BRACKET", "SEMICOLON",
    "QUOTE", "BACK_QUOTE", "EQUALS", "INSERT",
    "ADD", "SUBTRACT", "MULTIPLY", "DIVIDE", "DECIMAL", "SEPARATOR",
    *(f"NUMPAD{i}" for i in range(10)),
    "DEAD_ACUTE", "DEAD_CIRCUMFLEX",
}
# macOS Spaces / Mission Control consumes these before the IDE sees them.
SPACES = {"control LEFT", "control RIGHT", "control shift LEFT", "control shift RIGHT"}
ALLOWED_TAGS = {"keyboard-shortcut", "mouse-shortcut", "keyboard-gesture-shortcut"}
# Intentional empty overrides that clear conflicting *plugin* defaults; these
# action ids live in the Terminal/VCS plugins, not in the reference keymaps.
DOCUMENTED_EMPTIES = {
    "Terminal.ClearPrompt",
    "Terminal.SearchInCommandHistory",
    "Vcs.CombinedDiff.ToggleCollapseBlock",
}
# Bindings a macOS user will notice instantly if the Ctrl<->Meta swap regresses.
CANARIES = {
    "$Paste": "meta V", "$Copy": "meta C", "$Cut": "meta X",
    "$SelectAll": "meta A", "$Undo": "meta Z", "SaveAll": "meta S",
    "GotoAction": "meta shift A", "ReformatCode": "meta alt L",
    "EditorDuplicate": "meta D", "RecentFiles": "meta E",
}
RESERVED_PREFIX = "Mac OS X"

fails, warns = [], []


def swap(ks):
    """MacOSDefaultKeymap.mapModifiers: Ctrl <-> Meta."""
    if not ks:
        return ks
    return " ".join("meta" if t.lower() in ("control", "ctrl")
                    else "control" if t.lower() == "meta" else t
                    for t in ks.split())


def norm(ks):
    """Canonical form so equal shortcuts compare equal regardless of spelling."""
    if not ks:
        return ks
    toks = ks.split()
    key = toks[-1].upper() if len(toks[-1]) > 1 and not toks[-1].startswith("#") else toks[-1].upper()
    mods = ["control" if t.lower() == "ctrl" else t.lower() for t in toks[:-1]]
    order = {"control": 0, "meta": 1, "alt": 2, "shift": 3}
    return " ".join(sorted(mods, key=lambda m: order.get(m, 9)) + [key])


def load(path):
    root = ET.parse(path).getroot()
    out = {}
    for a in root.findall("action"):
        scs = []
        for c in a:
            if c.tag == "keyboard-shortcut":
                scs.append(("kbd", c.get("first-keystroke"), c.get("second-keystroke")))
            elif c.tag == "mouse-shortcut":
                scs.append(("mouse", c.get("keystroke"), None))
            elif c.tag == "keyboard-gesture-shortcut":
                scs.append(("gesture", c.get("modifier"), c.get("hold-count")))
        out[a.get("id")] = scs
    return root, out


try:
    _, dflt = load(os.path.join(KM, "$default.xml"))
    _, mac = load(os.path.join(KM, "Mac OS X 10.5+.xml"))
    root, own = load(TARGET)
except ET.ParseError as e:
    print(f"FAIL - not well-formed XML: {e}")
    sys.exit(1)

elems = list(ET.parse(TARGET).getroot())

# 1. root attributes
if root.get("parent") != "Mac OS X 10.5+":
    fails.append(f"parent is {root.get('parent')!r}, must be 'Mac OS X 10.5+'")
name = root.get("name") or ""
if name != os.path.splitext(os.path.basename(TARGET))[0]:
    fails.append(f"name {name!r} does not match the filename (users would be silently reverted)")
if name.startswith(RESERVED_PREFIX):
    fails.append(f"name starts with {RESERVED_PREFIX!r} -> MacOSDefaultKeymap would convert own bindings")

# 2. ids unique + alphabetically sorted
ids = [e.get("id") for e in elems]
if len(ids) != len(set(ids)):
    fails.append(f"duplicate action ids: {sorted(i for i in set(ids) if ids.count(i) > 1)}")
# the reference keymaps sort case-insensitively; match them so the three files
# stay diffable side by side
if ids != sorted(ids, key=str.lower):
    first = next((a for a, b in zip(ids, sorted(ids, key=str.lower)) if a != b), None)
    fails.append(f"ids not alphabetically sorted (first out of place: {first!r})")

# 3. only the three legal child tags (anything else -> InvalidDataException at load)
for e in elems:
    for c in e:
        if c.tag not in ALLOWED_TAGS:
            fails.append(f"{e.get('id')}: illegal child <{c.tag}> -> InvalidDataException at load")

# 4. every id known to a reference keymap (unknown ids fail SILENTLY at runtime)
known = set(dflt) | set(mac)
for aid in ids:
    if aid not in known and aid not in DOCUMENTED_EMPTIES:
        fails.append(f"{aid}: unknown action id -> fails SILENTLY at runtime")

# 5. build the effective keymap: parent chain + Ctrl<->Meta on $default rows
effective = {}
for aid in set(dflt) | set(mac):
    if aid in mac:
        effective[aid] = mac[aid]
    else:
        effective[aid] = [(k, swap(a) if k != "mouse" else a, swap(b) if k == "kbd" else b)
                          for k, a, b in dflt[aid]]
stock = dict(effective)
effective.update(own)          # an own declaration REPLACES the inherited set

# 6. no unreachable key anywhere in the effective keymap
for aid, scs in effective.items():
    for kind, a, b in scs:
        if kind != "kbd":
            continue
        for part in (a, b):
            if part and part.split()[-1].upper() in UNREACHABLE:
                fails.append(f"{aid}: unreachable key in {part!r}")

# 7. no macOS Spaces combo survives
spaces = {norm(s) for s in SPACES}
for aid, scs in effective.items():
    for kind, a, b in scs:
        if kind == "kbd" and a and not b and norm(a) in spaces:
            fails.append(f"{aid}: still on a macOS Spaces combo ({a})")

# 8. the Ctrl<->Meta canaries
for aid, want in CANARIES.items():
    got = [a for k, a, b in effective.get(aid, []) if k == "kbd"]
    if norm(want) not in {norm(g) for g in got}:
        fails.append(f"{aid}: expected {want}, effective set is {got or '(unbound)'}")

# 9. collisions: compare co-bound GROUPS, not keystrokes (a rebind moves the key,
#    so keystroke identity would flag every substitution as new)
def groups(km):
    by_stroke, prefixes = defaultdict(set), defaultdict(set)
    for aid, scs in km.items():
        for kind, a, b in scs:
            if kind != "kbd" or not a:
                continue
            (prefixes if b else by_stroke)[norm(a)].add(aid)
    return by_stroke, prefixes

new_single, new_prefix = groups(effective)
old_single, _ = groups(stock)
old_sets = [v for v in old_single.values() if len(v) > 1]
for ks, actions in sorted(new_single.items()):
    if len(actions) > 1 and not any(actions <= o for o in old_sets):
        warns.append(f"co-bound on {ks}, and not co-bound upstream: {sorted(actions)}")
for ks in sorted(set(new_single) & set(new_prefix)):
    fails.append(f"{ks} is both a single-stroke shortcut {sorted(new_single[ks])} "
                 f"and the prefix of {sorted(new_prefix[ks])} -> ambiguous")

# 10. report
bound = sum(1 for v in effective.values() if v)
deviations = [a for a in effective if effective[a] != stock.get(a, [])]
print(f"{os.path.basename(TARGET)}")
print(f"  declared actions    : {len(ids)}")
print(f"  effective bound     : {bound}")
print(f"  deviations vs stock : {len(deviations)}")
print(f"  co-bound groups     : {len([v for v in new_single.values() if len(v) > 1])} "
      f"(stock macOS: {len(old_sets)})")
print()
if fails:
    print(f"FAIL - {len(fails)} problem(s)")
    for f in fails:
        print(f"  x {f}")
else:
    print("PASS - all invariants hold")
if warns:
    print()
    for w in warns:
        print(f"  ! {w}")
sys.exit(1 if fails else 0)
