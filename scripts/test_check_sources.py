import json
from pathlib import Path
import tempfile
import unittest

from build_model import load_targets
from check_sources import validate, validate_ownership, validate_release


class CanonicalSourceTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.write('gradle.properties', 'mod_version=1.0.2\n')
        self.write('stonecutter.properties.toml', '''mod.java = "17"
mod.compile_java = "21"
mod.namespace = "yarn"
mod.offline_access = "false"
mod.qualification = "true"
["1.18.2"]
deps.fabric_loader = "0.17.3"
deps.yarn = "1.18.2+build.4"
deps.fabric_api = "0.77.0+1.18.2"
resources.mixin_profile = "classic"
resources.refmap = "AntiCrash-refmap.json"
audit.entities = "entities.tsv"
audit.block_entities = "block-entities.tsv"
''')
        self.write('gradle/mixins/classic.json', json.dumps({'required': True, 'injectors': {'defaultRequire': 1}, 'mixins': []}))
        self.write('qualification/fixtures/entities.tsv', '# fields\n')
        self.write('qualification/fixtures/block-entities.tsv', '# fields\n')
        self.write('src/main/java/com/atsukimc/anticrash/AntiCrashMod.java', 'class AntiCrashMod { static final String VERSION = "1.0.2"; }')
        self.write('src/main/java/com/atsukimc/anticrash/config/AntiCrashConfig.java', '# AntiCrash 1.0.2 - complete')
        for lang in ['ja', 'en']:
            self.write(f'src/main/resources/anticrash/messages_{lang}.properties', 'command.status=AntiCrash 1.0.2\\nState')

    def write(self, path, text='class Example {}'):
        target = self.root / path
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(text)

    def test_inherited_dependencies_and_target_contract_are_loaded(self):
        target = validate(self.root)['1.18.2']
        self.assertEqual(17, target.java)
        self.assertEqual('yarn', target.namespace)
        self.assertFalse(target.offline_access)

    def test_missing_profile_is_rejected(self):
        (self.root / 'gradle/mixins/classic.json').unlink()
        with self.assertRaisesRegex(ValueError, 'unsafe build input'):
            validate(self.root)

    def test_parent_traversal_is_rejected(self):
        path = self.root / 'stonecutter.properties.toml'
        path.write_text(path.read_text().replace('"entities.tsv"', '"../entities.tsv"'))
        with self.assertRaisesRegex(ValueError, 'Unsafe audit fixture'):
            validate(self.root)

    def test_linked_input_is_rejected(self):
        path = self.root / 'qualification/fixtures/entities.tsv'
        path.unlink()
        path.symlink_to(self.root / 'qualification/fixtures/block-entities.tsv')
        with self.assertRaisesRegex(ValueError, 'unsafe build input'):
            validate(self.root)

    def test_unknown_namespace_is_rejected(self):
        path = self.root / 'stonecutter.properties.toml'
        path.write_text(path.read_text().replace('"yarn"', '"guess"'))
        with self.assertRaisesRegex(ValueError, 'namespace contract'):
            validate(self.root)

    def test_invalid_flag_is_rejected(self):
        path = self.root / 'stonecutter.properties.toml'
        path.write_text(path.read_text().replace('"false"', '"maybe"'))
        with self.assertRaisesRegex(ValueError, 'true/false'):
            validate(self.root)

    def test_shared_clones_are_rejected(self):
        (self.root / 'shared').mkdir()
        with self.assertRaisesRegex(ValueError, 'Legacy source'):
            validate(self.root)

    def test_version_local_overrides_are_rejected(self):
        self.write('versions/1.18.2/src/main/java/Example.java')
        with self.assertRaisesRegex(ValueError, 'Version-local'):
            validate(self.root)

    def test_published_target_cannot_disappear(self):
        self.write('versions.json', json.dumps({'versions': [{'minecraft': '26.3', 'status': 'BUILD_VERIFIED'}]}))
        with self.assertRaisesRegex(ValueError, 'supported targets removed'):
            validate(self.root)

    def test_mixin_failure_cannot_be_silenced(self):
        self.write('gradle/mixins/classic.json', json.dumps({'required': False, 'injectors': {'defaultRequire': 0}, 'mixins': []}))
        with self.assertRaisesRegex(ValueError, 'policy weakened'):
            validate(self.root)

    def test_stale_runtime_version_is_rejected(self):
        self.write('src/main/java/com/atsukimc/anticrash/AntiCrashMod.java', 'class AntiCrashMod { static final String VERSION = "1.0.1"; }')
        with self.assertRaisesRegex(ValueError, 'Runtime VERSION'):
            validate(self.root)

    def test_stale_configuration_header_is_rejected(self):
        self.write('src/main/java/com/atsukimc/anticrash/config/AntiCrashConfig.java', '# AntiCrash 1.0.1 - complete')
        with self.assertRaisesRegex(ValueError, 'Configuration'):
            validate(self.root)

    def test_stale_translation_version_is_rejected(self):
        self.write('src/main/resources/anticrash/messages_ja.properties', 'command.status=AntiCrash 1.0.1\\nState')
        with self.assertRaisesRegex(ValueError, 'Status version'):
            validate(self.root)


if __name__ == '__main__':
    unittest.main()
