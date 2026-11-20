import unittest
from scripts.tag_policy import validate_candidate, parse_source_version


class TagPolicyTest(unittest.TestCase):
    def candidate(self, **changes):
        values = dict(channel="dev", tag="dev-build/2",
                      source_text="versionName=6.0.0\nversionCode=6\n",
                      source_sha="a"*40, source_tree="b"*40,
                      branch_sha="a"*40, branch_tree="b"*40)
        return validate_candidate(**(values | changes))

    def test_dev(self):
        result = self.candidate(existing_refs={"dev-build/1": "c"*40, "dev-build/2": "a"*40})
        self.assertEqual((result.version_name, result.version_code, result.application_id),
                         ("6.0.0-dev", 2, "com.jaguar.attendancetracker.dev"))
        self.assertEqual(self.candidate(tag="dev-build/2100000000").version_code, 2100000000)

    def test_invalid_dev(self):
        for tag in ("v6.0.0", "dev-build/0", "dev-build/02", "dev-build/-1",
                    "dev-build/２", "dev-build/2100000001"):
            with self.subTest(tag=tag), self.assertRaises(ValueError):
                self.candidate(tag=tag)
        for args in ({"existing_refs": {"dev-build/3": "c"*40}},
                     {"existing_refs": {"dev-build/2": "c"*40}},
                     {"successful_tags": {"dev-build/2"}},
                     {"branch_sha": "c"*40}, {"branch_tree": "c"*40},
                     {"source_tree": "abc"}):
            with self.subTest(args=args), self.assertRaises(ValueError):
                self.candidate(**args)

    def test_public(self):
        result = self.candidate(channel="public", tag="v6.0.0", previous_public=[("v5.0.0", 5)])
        self.assertEqual((result.version_name, result.version_code, result.application_id),
                         ("6.0.0", 6, "com.jaguar.attendancetracker"))
        for previous in ([("v5.0.0", 6)], [("v6.0.0", 5)], [("v5.0.0", "5")],
                         [("v05.0.0", 5)], [("v5.0.0", True)]):
            with self.subTest(previous=previous), self.assertRaises(ValueError):
                self.candidate(channel="public", tag="v6.0.0", previous_public=previous)
        with self.assertRaises(ValueError):
            self.candidate(channel="public", tag="v6.0.1")

    def test_source(self):
        for text in ("versionName=06.0.0\nversionCode=6", "versionName=6.0.0\nversionCode=06",
                     "versionName=6.0.0\nversionCode=6\nversionCode=7", "versionName=6.0.0",
                     "versionName=6.0.0\nversionCode=6\nunknown=x"):
            with self.subTest(text=text), self.assertRaises(ValueError):
                parse_source_version(text)
