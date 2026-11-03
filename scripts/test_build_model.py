import os
import tempfile
import unittest
from pathlib import Path
from unittest import mock

from build_model import java_home


class JavaHomeTest(unittest.TestCase):
    def test_pinned_toolchain_wins_over_environment(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            (root / '.toolchains/jdk-25.0.1').mkdir(parents=True)
            (root / '.toolchains/jdk-25.0.4').mkdir()
            with mock.patch.dict(os.environ, {'JAVA_HOME': '/opt/jdk17'}):
                self.assertEqual(root / '.toolchains/jdk-25.0.4', java_home(root))

    def test_environment_then_path_java(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            with mock.patch.dict(os.environ, {'JAVA_HOME': '/opt/jdk25'}):
                self.assertEqual(Path('/opt/jdk25'), java_home(root))
            with mock.patch.dict(os.environ, {}, clear=True):
                self.assertIsNone(java_home(root))
