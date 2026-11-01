import tempfile
import unittest
from pathlib import Path

from check_sources import collect, validate_groups, validate_release


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
        self.write("new/src/main/java/NewApi.java")
        groups = {"common": ["old", "new"], "shared/old": ["old"]}
        self.assertEqual({"src/main/java/Common.java", "src/main/java/NewApi.java"},
                         set(collect(self.root, "new", groups)))
        self.assertEqual({"src/main/java/Common.java", "src/main/java/OldApi.java"},
                         set(collect(self.root, "old", groups)))

    def test_identical_duplicate_classes_are_rejected(self):
        self.write("common/src/main/java/Example.java")
        self.write("new/src/main/java/Example.java")
        with self.assertRaisesRegex(ValueError, "duplicate source"):
            collect(self.root, "new", {"common": ["new"]})

    def test_resource_collisions_are_rejected_but_main_and_test_are_separate(self):
        self.write("common/src/main/resources/config.json", "{}")
        self.write("new/src/test/resources/config.json", "{}")
        self.assertEqual(2, len(collect(self.root, "new", {"common": ["new"]})))
        self.write("new/src/main/resources/config.json", "{}")
        with self.assertRaisesRegex(ValueError, "duplicate source"):
            collect(self.root, "new", {"common": ["new"]})

    def test_parent_traversal_and_absolute_source_groups_are_rejected(self):
        for group in ["../outside", "/tmp/outside", "shared/../../outside"]:
            with self.assertRaisesRegex(ValueError, "Unsafe source group"):
                validate_groups(self.root, ["new"], {group: ["new"]})

    def test_unknown_duplicate_and_missing_version_members_are_rejected(self):
        self.write("common/src/main/java/Example.java")
        for members in [["unknown"], ["new", "new"], []]:
            with self.assertRaises(ValueError):
                validate_groups(self.root, ["new"], {"common": members})

    def test_linked_source_group_is_rejected(self):
        self.write("other/main/java/Example.java")
        (self.root / "common").mkdir()
        (self.root / "common/src").symlink_to(self.root / "other", target_is_directory=True)
        with self.assertRaisesRegex(ValueError, "linked source group"):
            validate_groups(self.root, ["new"], {"common": ["new"]})

    def release_fixture(self):
        self.write("new/gradle.properties", "mod_version=1.0.1\n")
        base = "common/src/main/java/com/atsukimc/anticrash/"
        self.write(base + "AntiCrashMod.java", 'class AntiCrashMod { static final String VERSION = "1.0.1"; }')
        self.write(base + "config/AntiCrashConfig.java", "# AntiCrash 1.0.1 - complete")
        for language in ["ja", "en"]:
            self.write(f"common/src/main/resources/anticrash/messages_{language}.properties",
                       "command.status=AntiCrash 1.0.1\\nState")

    def test_consistent_release_is_accepted(self):
        self.release_fixture()
        validate_release(self.root, ["new"], "1.0.1")

    def test_one_stale_target_version_is_rejected(self):
        self.release_fixture()
        self.write("new/gradle.properties", "mod_version=1.0.0\n")
        with self.assertRaisesRegex(ValueError, "Release version mismatch"):
            validate_release(self.root, ["new"], "1.0.1")

    def test_stale_status_version_is_rejected(self):
        self.release_fixture()
        self.write("common/src/main/resources/anticrash/messages_ja.properties",
                   "command.status=AntiCrash 1.0.0\\nState")
        with self.assertRaisesRegex(ValueError, "Status version header is stale"):
            validate_release(self.root, ["new"], "1.0.1")


if __name__ == "__main__":
    unittest.main()
