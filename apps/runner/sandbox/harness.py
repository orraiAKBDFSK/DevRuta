"""Arnés que corre DENTRO del contenedor sandbox (un contenedor por envío).

Lee un JSON por stdin ({code, cases, checks, case_timeout_ms, total_timeout_ms}), ejecuta el código del estudiante
una vez por caso en un subproceso con límites propios y escribe el resultado como JSON por stdout.

El código del estudiante corre en un subproceso con sus propias tuberías: no puede escribir en el stdout de este
arnés. Si mata al arnés, el runner no recibe resultado y lo trata como fallo (nunca como acierto).
"""

import ast
import json
import os
import re
import resource
import signal
import subprocess
import sys
import tempfile
import threading
import time

MAX_STDOUT = 64 * 1024
MAX_STDERR = 8 * 1024
MEMORY_LIMIT = 200 * 1024 * 1024
COMPILE_TIMEOUT = 15
COMPILE_MEMORY = 700 * 1024 * 1024
MAX_COMPILE_ERROR = 4 * 1024
FILE_LIMIT = 8 * 1024 * 1024
NAME = re.compile(r"^[A-Za-z_][A-Za-z0-9_]{0,63}$")


def static_check(tree, check):
    if tree is None:
        return False
    kind = check.get("kind")
    if kind == "requires_assignment":
        count = sum(isinstance(n, ast.Assign) for n in ast.walk(tree))
        return count >= int(check.get("count_min", 1))
    if kind == "requires_function":
        name = check.get("name", "")
        if not NAME.match(name):
            return False
        for node in ast.walk(tree):
            if isinstance(node, ast.FunctionDef) and node.name == name:
                if not check.get("returns", False):
                    return True
                return any(isinstance(n, ast.Return) and n.value is not None for n in ast.walk(node))
        return False
    return False


def run_checks(code, checks):
    try:
        tree = ast.parse(code)
    except (SyntaxError, ValueError, RecursionError, MemoryError):
        tree = None
    return [{"kind": c.get("kind"), "ok": static_check(tree, c)} for c in checks]


def child_limits(timeout_s):
    def apply():
        resource.setrlimit(resource.RLIMIT_CPU, (timeout_s + 1, timeout_s + 1))
        resource.setrlimit(resource.RLIMIT_AS, (MEMORY_LIMIT, MEMORY_LIMIT))
        resource.setrlimit(resource.RLIMIT_FSIZE, (FILE_LIMIT, FILE_LIMIT))
        resource.setrlimit(resource.RLIMIT_NOFILE, (64, 64))
        # Con el límite de procesos del contenedor (64) por encima, una fork bomb se queda sin procesos ella sola y deja
        # sitio al arnés para recoger el resultado.
        resource.setrlimit(resource.RLIMIT_NPROC, (40, 40))
        resource.setrlimit(resource.RLIMIT_CORE, (0, 0))

    return apply


class Capture(threading.Thread):
    """Lee una tubería hasta `limit` bytes; si se pasa, marca `truncated` y avisa para matar el proceso."""

    def __init__(self, stream, limit, on_overflow):
        super().__init__(daemon=True)
        self.stream, self.limit, self.on_overflow = stream, limit, on_overflow
        self.data = bytearray()
        self.truncated = False

    def run(self):
        while True:
            chunk = self.stream.read1(4096)
            if not chunk:
                return
            room = self.limit - len(self.data)
            self.data += chunk[:max(room, 0)]
            if len(chunk) > room:
                self.truncated = True
                self.on_overflow()
                return


def feed(stream, text):
    try:
        stream.write(text.encode("utf-8", "replace"))
        stream.close()
    except (BrokenPipeError, OSError, ValueError):
        pass


def kill_group(proc):
    try:
        os.killpg(proc.pid, signal.SIGKILL)
    except (ProcessLookupError, PermissionError):
        pass


