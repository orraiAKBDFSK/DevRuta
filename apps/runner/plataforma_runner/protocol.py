"""Validación de la petición del API. Todo lo que no encaje se rechaza; nada se ejecuta sin pasar por aquí."""

import re

LANGUAGES = {"python"}
MAX_CODE = 20_000
MAX_STDIN = 10_000
MAX_CASES = 10
MAX_CHECKS = 5
DEFAULT_CASE_MS = 3_000
MAX_CASE_MS = 5_000
MAX_TOTAL_MS = 20_000
NAME = re.compile(r"^[A-Za-z_][A-Za-z0-9_]{0,63}$")


class InvalidRequest(ValueError):
    pass


def _int(value, name, low, high, default):
    if value is None:
        return default
    if isinstance(value, bool) or not isinstance(value, int) or not low <= value <= high:
        raise InvalidRequest(f"{name} debe ser un entero entre {low} y {high}")
    return value


def _check(raw):
    if not isinstance(raw, dict):
        raise InvalidRequest("cada check debe ser un objeto")
    kind = raw.get("kind")
    if kind == "requires_assignment":
        return {"kind": kind, "count_min": _int(raw.get("count_min"), "count_min", 1, 20, 1)}
    if kind == "requires_function":
        name = raw.get("name")
        if not isinstance(name, str) or not NAME.match(name):
            raise InvalidRequest("nombre de función no válido")
        return {"kind": kind, "name": name, "returns": raw.get("returns") is True}
    raise InvalidRequest("tipo de check desconocido")


def parse(body):
    """Devuelve la petición normalizada que se enviará al arnés, o lanza InvalidRequest."""
    if not isinstance(body, dict):
        raise InvalidRequest("el cuerpo debe ser un objeto JSON")
    language = body.get("language")
    if language not in LANGUAGES:
        raise InvalidRequest("lenguaje no soportado")
    code = body.get("code")
    if not isinstance(code, str) or not code.strip() or len(code) > MAX_CODE:
        raise InvalidRequest(f"code debe ser texto no vacío de hasta {MAX_CODE} caracteres")
    raw_cases = body.get("cases")
    if not isinstance(raw_cases, list) or not 1 <= len(raw_cases) <= MAX_CASES:
        raise InvalidRequest(f"cases debe tener entre 1 y {MAX_CASES} elementos")
    cases = []
    for case in raw_cases:
        stdin = case.get("stdin", "") if isinstance(case, dict) else None
        if not isinstance(stdin, str) or len(stdin) > MAX_STDIN:
            raise InvalidRequest(f"stdin debe ser texto de hasta {MAX_STDIN} caracteres")
        cases.append({"stdin": stdin})
    raw_checks = body.get("checks", [])
    if not isinstance(raw_checks, list) or len(raw_checks) > MAX_CHECKS:
        raise InvalidRequest("checks no válido")
    return {
        "language": language,
        "code": code,
        "cases": cases,
        "checks": [_check(c) for c in raw_checks],
        "case_timeout_ms": _int(body.get("case_timeout_ms"), "case_timeout_ms", 100, MAX_CASE_MS, DEFAULT_CASE_MS),
        "total_timeout_ms": _int(body.get("total_timeout_ms"), "total_timeout_ms", 100, MAX_TOTAL_MS, 10_000),
    }
