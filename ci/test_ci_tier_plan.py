import importlib.util
import pathlib
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("ci_tier_plan", ROOT / "ci" / "ci_tier_plan.py")
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class ThreeStagePlanTests(unittest.TestCase):
    def test_lightweight_has_no_emulator(self):
        p = module.make_plan("dpc", "lightweight")
        self.assertEqual(p["emulator_count"], 0)
        self.assertEqual(p["matrix"], {"include": []})

    def test_targeted_is_exactly_one_api(self):
        p = module.make_plan("dpc", "targeted", 28)
        self.assertEqual(p["matrix"], {"include": [{"api": 28}]})
        self.assertEqual(p["emulator_count"], 1)

    def test_targeted_rejects_unsupported(self):
        with self.assertRaises(ValueError):
            module.make_plan("aru-tiker", "targeted", 36)
        with self.assertRaises(ValueError):
            module.make_plan("dpc", "targeted")

    def test_full_fails_closed_without_explicit_approval(self):
        for kwargs in ({}, {"approval_reference": "issue-123"},
                       {"approved_source_sha": "a" * 40}):
            with self.subTest(kwargs=kwargs), self.assertRaises(ValueError):
                module.make_plan("dpc", "full", **kwargs)

    def test_full_rejects_pr(self):
        with self.assertRaises(ValueError):
            module.make_plan("dpc", "full", approval_reference="release-123",
                             approved_source_sha="a" * 40, event_name="pull_request")

    def test_full_matrix_uses_pinned_source(self):
        p = module.make_plan("dpc", "full", approval_reference="approved-release",
                             approved_source_sha="a" * 40, event_name="workflow_dispatch")
        self.assertEqual([v["api"] for v in p["matrix"]["include"]], [28, 29, 35, 36])
        self.assertFalse(p["release_authorized_by_plan"])

    def test_fail_closed_on_unrecognized_inputs(self):
        for app, tier in (("unknown", "lightweight"), ("dpc", "skipped")):
            with self.assertRaises(ValueError):
                module.make_plan(app, tier)


if __name__ == "__main__":
    unittest.main()