def run_case(command, stdin, timeout_ms, deadline):
    timeout_s = max(min(timeout_ms / 1000, deadline - time.monotonic()), 0.05)
    started = time.monotonic()
    env = {"PATH": "/usr/local/bin:/usr/bin:/bin", "PYTHONIOENCODING": "utf-8", "PYTHONDONTWRITEBYTECODE": "1",
           "LANG": "C.UTF-8"}
    # Carpeta temporal propia para cada test: ni los archivos ni el directorio de trabajo se comparten entre tests.
    with tempfile.TemporaryDirectory(dir="/tmp") as work:
        env["HOME"] = work
        proc = subprocess.Popen(command, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                                stderr=subprocess.PIPE, cwd=work, env=env, start_new_session=True,
                                preexec_fn=child_limits(int(timeout_s) + 1), close_fds=True)
        try:
            out = Capture(proc.stdout, MAX_STDOUT, lambda: kill_group(proc))
            err = Capture(proc.stderr, MAX_STDERR, lambda: None)
            out.start()
            err.start()
            threading.Thread(target=feed, args=(proc.stdin, stdin), daemon=True).start()
        except (RuntimeError, OSError):
            # El programa se quedó sin procesos o hilos (p. ej. una fork bomb): se corta y se cuenta como fallo suyo.
            kill_group(proc)
            proc.wait()
            return {"stdout": "", "stderr": "El programa creó demasiados procesos.", "exit_code": 1,
                    "timed_out": False, "truncated": False, "duration_ms": int((time.monotonic() - started) * 1000)}
        timed_out = False
        try:
            proc.wait(timeout=timeout_s)
        except subprocess.TimeoutExpired:
            timed_out = True
        kill_group(proc)  # limpia también los procesos hijos que haya dejado
        proc.wait()
        out.join(1)
        err.join(1)
    return {
        "stdout": out.data.decode("utf-8", "replace"),
        "stderr": err.data.decode("utf-8", "replace"),
        "exit_code": proc.returncode,
        "timed_out": timed_out,
        "truncated": out.truncated,
        "duration_ms": int((time.monotonic() - started) * 1000),
    }


def compile_cpp(code, work):
    """Compila el programa; devuelve (ruta del ejecutable, None) o (None, mensaje de error del compilador)."""
    source = os.path.join(work, "main.cpp")
    binary = os.path.join(work, "main")
    with open(source, "w", encoding="utf-8") as f:
        f.write(code)

    def limits():
        resource.setrlimit(resource.RLIMIT_CPU, (COMPILE_TIMEOUT, COMPILE_TIMEOUT))
        resource.setrlimit(resource.RLIMIT_AS, (COMPILE_MEMORY, COMPILE_MEMORY))
        resource.setrlimit(resource.RLIMIT_FSIZE, (FILE_LIMIT * 4, FILE_LIMIT * 4))
        resource.setrlimit(resource.RLIMIT_CORE, (0, 0))

    env = {"PATH": "/usr/local/bin:/usr/bin:/bin", "LANG": "C.UTF-8", "TMPDIR": work}
    try:
        proc = subprocess.run(["g++", "-std=c++20", "-O1", "-pipe", "-Wall", "-o", binary, source],
                              capture_output=True, timeout=COMPILE_TIMEOUT + 5, cwd=work, env=env,
                              preexec_fn=limits)
    except subprocess.TimeoutExpired:
        return None, "El compilador tardó demasiado."
    if proc.returncode != 0:
        message = proc.stderr.decode("utf-8", "replace").replace(work + "/", "")
        return None, message[-MAX_COMPILE_ERROR:]
    return binary, None


def main():
    request = json.loads(sys.stdin.read())
    deadline = time.monotonic() + request["total_timeout_ms"] / 1000
    code = request["code"]
    language = request.get("language", "python")
    cases = []
    with tempfile.TemporaryDirectory(dir="/tmp") as work:
        command = [sys.executable, "-I", "-c", code]
        if language == "cpp":
            binary, error = compile_cpp(code, work)
            if error is not None:
                failed = {"stdout": "", "stderr": error, "exit_code": 1, "timed_out": False, "truncated": False,
                          "compile_error": True, "duration_ms": 0}
                sys.stdout.write(json.dumps({"checks": [], "cases": [dict(failed) for _ in request["cases"]]}))
                return
            command = [binary]
        for case in request["cases"]:
            if time.monotonic() >= deadline:
                cases.append({"stdout": "", "stderr": "", "exit_code": None, "timed_out": True, "truncated": False,
                              "duration_ms": 0})
                continue
            cases.append(run_case(command, case["stdin"], request["case_timeout_ms"], deadline))
    checks = run_checks(code, request["checks"]) if language == "python" else []
    result = {"checks": checks, "cases": cases}
    sys.stdout.write(json.dumps(result))
    sys.stdout.flush()


if __name__ == "__main__":
    main()
