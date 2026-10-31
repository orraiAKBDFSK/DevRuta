"""Lanza un contenedor sandbox por envío mediante la CLI de Docker.

El código del estudiante nunca se ejecuta en este proceso: viaja por stdin al contenedor, que no tiene red, ni
volúmenes, ni capacidades, ni sistema de archivos escribible (salvo un /tmp pequeño en memoria).
"""

import json
import subprocess
import threading
import uuid
from dataclasses import dataclass

LABEL = "plataforma.sandbox=1"


@dataclass(frozen=True)
class Settings:
    image: str  # imagen de Python (la de siempre)
    cpp_image: str = "plataforma-sandbox-cpp:local"
    docker_bin: str = "docker"
    runtime: str | None = None  # p. ej. "runsc" (gVisor) en producción
    memory: str = "256m"
    cpus: str = "1"
    pids: int = 64
    max_concurrent: int = 4


class Busy(Exception):
    pass


class SandboxFailure(Exception):
    pass


def docker_args(settings: Settings, name: str, language: str = "python") -> list[str]:
    cpp = language == "cpp"
    # El compilador necesita más memoria y poder ejecutar el binario que genera, así que /tmp admite ejecución.
    memory = "768m" if cpp else settings.memory
    tmpfs = "/tmp:rw,exec,nosuid,nodev,size=64m" if cpp else "/tmp:rw,noexec,nosuid,nodev,size=32m"
    args = [
        settings.docker_bin, "run", "--rm", "-i", "--name", name, "--label", LABEL,
        "--network", "none",
        "--memory", memory, "--memory-swap", memory,
        "--cpus", settings.cpus, "--pids-limit", str(settings.pids),
        "--cap-drop", "ALL", "--security-opt", "no-new-privileges",
        "--read-only", "--tmpfs", tmpfs,
        "--user", "65534:65534",
        "--ulimit", "nofile=256:256", "--ulimit", "core=0",
        "--hostname", "sandbox",
    ]
    if settings.runtime:
        args += ["--runtime", settings.runtime]
    return args + [settings.cpp_image if cpp else settings.image]


class Executor:
    def __init__(self, settings: Settings):
        self.settings = settings
        self._slots = threading.BoundedSemaphore(settings.max_concurrent)

    def cleanup_leftovers(self) -> int:
        """Borra contenedores sandbox que hayan quedado de una ejecución anterior (p. ej. tras un reinicio)."""
        listed = subprocess.run([self.settings.docker_bin, "ps", "-aq", "--filter", f"label={LABEL}"],
                                capture_output=True, text=True, timeout=30)
        ids = listed.stdout.split()
        if ids:
            subprocess.run([self.settings.docker_bin, "rm", "-f", *ids], capture_output=True, timeout=60)
        return len(ids)

    def docker_ok(self) -> bool:
        try:
            return subprocess.run([self.settings.docker_bin, "version", "--format", "{{.Server.Version}}"],
                                  capture_output=True, timeout=5).returncode == 0
        except (OSError, subprocess.TimeoutExpired):
            return False

    def run(self, request: dict) -> dict:
        if not self._slots.acquire(timeout=2):
            raise Busy()
        try:
            return self._run(request)
        finally:
            self._slots.release()

    def _run(self, request: dict) -> dict:
        name = f"plat-sbx-{uuid.uuid4().hex[:16]}"
        payload = {k: request[k] for k in ("language", "code", "cases", "checks", "case_timeout_ms", "total_timeout_ms")}
        wall = request["total_timeout_ms"] / 1000 + (30 if request["language"] == "cpp" else 8)  # arranque y compilación
        proc = subprocess.Popen(docker_args(self.settings, name, request["language"]), stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                                stderr=subprocess.PIPE, text=True, encoding="utf-8")
        try:
            out, _ = proc.communicate(json.dumps(payload), timeout=wall)
        except subprocess.TimeoutExpired:
            self._kill(name)
            proc.kill()
            proc.communicate()
            raise SandboxFailure("tiempo total agotado") from None
        if proc.returncode != 0:
            self._kill(name)
            raise SandboxFailure(f"el contenedor terminó con código {proc.returncode}")
        try:
            result = json.loads(out)
            if not isinstance(result.get("cases"), list) or not isinstance(result.get("checks"), list):
                raise ValueError
        except ValueError:
            raise SandboxFailure("respuesta del sandbox no válida") from None
        return result

    def _kill(self, name: str) -> None:
        subprocess.run([self.settings.docker_bin, "rm", "-f", name], capture_output=True, timeout=30)
