"""Servidor HTTP del runner. Solo biblioteca estándar: menos dependencias que auditar."""

import hmac
import json
import logging
import os
import sys
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

from .executor import Busy, Executor, SandboxFailure, Settings
from .protocol import InvalidRequest, parse

MAX_BODY = 64 * 1024
log = logging.getLogger("runner")


def make_handler(executor: Executor, token: str):
    class Handler(BaseHTTPRequestHandler):
        server_version = "runner"
        sys_version = ""

        def log_message(self, fmt, *args):  # nunca se registra el cuerpo (contiene código del estudiante)
            log.info("%s %s", self.address_string(), fmt % args)

        def _send(self, status: int, body: dict) -> None:
            data = json.dumps(body).encode()
            self.send_response(status)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(data)))
            self.send_header("Cache-Control", "no-store")
            self.end_headers()
            self.wfile.write(data)

        def do_GET(self):
            if self.path == "/healthz":
                ok = executor.docker_ok()
                self._send(200 if ok else 503, {"status": "ok" if ok else "docker_unavailable"})
            else:
                self._send(404, {"error": "NOT_FOUND"})

        def do_POST(self):
            if self.path != "/v1/run":
                return self._send(404, {"error": "NOT_FOUND"})
            supplied = self.headers.get("Authorization", "")
            if not hmac.compare_digest(supplied.encode(), f"Bearer {token}".encode()):
                return self._send(401, {"error": "UNAUTHORIZED"})
            try:
                length = int(self.headers.get("Content-Length", ""))
            except ValueError:
                return self._send(411, {"error": "LENGTH_REQUIRED"})
            if not 0 < length <= MAX_BODY:
                return self._send(413, {"error": "BODY_TOO_LARGE"})
            try:
                request = parse(json.loads(self.rfile.read(length)))
            except (InvalidRequest, ValueError) as e:
                return self._send(400, {"error": "INVALID_REQUEST", "detail": str(e)[:200]})
            try:
                result = executor.run(request)
            except Busy:
                return self._send(503, {"error": "BUSY"})
            except SandboxFailure as e:
                log.warning("fallo del sandbox: %s", e)
                return self._send(502, {"error": "SANDBOX_FAILURE"})
            self._send(200, {"status": "ok", **result})

    return Handler


def main() -> None:
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
    token = os.environ.get("RUNNER_TOKEN", "")
    if len(token) < 16:
        sys.exit("RUNNER_TOKEN es obligatorio y debe tener al menos 16 caracteres")
    settings = Settings(
        image=os.environ.get("SANDBOX_IMAGE_PYTHON", "plataforma-sandbox-python:local"),
        cpp_image=os.environ.get("SANDBOX_IMAGE_CPP", "plataforma-sandbox-cpp:local"),
        runtime=os.environ.get("SANDBOX_RUNTIME") or None,
        max_concurrent=int(os.environ.get("RUNNER_MAX_CONCURRENT", "4")),
    )
    executor = Executor(settings)
    removed = executor.cleanup_leftovers()
    if removed:
        log.info("borrados %d contenedores sandbox huérfanos", removed)
    server = ThreadingHTTPServer((os.environ.get("RUNNER_BIND", "0.0.0.0"), int(os.environ.get("RUNNER_PORT", "8090"))),
                                 make_handler(executor, token))
    log.info("runner escuchando; imagen=%s runtime=%s", settings.image, settings.runtime or "default")
    server.serve_forever()


if __name__ == "__main__":
    main()
