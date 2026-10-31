#!/usr/bin/env python3
"""Valida el contenido en /content (ver content/SCHEMA.md).

Ejecuta código del propio repositorio (soluciones de referencia) con un
subproceso local y timeout. NO es el runner de producción y nunca debe usarse
con código de usuarios.
"""
import ast
import subprocess
import sys
import tempfile
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parent.parent / "content"
TYPES = {"multiple_choice", "predict_output", "order_lines", "fill_code", "write_code"}
SAMPLE_STDIN = "1\n" * 5  # entrada de relleno para ejercicios sin tests
errors: list[str] = []
# Solo se ejecuta aquí el código de Python. El de otros lenguajes (C++) lo ejecuta scripts/verify_runner_content.py
# con el runner real, que es el que tiene el compilador y el sandbox.
RUN_LOCAL = True


def err(where: str, msg: str) -> None:
    errors.append(f"{where}: {msg}")


def run(code: str, stdin: str = "") -> tuple[int, str]:
    # carpeta temporal propia: los ejercicios de archivos no deben ensuciar el repo
    with tempfile.TemporaryDirectory() as tmp:
        try:
            p = subprocess.run(
                [sys.executable, "-I", "-c", code],
                input=stdin, capture_output=True, text=True, timeout=10, cwd=tmp,
            )
        except subprocess.TimeoutExpired:
            return -1, ""  # bucle infinito: cuenta como fallo
    return p.returncode, p.stdout


def bilingual(obj, where: str) -> None:
    if isinstance(obj, dict) and ("es" in obj or "en" in obj):
        for lang in ("es", "en"):
            if not str(obj.get(lang, "")).strip():
                err(where, f"falta texto '{lang}'")
    elif isinstance(obj, dict):
        for k, v in obj.items():
            bilingual(v, f"{where}.{k}")
    elif isinstance(obj, list):
        for i, v in enumerate(obj):
            bilingual(v, f"{where}[{i}]")


def count_assignments(code: str) -> int:
    try:
        return sum(isinstance(n, ast.Assign) for n in ast.walk(ast.parse(code)))
    except SyntaxError:
        return 0


def has_function(code: str, name: str, returns: bool) -> bool:
    """True si el código define `name` (y, si returns, con un `return valor`)."""
    try:
        tree = ast.parse(code)
    except SyntaxError:
        return False
    for node in ast.walk(tree):
        if isinstance(node, ast.FunctionDef) and node.name == name:
            if not returns:
                return True
            return any(isinstance(n, ast.Return) and n.value is not None for n in ast.walk(node))
    return False


def passes(code: str, ex: dict) -> bool:
    for t in ex["tests"]:
        rc, out = run(code, t.get("stdin", ""))
        if rc != 0 or out != t["expected_stdout"]:
            return False
    for c in ex.get("static_checks", []):
        if c["kind"] == "requires_assignment" and count_assignments(code) < c.get("count_min", 1):
            return False
        if c["kind"] == "requires_function" and not has_function(code, c["name"], c.get("returns", False)):
            return False
    return True


def check_exercise(ex: dict, where: str) -> None:
    t = ex.get("type")
    if t not in TYPES:
        return err(where, f"tipo desconocido {t!r}")
    bilingual({k: ex.get(k) for k in ("prompt", "hints", "explanation", "options")}, where)
    if not ex.get("hints"):
        err(where, "sin pistas")
    if not RUN_LOCAL and t != "multiple_choice":
        if t == "write_code" and not ex.get("wrong_examples"):
            err(where, "sin wrong_examples")
        return
    if t == "multiple_choice":
        if not 0 <= ex["answer"] < len(ex["options"]):
            err(where, "answer fuera de rango")
    elif t == "predict_output":
        rc, out = run(ex["code"])
        if rc != 0 or out.rstrip() != ex["answer"].rstrip():
            err(where, f"salida real {out!r} != answer {ex['answer']!r}")
    elif t == "order_lines":
        rc, _ = run("\n".join(ex["lines"]), SAMPLE_STDIN)
        if rc != 0:
            err(where, "las líneas en orden correcto no se ejecutan")
    elif t == "fill_code":
        parts = ex["code"].split("___")
        if len(parts) - 1 != len(ex["blanks"]):
            return err(where, "nº de huecos != nº de blanks")
        code = parts[0]
        for blank, tail in zip(ex["blanks"], parts[1:]):
            code += blank[0] + tail
        rc, _ = run(code, SAMPLE_STDIN)
        if rc != 0:
            err(where, "el código completado no se ejecuta")
    elif t == "write_code":
        if not passes(ex["reference_solution"], ex):
            err(where, "la solución de referencia NO pasa los tests")
        for i, w in enumerate(ex.get("wrong_examples", [])):
            if passes(w["code"], ex):
                err(where, f"wrong_examples[{i}] pasa los tests (debería fallar)")
        if not ex.get("wrong_examples"):
            err(where, "sin wrong_examples")


META_FILES = {"course.yaml", "skills.yaml", "unit.yaml"}
STATUSES = {"draft", "reviewed", "published"}


def load(path: Path) -> dict:
    return yaml.safe_load(path.read_text(encoding="utf-8"))


