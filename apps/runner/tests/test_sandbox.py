"""Pruebas de aislamiento: ejecutan código hostil de verdad dentro del sandbox. Necesitan Docker y la imagen
`plataforma-sandbox-python:local` (si faltan, se omiten y lo dicen)."""

import json
import subprocess
import time
import unittest

from plataforma_runner.executor import LABEL, Executor, SandboxFailure, Settings
from plataforma_runner.protocol import parse

IMAGE = "plataforma-sandbox-python:local"


def sandbox_available() -> bool:
    try:
        return subprocess.run(["docker", "image", "inspect", IMAGE], capture_output=True, timeout=20).returncode == 0
    except (OSError, subprocess.TimeoutExpired):
        return False


@unittest.skipUnless(sandbox_available(), f"Docker o la imagen {IMAGE} no están disponibles")
class SandboxTests(unittest.TestCase):

    @classmethod
    def setUpClass(cls):
        cls.executor = Executor(Settings(image=IMAGE))

    def run_code(self, code, stdins=("",), **extra):
        request = parse({"language": "python", "code": code, "cases": [{"stdin": s} for s in stdins], **extra})
        return self.executor.run(request)

    def first(self, code, **extra):
        return self.run_code(code, **extra)["cases"][0]

    def test_runs_code_and_returns_output(self):
        case = self.first("print(input().upper())", )
        self.assertEqual(case["exit_code"], 1)  # sin entrada: EOFError
        result = self.run_code("print(input().upper())", stdins=["hola\n"])
        self.assertEqual(result["cases"][0]["stdout"], "HOLA\n")
        self.assertEqual(result["cases"][0]["exit_code"], 0)

    def test_infinite_loop_is_cut_by_the_timeout(self):
        started = time.monotonic()
        case = self.first("while True: pass", case_timeout_ms=1000)
        self.assertTrue(case["timed_out"])
        self.assertLess(time.monotonic() - started, 8)

    def test_remaining_cases_are_marked_timed_out_when_total_time_runs_out(self):
        result = self.run_code("while True: pass", stdins=["", "", ""], case_timeout_ms=1000, total_timeout_ms=1500)
        self.assertTrue(all(c["timed_out"] for c in result["cases"]))

    def test_fork_bomb_is_contained_and_the_sandbox_survives(self):
        code = "import os\nwhile True:\n    try:\n        os.fork()\n    except OSError:\n        pass\n"
        started = time.monotonic()
        result = self.run_code(code, case_timeout_ms=1500)
        self.assertLess(time.monotonic() - started, 12)
        self.assertTrue(result["cases"][0]["timed_out"] or result["cases"][0]["exit_code"] is not None)
        self.assertEqual(self.first("print('vivo')")["stdout"], "vivo\n")

    def test_memory_bomb_raises_memory_error_instead_of_taking_the_container_down(self):
        case = self.first("x = bytearray(2 * 1024**3)")
        self.assertNotEqual(case["exit_code"], 0)
        self.assertIn("MemoryError", case["stderr"])

    def test_there_is_no_network(self):
        code = ("import socket\n"
                "s = socket.socket(); s.settimeout(2)\n"
                "try:\n    s.connect(('1.1.1.1', 80)); print('CONECTADO')\n"
                "except OSError as e:\n    print('sin red')\n")
        self.assertEqual(self.first(code)["stdout"], "sin red\n")
        self.assertEqual(self.first("import socket\ntry:\n    socket.gethostbyname('example.com'); print('DNS')\n"
                                    "except OSError:\n    print('sin dns')")["stdout"], "sin dns\n")

    def test_root_filesystem_is_read_only_and_harness_cannot_be_modified(self):
        for path in ("/etc/pwned", "/opt/harness.py", "/usr/local/bin/pwned"):
            case = self.first(f"open({path!r}, 'w').write('x')")
            self.assertNotEqual(case["exit_code"], 0, path)

    def test_runs_unprivileged_with_no_capabilities(self):
        case = self.first("import os\nprint(os.getuid())\nprint([l for l in open('/proc/self/status') "
                          "if l.startswith('CapEff')][0].strip())")
        self.assertEqual(case["stdout"], "65534\nCapEff:\t0000000000000000\n")

    def test_huge_output_is_truncated_and_stops_the_program(self):
        started = time.monotonic()
        case = self.first("while True: print('x' * 1000)")
        self.assertTrue(case["truncated"])
        self.assertLessEqual(len(case["stdout"]), 64 * 1024)
        self.assertLess(time.monotonic() - started, 6)

    def test_filling_the_disk_fails_without_hurting_the_host(self):
        code = ("n = 0\ntry:\n    while True:\n        open(f'f{n}', 'w').write('x' * 4_000_000); n += 1\n"
                "except OSError as e:\n    print('lleno', n)\n")
        case = self.first(code)
        self.assertTrue(case["stdout"].startswith("lleno"), case)

    def test_the_program_cannot_forge_the_result(self):
        forged = '{"checks": [], "cases": [{"stdout": "OK", "exit_code": 0}]}'
        result = self.run_code(f"print({forged!r})\nimport os\nos.write(1, b'x')")
        self.assertEqual(len(result["cases"]), 1)
        self.assertIn("checks", result["cases"][0]["stdout"])  # queda dentro de SU salida, no sustituye al resultado

    def test_the_program_cannot_kill_the_harness(self):
        # El arnés es el PID 1 del contenedor: el kernel ignora SIGKILL enviado desde dentro de su propio espacio.
        result = self.run_code("import os, signal\nos.kill(os.getppid(), signal.SIGKILL)\nprint('sigue')")
        self.assertEqual(result["cases"][0]["stdout"], "sigue\n")

    def test_a_sandbox_that_cannot_start_fails_closed(self):
        broken = Executor(Settings(image="plataforma-sandbox-no-existe:local"))
        with self.assertRaises(SandboxFailure):
            broken.run(parse({"language": "python", "code": "print(1)", "cases": [{"stdin": ""}]}))

    def test_cases_do_not_share_files(self):
        code = ("import os\nif os.path.exists('marca.txt'):\n    print('COMPARTIDO')\nelse:\n"
                "    open('marca.txt', 'w').write('x'); print('limpio')\n")
        result = self.run_code(code, stdins=["", ""])
        self.assertEqual([c["stdout"] for c in result["cases"]], ["limpio\n", "limpio\n"])

    def test_no_docker_socket_or_host_secrets_are_visible(self):
        case = self.first("import os\nprint(os.path.exists('/var/run/docker.sock'), os.path.exists('/content'), "
                          "os.environ.get('RUNNER_TOKEN'))")
        self.assertEqual(case["stdout"], "False False None\n")

    def test_static_checks_run_inside_the_sandbox(self):
        result = self.run_code("def es_par(n):\n    return n % 2 == 0\n",
                               checks=[{"kind": "requires_function", "name": "es_par", "returns": True},
                                       {"kind": "requires_function", "name": "otra"},
                                       {"kind": "requires_assignment", "count_min": 1}])
        self.assertEqual([c["ok"] for c in result["checks"]], [True, False, False])

    def test_syntax_error_is_reported_and_fails_static_checks(self):
        result = self.run_code("def f(:\n", checks=[{"kind": "requires_function", "name": "f"}])
        self.assertNotEqual(result["cases"][0]["exit_code"], 0)
        self.assertIn("SyntaxError", result["cases"][0]["stderr"])
        self.assertFalse(result["checks"][0]["ok"])

    def test_no_sandbox_containers_are_left_behind(self):
        self.first("print(1)")
        self.run_code("while True: pass", case_timeout_ms=500)
        listed = subprocess.run(["docker", "ps", "-aq", "--filter", f"label={LABEL}"], capture_output=True, text=True)
        self.assertEqual(listed.stdout.strip(), "")

    def test_docker_command_has_the_expected_hardening(self):
        from plataforma_runner.executor import docker_args
        args = " ".join(docker_args(Settings(image=IMAGE), "x"))
        for flag in ("--network none", "--cap-drop ALL", "--read-only", "no-new-privileges", "--pids-limit",
                     "--memory-swap", "--user 65534:65534"):
            self.assertIn(flag, args)
        self.assertNotIn("-v ", args)
        self.assertNotIn("--privileged", args)
        json.dumps(args)


if __name__ == "__main__":
    unittest.main()
