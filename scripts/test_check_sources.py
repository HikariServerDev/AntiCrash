import tempfile
import unittest
from pathlib import Path

from check_sources import collect


class SourceLayoutTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)

    def write(self, path, content="class Example {}"):
        target = self.root / path
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(content)

    def test_shared_and_version_sources_combine_without_leaking_other_versions(self):
        self.write("common/src/main/java/Common.java")
        self.write("shared/old/src/main/java/OldApi.java")
        self.write("versions/new/src/main/java/NewApi.java")
        groups = {"common": ["old", "new"], "shared/old": ["old"]}
        self.assertEqual({"src/main/java/Common.java", "src/main/java/NewApi.java"},
                         set(collect(self.root, "new", groups)))
        self.assertEqual({"src/main/java/Common.java", "src/main/java/OldApi.java"},
                         set(collect(self.root, "old", groups)))

    def test_identical_duplicate_classes_are_rejected(self):
        self.write("common/src/main/java/Example.java")
        self.write("versions/new/src/main/java/Example.java")
        with self.assertRaisesRegex(ValueError, "duplicate source"):
            collect(self.root, "new", {"common": ["new"]})

    def test_resource_collisions_are_rejected_but_main_and_test_are_separate(self):
        self.write("common/src/main/resources/config.json", "{}")
        self.write("versions/new/src/test/resources/config.json", "{}")
        self.assertEqual(2, len(collect(self.root, "new", {"common": ["new"]})))
        self.write("versions/new/src/main/resources/config.json", "{}")
        with self.assertRaisesRegex(ValueError, "duplicate source"):
            collect(self.root, "new", {"common": ["new"]})


if __name__ == "__main__":
    unittest.main()
