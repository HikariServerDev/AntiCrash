from pathlib import Path
import os
import tempfile
import unittest
from unittest.mock import patch

from check_conditions import cached_libraries, only_jar


class ConditionClasspathTest(unittest.TestCase):
    def test_uses_pinned_plugin_and_wrapper_from_configured_cache(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / 'gradle/wrapper').mkdir(parents=True)
            (root / 'settings.gradle.kts').write_text(
                'plugins { id("dev.kikugie.stonecutter") version "0.9.8" }')
            (root / 'gradle/wrapper/gradle-wrapper.properties').write_text(
                'distributionUrl=https://example.invalid/gradle-9.6.0-bin.zip')
            cache = root / 'cache'
            names = [
                'caches/modules-2/files-2.1/dev.kikugie/stonecutter/0.9.8/hash/stonecutter-0.9.8.jar',
                'wrapper/dists/gradle-9.6.0-bin/hash/gradle-9.6.0/lib/kotlin-stdlib-2.3.21.jar',
                'caches/modules-2/files-2.1/dev.kikugie/stonecutter/0.10.0/hash/other.jar',
                'caches/modules-2/files-2.1/dev.kikugie/stonecutter/0.9.8/hash/stonecutter-0.9.8-sources.jar',
            ]
            for name in names:
                jar = cache / name
                jar.parent.mkdir(parents=True, exist_ok=True)
                jar.touch()
            with patch.dict(os.environ, {'GRADLE_USER_HOME': str(cache)}):
                self.assertEqual(([cache / names[0]], [cache / names[1]]), cached_libraries(root))

    def test_missing_or_ambiguous_dependencies_are_rejected(self):
        for paths in ([], [Path('one.jar'), Path('two.jar')]):
            with self.subTest(paths=paths), self.assertRaisesRegex(ValueError, 'pass its path explicitly'):
                only_jar(paths, 'Stonecutter')

    def test_exactly_one_dependency_is_accepted(self):
        self.assertEqual(Path('one.jar'), only_jar([Path('one.jar')], 'Stonecutter'))

    def test_identical_wrapper_copies_are_one_binary_dependency(self):
        with tempfile.TemporaryDirectory() as directory:
            jars = [Path(directory) / name / 'kotlin.jar' for name in ['old-url', 'new-url']]
            for jar in jars:
                jar.parent.mkdir()
                jar.write_bytes(b'same pinned binary')
            self.assertEqual(sorted(jars)[0], only_jar(jars, 'Kotlin'))

    def test_different_cached_binaries_are_still_ambiguous(self):
        with tempfile.TemporaryDirectory() as directory:
            jars = [Path(directory) / name for name in ['one.jar', 'two.jar']]
            for i, jar in enumerate(jars):
                jar.write_bytes(bytes([i]))
            with self.assertRaisesRegex(ValueError, 'pass its path explicitly'):
                only_jar(jars, 'Kotlin')
