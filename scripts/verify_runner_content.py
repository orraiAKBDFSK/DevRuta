#!/usr/bin/env python3
"""Pasa por el runner real (contenedores aislados) los ejercicios de escribir código de TODOS los lenguajes y los
de «predecir la salida» de los lenguajes que no son Python (C++), que no se pueden ejecutar en local.

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


def call(url: str, token: str, code: str, exercise: dict, language: str = "python") -> dict:
    body = {
        "language": language,
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
    """Todos los tests (y comprobaciones estáticas) pasan con la salida de este resultado."""
    for case, test in zip(result["cases"], exercise["tests"]):
        if (case["timed_out"] or case["truncated"] or case["exit_code"] != 0
                or normalize(case["stdout"]) != normalize(test["expected_stdout"])):
            return False
    return all(check["ok"] for check in result["checks"])


def verify(args, item) -> list[str]:
    where, exercise, language = item
    problems = []
    if exercise["type"] == "fill_code":
        # El código completado con la primera alternativa de cada hueco debe compilar y ejecutarse sin error.
        parts = exercise["code"].split("___")
        code = parts[0] + "".join(blank[0] + tail for blank, tail in zip(exercise["blanks"], parts[1:]))
        result = call(args.url, args.token, code, {"tests": [{"stdin": "1\n"}]}, language)
        if any(c["exit_code"] != 0 or c["timed_out"] for c in result["cases"]):
            problems.append(f"{where}: el código completado no compila o falla en el runner")
        return problems
    if exercise["type"] == "predict_output":
        # El programa del enunciado, ejecutado de verdad, debe imprimir la respuesta declarada.
        case = {"tests": [{"stdin": "", "expected_stdout": exercise["answer"]}]}
        if not passes(call(args.url, args.token, exercise["code"], case, language), case):
            problems.append(f"{where}: el código no imprime la respuesta declarada en el runner")
        return problems
    if not passes(call(args.url, args.token, exercise["reference_solution"], exercise, language), exercise):
        problems.append(f"{where}: la solución de referencia NO pasa en el runner")
    for i, wrong in enumerate(exercise.get("wrong_examples", [])):
        if passes(call(args.url, args.token, wrong["code"], exercise, language), exercise):
            problems.append(f"{where}: wrong_examples[{i}] pasa en el runner (debería fallar)")
    return problems


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--url", required=True)
    parser.add_argument("--token", required=True)
    parser.add_argument("--workers", type=int, default=3)
    args = parser.parse_args()

    items = []
    for course_file in sorted(ROOT.glob("*/course.yaml")):
        language = yaml.safe_load(course_file.read_text(encoding="utf-8"))["language"]
        for path in sorted(course_file.parent.glob("*/*.yaml")):
            if path.name == "unit.yaml":
                continue
            for exercise in (yaml.safe_load(path.read_text(encoding="utf-8")) or {}).get("exercises", []):
                # Python: solo escribir código (lo demás ya lo ejecuta validate_content.py en local).
                if exercise.get("type") == "write_code" or (language != "python" and exercise.get("type") in ("predict_output", "fill_code")):
                    items.append((exercise["id"], exercise, language))

    with ThreadPoolExecutor(args.workers) as pool:
        results = list(pool.map(lambda item: verify(args, item), items))
    problems = [p for r in results for p in r]
    wrongs = sum(len(e.get("wrong_examples", [])) for _, e, _ in items)
    print(f"{len(items)} ejercicios verificados en el runner, {wrongs} ejemplos erróneos, {len(problems)} problemas")
    for problem in problems:
        print("  -", problem)
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
