import unittest

from plataforma_runner.protocol import InvalidRequest, parse


def valid(**overrides):
    base = {"language": "python", "code": "print(1)", "cases": [{"stdin": ""}]}
    return {**base, **overrides}


class ProtocolTests(unittest.TestCase):

    def test_accepts_a_minimal_request_and_fills_defaults(self):
        request = parse(valid())
        self.assertEqual(request["case_timeout_ms"], 3000)
        self.assertEqual(request["checks"], [])

    def test_rejects_what_does_not_fit(self):
        bad = [
            None, [], valid(language="rust"), valid(code=""), valid(code="  "), valid(code=5),
            valid(code="x" * 20_001), valid(cases=[]), valid(cases=[{"stdin": ""}] * 11), valid(cases=["x"]),
            valid(cases=[{"stdin": "x" * 10_001}]), valid(case_timeout_ms=99_999), valid(case_timeout_ms=True),
            valid(total_timeout_ms=0), valid(checks="x"), valid(checks=[{"kind": "rm -rf"}]),
            valid(checks=[{"kind": "requires_function", "name": "a b"}]),
            valid(checks=[{"kind": "requires_function", "name": "__import__('os')"}]),
            valid(checks=[{"kind": "requires_assignment", "count_min": 0}]),
        ]
        for body in bad:
            with self.subTest(body=str(body)[:60]), self.assertRaises(InvalidRequest):
                parse(body)

    def test_drops_unknown_fields_instead_of_forwarding_them(self):
        request = parse(valid(image="evil", privileged=True))
        self.assertNotIn("image", request)
        self.assertNotIn("privileged", request)


if __name__ == "__main__":
    unittest.main()
