import json
import threading
import unittest
import urllib.error
import urllib.request
from http.server import ThreadingHTTPServer

from plataforma_runner.executor import Busy, SandboxFailure
from plataforma_runner.server import make_handler

TOKEN = "token-de-prueba-0123456789"


class FakeExecutor:
    def __init__(self):
        self.behaviour = lambda request: {"checks": [], "cases": [{"stdout": "ok"}]}
        self.calls = 0

    def run(self, request):
        self.calls += 1
        return self.behaviour(request)

    def docker_ok(self):
        return True


class ServerTests(unittest.TestCase):

    def setUp(self):
        self.executor = FakeExecutor()
        self.server = ThreadingHTTPServer(("127.0.0.1", 0), make_handler(self.executor, TOKEN))
        threading.Thread(target=self.server.serve_forever, daemon=True).start()
        self.base = f"http://127.0.0.1:{self.server.server_address[1]}"

    def tearDown(self):
        self.server.shutdown()
        self.server.server_close()

    def post(self, body, token=TOKEN, raw=None):
        headers = {"Content-Type": "application/json"}
        if token is not None:
            headers["Authorization"] = f"Bearer {token}"
        data = raw if raw is not None else json.dumps(body).encode()
        request = urllib.request.Request(f"{self.base}/v1/run", data=data, headers=headers, method="POST")
        try:
            with urllib.request.urlopen(request) as response:
                return response.status, json.load(response)
        except urllib.error.HTTPError as e:
            return e.code, json.load(e)

    VALID = {"language": "python", "code": "print(1)", "cases": [{"stdin": ""}]}

    def test_runs_a_valid_request(self):
        status, body = self.post(self.VALID)
        self.assertEqual((status, body["status"]), (200, "ok"))

    def test_requires_the_bearer_token_and_never_runs_without_it(self):
        for token in (None, "otro", TOKEN + "x", ""):
            self.assertEqual(self.post(self.VALID, token=token)[0], 401)
        self.assertEqual(self.executor.calls, 0)

    def test_rejects_invalid_and_oversized_bodies_before_executing(self):
        self.assertEqual(self.post({"language": "cpp"})[0], 400)
        self.assertEqual(self.post(None, raw=b"no es json")[0], 400)
        self.assertEqual(self.post(None, raw=b"x" * (64 * 1024 + 1))[0], 413)
        self.assertEqual(self.executor.calls, 0)

    def test_maps_busy_and_sandbox_failures_without_leaking_details(self):
        self.executor.behaviour = lambda r: (_ for _ in ()).throw(Busy())
        self.assertEqual(self.post(self.VALID), (503, {"error": "BUSY"}))
        self.executor.behaviour = lambda r: (_ for _ in ()).throw(SandboxFailure("detalle interno"))
        self.assertEqual(self.post(self.VALID), (502, {"error": "SANDBOX_FAILURE"}))

    def test_health_is_public_and_unknown_paths_are_404(self):
        with urllib.request.urlopen(f"{self.base}/healthz") as response:
            self.assertEqual(response.status, 200)
        with self.assertRaises(urllib.error.HTTPError) as caught:
            urllib.request.urlopen(f"{self.base}/otra")
        self.assertEqual(caught.exception.code, 404)


if __name__ == "__main__":
    unittest.main()