def has_cycle(requires: dict[str, list[str]]) -> list[str] | None:
    """Devuelve un ciclo de prerrequisitos si existe, o None."""
    state: dict[str, int] = {}
    path: list[str] = []

    def visit(node: str) -> list[str] | None:
        state[node] = 1
        path.append(node)
        for nxt in requires.get(node, []):
            if state.get(nxt) == 1:
                return path[path.index(nxt):] + [nxt]
            if state.get(nxt) is None:
                found = visit(nxt)
                if found:
                    return found
        path.pop()
        state[node] = 2
        return None

    for node in requires:
        if state.get(node) is None:
            found = visit(node)
            if found:
                return found
    return None


def check_language(lang_dir: Path, seen_exercises: set[str]) -> int:
    """Valida curso, habilidades, unidades y lecciones de un lenguaje. Devuelve el nº de lecciones."""
    w0 = lang_dir.name
    course_file = lang_dir / "course.yaml"
    skills_file = lang_dir / "skills.yaml"
    if not course_file.exists() or not skills_file.exists():
        err(w0, "falta course.yaml o skills.yaml")
        return 0

    course = load(course_file)
    global RUN_LOCAL
    RUN_LOCAL = course.get("language") == "python"
    bilingual({"title": course["title"], "description": course["description"]}, f"{w0}/course.yaml")

    # Habilidades: ids únicos, textos en ambos idiomas, prerrequisitos existentes y sin ciclos.
    requires: dict[str, list[str]] = {}
    for s in load(skills_file)["skills"]:
        if s["id"] in requires:
            err(f"{w0}/skills.yaml", f"habilidad duplicada {s['id']}")
        requires[s["id"]] = s.get("requires", [])
        bilingual(s["name"], f"{w0}/skills.yaml::{s['id']}")
    for sid, reqs in requires.items():
        for r in reqs:
            if r not in requires:
                err(f"{w0}/skills.yaml", f"{sid} requiere una habilidad inexistente: {r}")
    cycle = has_cycle(requires)
    if cycle:
        err(f"{w0}/skills.yaml", "ciclo de prerrequisitos: " + " -> ".join(cycle))

    # Unidades: ids únicos y posiciones 1..n.
    units: dict[str, int] = {}
    unit_files = sorted(lang_dir.glob("*/unit.yaml"))
    for uf in unit_files:
        u = load(uf)
        w = uf.relative_to(ROOT).as_posix()
        bilingual({"title": u["title"], "summary": u["summary"]}, w)
        if u["id"] in units:
            err(w, f"unidad duplicada {u['id']}")
        units[u["id"]] = u["position"]
    if sorted(units.values()) != list(range(1, len(units) + 1)):
        err(f"{w0}", f"las posiciones de las unidades no son 1..{len(units)}: {sorted(units.values())}")

    # Lecciones.
    positions: dict[str, list[int]] = {u: [] for u in units}
    lesson_ids: set[str] = set()
    count = 0
    for f in sorted(lang_dir.rglob("*.yaml")):
        if f.name in META_FILES:
            continue
        lesson = load(f)
        w = f.relative_to(ROOT).as_posix()
        count += 1
        bilingual({"title": lesson["title"], "explanation": lesson["explanation"]}, w)
        if lesson["status"] not in STATUSES:
            err(w, f"status desconocido {lesson['status']!r}")
        if lesson["status"] == "published" and not lesson.get("reviewed_by"):
            err(w, "published sin reviewed_by")
        if lesson["id"] in lesson_ids:
            err(w, f"id de lección duplicado {lesson['id']}")
        lesson_ids.add(lesson["id"])
        if lesson["unit"] not in units:
            err(w, f"la unidad {lesson['unit']} no existe")
        else:
            positions[lesson["unit"]].append(lesson["position"])
        if f.parent.joinpath("unit.yaml").exists() and load(f.parent / "unit.yaml")["id"] != lesson["unit"]:
            err(w, "la lección declara una unidad distinta a la de su carpeta")
        used = set(lesson["skills"])
        for ex in lesson["exercises"]:
            used.update(ex["skills"])
        for s in sorted(used - requires.keys()):
            err(w, f"usa la habilidad inexistente {s}")
        for ex in lesson["exercises"]:
            if ex["id"] in seen_exercises:
                err(w, f"id duplicado {ex['id']}")
            seen_exercises.add(ex["id"])
            check_exercise(ex, f"{w}::{ex['id']}")
    for unit_id, pos in positions.items():
        if sorted(pos) != list(range(1, len(pos) + 1)):
            err(w0, f"las posiciones de las lecciones de {unit_id} no son 1..{len(pos)}: {sorted(pos)}")
        if not pos:
            err(w0, f"la unidad {unit_id} no tiene lecciones")
    return count


def main() -> int:
    seen: set[str] = set()
    lessons = 0
    for lang_dir in sorted(p for p in ROOT.iterdir() if p.is_dir()):
        lessons += check_language(lang_dir, seen)
    for e in errors:
        print("ERROR", e)
    print(f"{lessons} lecciones, {len(seen)} ejercicios, {len(errors)} errores")
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
