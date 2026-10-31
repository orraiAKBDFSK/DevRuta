"""Pruebas de aislamiento de C++: compilan y ejecutan código hostil de verdad. Necesitan Docker y la imagen
`plataforma-sandbox-cpp:local` (si faltan, se omiten)."""

import subprocess
import time
import unittest

from plataforma_runner.executor import Executor, Settings
from plataforma_runner.protocol import InvalidRequest, parse

IMAGE = "plataforma-sandbox-cpp:local"


def available() -> bool:
    try:
        return subprocess.run(["docker", "image", "inspect", IMAGE], capture_output=True, timeout=20).returncode == 0
    except (OSError, subprocess.TimeoutExpired):
        return False


def program(body: str, includes: str = "#include <iostream>") -> str:
    return f"{includes}\nint main() {{\n{body}\n}}\n"


@unittest.skipUnless(available(), f"Docker o la imagen {IMAGE} no están disponibles")
class CppSandboxTests(unittest.TestCase):

    @classmethod
    def setUpClass(cls):
        cls.executor = Executor(Settings(image="plataforma-sandbox-python:local", cpp_image=IMAGE))

    def run_code(self, code, stdins=("",), **extra):
        request = parse({"language": "cpp", "code": code, "cases": [{"stdin": s} for s in stdins], **extra})
        return self.executor.run(request)

    def first(self, code, **extra):
        return self.run_code(code, **extra)["cases"][0]

    def test_compiles_and_runs_with_input(self):
        code = program("int a, b; std::cin >> a >> b; std::cout << a + b << \"\\n\";")
        result = self.run_code(code, stdins=["2 3\n", "10 -4\n"])
        self.assertEqual([c["stdout"] for c in result["cases"]], ["5\n", "6\n"])
        self.assertTrue(all(c["exit_code"] == 0 for c in result["cases"]))

    def test_a_compile_error_is_reported_for_every_case_and_runs_nothing(self):
        result = self.run_code(program("std::cout << undefined_name;"), stdins=["", ""])
        for case in result["cases"]:
            self.assertTrue(case["compile_error"])
            self.assertNotEqual(case["exit_code"], 0)
            self.assertIn("undefined_name", case["stderr"])
            self.assertNotIn("/tmp", case["stderr"])  # no filtra rutas internas
        self.assertEqual(len(result["cases"]), 2)

    def test_a_program_that_compiles_is_not_marked_as_compile_error(self):
        self.assertFalse(self.first(program("std::cout << 1;")).get("compile_error", False))

    def test_infinite_loop_is_cut_by_the_timeout(self):
        started = time.monotonic()
        case = self.first(program("while (true) {}"), case_timeout_ms=1000)
        self.assertTrue(case["timed_out"])
        self.assertLess(time.monotonic() - started, 15)

    def test_infinite_compile_time_computation_is_stopped(self):
        code = ("constexpr long long f(long long n) { long long s = 0; for (long long i = 0; i < n; ++i) s += i; return s; }\n"
                "constexpr long long x = f(1000000000000LL);\nint main() { return x; }\n")
        started = time.monotonic()
        case = self.first(code)
        self.assertNotEqual(case["exit_code"], 0)
        self.assertLess(time.monotonic() - started, 45)

    def test_fork_bomb_is_contained(self):
        code = program("while (true) fork();", "#include <unistd.h>")
        started = time.monotonic()
        result = self.run_code(code, case_timeout_ms=1500)
        self.assertLess(time.monotonic() - started, 30)
        self.assertTrue(result["cases"][0]["timed_out"] or result["cases"][0]["exit_code"] is not None)
        self.assertEqual(self.first(program('std::cout << "vivo";'))["stdout"], "vivo")

    def test_memory_bomb_fails_instead_of_taking_the_container_down(self):
        code = program("volatile char* p = (volatile char*)malloc(3000000000ULL); if (!p) return 3;"
                       " for (unsigned long long i = 0; i < 3000000000ULL; i += 4096) p[i] = 1; std::cout << 'x';",
                       "#include <iostream>\n#include <cstdlib>")
        case = self.first(code)
        self.assertNotEqual(case["exit_code"], 0)

    def test_there_is_no_network(self):
        code = program("""
            int s = socket(AF_INET, SOCK_STREAM, 0);
            sockaddr_in a{}; a.sin_family = AF_INET; a.sin_port = htons(80); inet_pton(AF_INET, "1.1.1.1", &a.sin_addr);
            std::cout << (connect(s, (sockaddr*)&a, sizeof a) == 0 ? "CONECTADO" : "sin red");""",
                       "#include <iostream>\n#include <sys/socket.h>\n#include <netinet/in.h>\n#include <arpa/inet.h>")
        self.assertEqual(self.first(code)["stdout"], "sin red")

    def test_the_root_filesystem_is_read_only(self):
        code = program('FILE* f = fopen("/etc/pwned", "w"); std::cout << (f ? "ESCRIBIO" : "denegado");',
                       "#include <cstdio>\n#include <iostream>")
        self.assertEqual(self.first(code)["stdout"], "denegado")

    def test_runs_unprivileged_with_no_capabilities(self):
        code = program('std::cout << getuid() << "\\n"; std::ifstream f("/proc/self/status"); std::string l;'
                       ' while (std::getline(f, l)) if (l.rfind("CapEff", 0) == 0) std::cout << l << "\\n";',
                       "#include <iostream>\n#include <fstream>\n#include <string>\n#include <unistd.h>")
        self.assertEqual(self.first(code)["stdout"], "65534\nCapEff:\t0000000000000000\n")

    def test_huge_output_is_truncated(self):
        case = self.first(program('while (true) std::cout << std::string(1000, \'x\');', "#include <iostream>\n#include <string>"))
        self.assertTrue(case["truncated"])
        self.assertLessEqual(len(case["stdout"]), 64 * 1024)

    def test_including_a_system_file_does_not_leak_anything_useful(self):
        result = self.first('#include "/etc/shadow"\nint main() {}\n')
        self.assertTrue(result["compile_error"])
        self.assertNotIn("root:", result["stderr"])

    def test_cases_do_not_share_files(self):
        code = program('std::ifstream in("marca.txt"); if (in) { std::cout << "COMPARTIDO"; return 0; }'
                       ' std::ofstream("marca.txt") << "x"; std::cout << "limpio";',
                       "#include <iostream>\n#include <fstream>")
        result = self.run_code(code, stdins=["", ""])
        self.assertEqual([c["stdout"] for c in result["cases"]], ["limpio", "limpio"])

    def test_no_static_checks_are_applied_to_cpp(self):
        request = parse({"language": "cpp", "code": "int main(){}", "cases": [{"stdin": ""}],
                         "checks": [{"kind": "requires_function", "name": "f"}]})
        self.assertEqual(request["checks"], [])

    def test_unknown_languages_are_still_rejected(self):
        with self.assertRaises(InvalidRequest):
            parse({"language": "rust", "code": "x", "cases": [{"stdin": ""}]})


if __name__ == "__main__":
    unittest.main()
