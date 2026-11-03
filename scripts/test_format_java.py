import unittest
from format_java import preserve_directives, java_sources
from pathlib import Path
import tempfile


class StonecutterFormattingTest(unittest.TestCase):
    def test_restore_only_real_comment_markers(self):
        source = '  // ? if >=26.1 {\nString value = "// ?";\n*/// ?}\n'
        expected = '  //? if >=26.1 {\nString value = "// ?";\n*///?}\n'
        self.assertEqual(expected, preserve_directives(source))
        self.assertEqual(expected, preserve_directives(expected))

    def test_ghost_terminators_do_not_accumulate_blank_lines(self):
        source = '/*inactive();\n\n */\n  // ?} else {\n'
        compact = '/*inactive();\n*///?} else {\n'
        self.assertEqual(compact, preserve_directives(source))
        self.assertEqual(compact, preserve_directives(compact))

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
