import unittest
from format_java import preserve_directives, java_sources
from pathlib import Path
import tempfile


class StonecutterFormattingTest(unittest.TestCase):
    def test_restore_only_real_comment_markers(self):
        source = '  // ? if >=26.1 {\nString value = "// ?";\nString block = """\n// ?}\n""";\n/*inactive();\n*/// ?}\n'
        expected = '  //? if >=26.1 {\nString value = "// ?";\nString block = """\n// ?}\n""";\n/*inactive();\n*///?}\n'
        self.assertEqual(expected, preserve_directives(source))
        self.assertEqual(expected, preserve_directives(expected))

    def test_ghost_terminators_do_not_accumulate_blank_lines(self):
        source = '/*inactive();\n\n */\n  // ?} else {\n'
        compact = '/*inactive();\n\n *///?} else {\n'
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


class BlockCommentPreservationTest(unittest.TestCase):
    def test_ghost_body_is_restored_verbatim(self):
        from format_java import mask_block_comments, restore_block_comments
        source = '//? if >1.18.2 {\n/*call(\n                nested());\n/^ nested comment ^/\n/*inactive();\n*///?}\n'
        masked, blocks = mask_block_comments(source)
        self.assertNotIn('nested()', masked)
        self.assertEqual(source, restore_block_comments(masked, blocks))

    def test_strings_characters_text_blocks_and_line_comments_are_not_masked(self):
        from format_java import mask_block_comments, restore_block_comments
        source = 'String a = "/* literal */"; char c = \'"\';\nString b = """\n/* text block */\n""";\n// /* line comment */\n/* real */'
        masked, blocks = mask_block_comments(source)
        self.assertEqual(['/* real */'], blocks)
        self.assertEqual(source, restore_block_comments(masked, blocks))

    def test_missing_duplicate_or_unknown_placeholder_is_rejected(self):
        from format_java import mask_block_comments, restore_block_comments
        masked, blocks = mask_block_comments('/* saved */')
        for invalid in ['', masked + masked, masked.replace('_0__', '_99__')]:
            with self.subTest(text=invalid), self.assertRaises(ValueError):
                restore_block_comments(invalid, blocks)


    def test_javadoc_before_scope_end_keeps_its_separation(self):
        source = '/** Documentation. */\n\n//?}\n'
        self.assertEqual(source, preserve_directives(source))
