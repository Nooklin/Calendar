#!/usr/bin/env bash
# Checks the Kotlin and XML sources before packaging: balanced braces, parentheses and comments,
# unused imports, the wrong CompositionLocalProvider import, double hyphens in comments, em dashes.
set -u
cd "$(dirname "$0")/.."
python3 - <<'PY'
import re, sys, pathlib
problems = []
files = [p for p in pathlib.Path("app/src").rglob("*") if p.suffix in (".kt", ".xml")]
for p in files:
    t = p.read_text(encoding="utf-8")
    if "—" in t:
        problems.append(f"{p}: em dash")
    if p.suffix == ".xml":
        for m in re.finditer(r"<!--(.*?)-->", t, re.S):
            if "--" in m.group(1):
                problems.append(f"{p}: double hyphen in XML comment")
        continue
    if t.count("/*") != t.count("*/"):
        problems.append(f"{p}: unbalanced block comments")
    for i, line in enumerate(t.splitlines(), 1):
        s = line.strip()
        if s.startswith("//") and "--" in s:
            problems.append(f"{p}:{i}: double hyphen in comment")
    # strip strings and comments before counting brackets
    token = re.compile(r'"""(?:.|\n)*?"""|"(?:\\.|[^"\\\n])*"|\'(?:\\.|[^\'\\\n])\'|/\*(?:.|\n)*?\*/|//[^\n]*')
    code = token.sub(lambda m: "" if m.group(0).startswith("/") else "0", t)
    for a, b in ("{}", "()", "[]"):
        if code.count(a) != code.count(b):
            problems.append(f"{p}: unbalanced {a}{b} ({code.count(a)} vs {code.count(b)})")
    body = "\n".join(l for l in t.splitlines() if not l.startswith("import "))
    for m in re.finditer(r"^import ([\w.]+)(?: as (\w+))?$", t, re.M):
        name = m.group(2) or m.group(1).split(".")[-1]
        if name in ("*", "getValue", "setValue", "provideDelegate"):
            continue
        if not re.search(r"\b" + re.escape(name) + r"\b", body):
            problems.append(f"{p}: unused import {m.group(1)}")
    if "import androidx.compose.runtime.CompositionLocalProvider" not in t and "CompositionLocalProvider(" in t:
        problems.append(f"{p}: CompositionLocalProvider used without the runtime import")
    if re.search(r"import androidx\.compose\.\w+(\.\w+)*\.CompositionLocalProvider", t) and "import androidx.compose.runtime.CompositionLocalProvider" not in t:
        problems.append(f"{p}: wrong CompositionLocalProvider import")
print("\n".join(problems) if problems else "sweep clean")
sys.exit(1 if problems else 0)
PY
