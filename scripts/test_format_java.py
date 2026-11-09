import os
import hashlib
import io
import urllib.error
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

import format_java
from format_java import FORMATTER, active_version, command, java_sources

ROOT = Path(__file__).resolve().parents[1]


class SourceSelectionTest(unittest.TestCase):
    def test_all_canonical_source_sets_are_checked(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            names = ['src/main/java/Main.java', 'src/test/java/Test.java',
                     'src/qualification/java/Probe.java', 'supervisor/src/main/java/Supervisor.java',
                     'scripts/Parity.java']
            for name in names:
                file = root / name
                file.parent.mkdir(parents=True, exist_ok=True)
                file.write_text('')
            generated = root / 'versions/26.3/build/generated/Generated.java'
            generated.parent.mkdir(parents=True)
            generated.write_text('')
            self.assertEqual(set(names), {p.relative_to(root).as_posix() for p in java_sources(root)})

    def test_active_version_is_read_from_the_stonecutter_build(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            build = root / 'stonecutter.gradle.kts'
            build.write_text('plugins { id("dev.kikugie.stonecutter") }\nstonecutter active "1.21.5"\n')
            self.assertEqual('1.21.5', active_version(root))
            build.write_text('plugins { id("dev.kikugie.stonecutter") }\n')
            with self.assertRaisesRegex(ValueError, 'active version'):
                active_version(root)


class CommandTest(unittest.TestCase):
    def test_formatter_and_processor_share_one_jvm_with_javac_exports(self):
        jars = [Path('formatter.jar'), Path('stonecutter.jar'), Path('kotlin.jar')]
        with patch.object(format_java, 'formatter_jar', return_value=jars[0]), \
                patch.object(format_java, 'cached_libraries', return_value=([jars[1]], [jars[2]])), \
                patch.object(format_java, 'java_home', return_value=None):
            line = command(ROOT, '--self-test')
        self.assertEqual('java', line[0])
        self.assertEqual(os.pathsep.join(map(str, jars)), line[line.index('--class-path') + 1])
        for name in ['api', 'code', 'file', 'parser', 'tree', 'util']:
            self.assertIn(f'--add-exports=jdk.compiler/com.sun.tools.javac.{name}=ALL-UNNAMED', line)
        self.assertEqual([str(ROOT / 'scripts/StonecutterFormat.java'), '--self-test'], line[-2:])


class FormatterDownloadTest(unittest.TestCase):
    def test_transient_failure_retries_then_checks_the_download(self):
        payload = b'verified formatter'
        error = urllib.error.HTTPError(format_java.URL, 504, 'timeout', {}, None)
        with tempfile.TemporaryDirectory() as temporary, \
                patch.object(format_java.urllib.request, 'urlopen', side_effect=[error, io.BytesIO(payload)]) as fetch, \
                patch.object(format_java.time, 'sleep') as sleep, \
                patch.object(format_java, 'SHA256', hashlib.sha256(payload).hexdigest()):
            jar = format_java.formatter_jar(Path(temporary))
            self.assertEqual(payload, jar.read_bytes())
            self.assertEqual(2, fetch.call_count)
            sleep.assert_called_once_with(1)

    def test_transport_retries_are_bounded_and_do_not_publish_a_cache(self):
        with tempfile.TemporaryDirectory() as temporary, \
                patch.object(format_java.urllib.request, 'urlopen', side_effect=TimeoutError('timeout')) as fetch, \
                patch.object(format_java.time, 'sleep'):
            with self.assertRaises(TimeoutError):
                format_java.formatter_jar(Path(temporary))
            self.assertEqual(3, fetch.call_count)
            self.assertEqual([], list(Path(temporary).rglob('*.jar')))

    def test_http_client_errors_are_not_retried(self):
        error = urllib.error.HTTPError(format_java.URL, 404, 'missing', {}, None)
        with tempfile.TemporaryDirectory() as temporary, \
                patch.object(format_java.urllib.request, 'urlopen', side_effect=error) as fetch, \
                patch.object(format_java.time, 'sleep') as sleep:
            with self.assertRaises(urllib.error.HTTPError):
                format_java.formatter_jar(Path(temporary))
            self.assertEqual(1, fetch.call_count)
            sleep.assert_not_called()

    def test_corrupt_payload_is_rejected_without_retry_or_cache(self):
        with tempfile.TemporaryDirectory() as temporary, \
                patch.object(format_java.urllib.request, 'urlopen', return_value=io.BytesIO(b'corrupt')) as fetch, \
                patch.object(format_java.time, 'sleep') as sleep:
            with self.assertRaisesRegex(ValueError, 'checksum'):
                format_java.formatter_jar(Path(temporary))
            self.assertEqual(1, fetch.call_count)
            sleep.assert_not_called()
            self.assertEqual([], list(Path(temporary).rglob('*.jar')))


class StonecutterFormatSelfTest(unittest.TestCase):
    def test_processor_self_test(self):
        jar = ROOT / f'.toolchains/formatters/google-java-format-{FORMATTER}-all-deps.jar'
        if not jar.exists():
            self.skipTest('google-java-format is not cached yet; formatCheck downloads it')
        try:
            line = command(ROOT, '--self-test')
        except ValueError as missing:
            self.skipTest(f'Stonecutter is not in the Gradle cache: {missing}')
        result = subprocess.run(line, cwd=ROOT, capture_output=True, text=True)
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertIn('self-tests passed', result.stdout)


if __name__ == '__main__':
    unittest.main()
