import tempfile
import unittest
from pathlib import Path
from build_matrix import junit_results


class JunitEvidenceTest(unittest.TestCase):
    def test_reports_are_required(self):
        with tempfile.TemporaryDirectory() as temporary:
            with self.assertRaisesRegex(ValueError, 'No JUnit evidence'):
                junit_results(Path(temporary))

    def test_failed_or_errored_suite_never_passes(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            for attribute in ['failures', 'errors']:
                (root / 'TEST-fixture.xml').write_text(f'<testsuite tests="1" {attribute}="1"/>')
                with self.subTest(attribute=attribute), self.assertRaisesRegex(ValueError, 'Failed JUnit'):
                    junit_results(root)

    def test_skips_are_retained_in_evidence(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            (root / 'TEST-a.xml').write_text('<testsuite tests="3" skipped="1"/>')
            (root / 'TEST-b.xml').write_text('<testsuite tests="2"/>')
            self.assertEqual(dict(tests=5, failures=0, errors=0, skipped=1), junit_results(root))
