#!/usr/bin/env python3
"""Informe para quien revisa el contenido: qué lecciones faltan por revisar y qué contienen.

Uso:  python scripts/review_report.py [--lang es]
No modifica nada. Para publicar una lección, quien la revisó añade su nombre a `reviewed_by` y pone
`status: published` en el YAML (ver docs/revision-de-contenido.md).
"""

import argparse
from collections import Counter
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parent.parent / "content"


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--lang", default="es", choices=["es", "en"])
    args = parser.parse_args()

    total = Counter()
    for unit_file in sorted(ROOT.glob("python/*/unit.yaml")):
        unit = yaml.safe_load(unit_file.read_text(encoding="utf-8"))
        title = unit.get("title", {}).get(args.lang, unit_file.parent.name)
        print(f"\n## {unit_file.parent.name}: {title}")
        for lesson_file in sorted(unit_file.parent.glob("*.yaml")):
            if lesson_file.name == "unit.yaml":
                continue
            lesson = yaml.safe_load(lesson_file.read_text(encoding="utf-8"))
            exercises = lesson.get("exercises", [])
            kinds = Counter(e.get("type") for e in exercises)
            reviewers = lesson.get("reviewed_by") or []
            state = "PUBLICADA" if lesson.get("status") == "published" and reviewers else "pendiente"
            total[state] += 1
            detail = ", ".join(f"{n} {k}" for k, n in sorted(kinds.items()))
            print(f"- [{state}] {lesson['id']} — {lesson.get('title', {}).get(args.lang, '?')} "
                  f"({len(exercises)} ejercicios: {detail}) revisores: {', '.join(reviewers) or '—'}")
    print(f"\nResumen: {total['PUBLICADA']} publicadas, {total['pendiente']} pendientes de revisión")


if __name__ == "__main__":
    main()
