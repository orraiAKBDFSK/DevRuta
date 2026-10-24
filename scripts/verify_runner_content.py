#!/usr/bin/env python3
"""Pasa TODOS los ejercicios de escribir código por el runner real (contenedores aislados).

Comprueba, con la misma comparación que usa la API, que la solución de referencia de cada ejercicio pasa todos sus
tests y comprobaciones estáticas, y que cada `wrong_examples` falla. Es la prueba de que el contenido y el motor
de ejecución se entienden entre sí (scripts/validate_content.py hace algo parecido, pero con el Python local).

Uso:
    python scripts/verify_runner_content.py --url http://127.0.0.1:8090 --token <RUNNER_TOKEN>
"""

import argparse
import json
import sys
import urllib.error
import urllib.request
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parent.parent / "content"


def normalize(text: str) -> str:
    """Igual que ExerciseEvaluation.normalizeOutput en la API."""
    lines = [line.rstrip() for line in text.replace("\r\n", "\n").split("\n")]
    while lines and not lines[0]:
        lines.pop(0)
    while lines and not lines[-1]:
        lines.pop()
    return "\n".join(lines)


def call(url: str, token: str, code: str, exercise: dict) -> dict:
    body = {
        "language": "python",
        "code": code,
        "cases": [{"stdin": t.get("stdin", "")} for t in exercise["tests"]],
        "checks": exercise.get("static_checks", []),
    }
    for attempt in range(6):
        request = urllib.request.Request(
            url + "/v1/run", data=json.dumps(body).encode(), method="POST",
            headers={"Authorization": f"Bearer {token}", "Content-Type": "application/json"})
        try:
            with urllib.request.urlopen(request, timeout=60) as response:
                return json.load(response)
        except urllib.error.HTTPError as e:
            if e.code != 503:  # 503 = ocupado: se reintenta
                raise
    raise RuntimeError("el runner sigue ocupado")


def passes(result: dict, exercise: dict) -> bool:
    for case, test in zip(result["cases"], exercise["tests"]):
        if (case["timed_out"] or case["truncated"] or case["exit_code"] != 0
                or normalize(case["stdout"]) != normalize(test["expected_stdout"])):
            return False
    return all(check["ok"] for check in result["checks"])


def verify(args, item) -> list[str]:
    where, exercise = item
    problems = []
    if not passes(call(args.url, args.token, exercise["reference_solution"], exercise), exercise):
        problems.append(f"{where}: la solución de referencia NO pasa en el runner")
    for i, wrong in enumerate(exercise.get("wrong_examples", [])):
        if passes(call(args.url, args.token, wrong["code"], exercise), exercise):
            problems.append(f"{where}: wrong_examples[{i}] pasa en el runner (debería fallar)")
    return problems


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--url", required=True)
    parser.add_argument("--token", required=True)
    parser.add_argument("--workers", type=int, default=3)
    args = parser.parse_args()

    items = []
    for path in sorted(ROOT.glob("python/*/*.yaml")):
        if path.name == "unit.yaml":
            continue
        for exercise in (yaml.safe_load(path.read_text(encoding="utf-8")) or {}).get("exercises", []):
            if exercise.get("type") == "write_code":
                items.append((exercise["id"], exercise))

    with ThreadPoolExecutor(args.workers) as pool:
        results = list(pool.map(lambda item: verify(args, item), items))
    problems = [p for r in results for p in r]
    wrongs = sum(len(e.get("wrong_examples", [])) for _, e in items)
    print(f"{len(items)} ejercicios de escribir código, {wrongs} ejemplos erróneos, {len(problems)} problemas")
    for problem in problems:
        print("  -", problem)
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
