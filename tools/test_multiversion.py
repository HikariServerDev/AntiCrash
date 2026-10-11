import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
import zipfile

spec = importlib.util.spec_from_file_location('multiversion', Path(__file__).with_name('multiversion.py'))
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)

class ArtifactTests(unittest.TestCase):
    def test_java25_is_scoped_and_missing_toolchain_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory); jdk=root/'.toolchains/jdk-25'; (jdk/'bin').mkdir(parents=True)
            (jdk/'release').write_text('JAVA_VERSION="25.0.4.1"\n'); (jdk/'bin/java').write_text('fixture')
            env={'JAVA_HOME':'/unchanged-java21'}
            self.assertEqual(env,module.build_environment({'minimum_game_java':21},root,env))
            self.assertEqual(str(jdk),module.build_environment({'minimum_game_java':25},root,env)['JAVA_HOME'])
            self.assertEqual('/unchanged-java21',env['JAVA_HOME'])
            (jdk/'release').write_text('JAVA_VERSION="21.0.1"\n')
            with self.assertRaises(ValueError): module.build_environment({'minimum_game_java':25},root,{})
    def test_exact_version_and_icon_required(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            icon = root / 'icon.jpg'; icon.write_bytes(b'image fixture')
            jar = root / 'mod.jar'
            def create(version, image):
                with zipfile.ZipFile(jar, 'w') as output:
                    output.writestr('fabric.mod.json', json.dumps({'id': 'anticrash', 'version': '1.0.0',
                        'depends': {'minecraft': '=' + version}, 'icon': 'assets/anticrash/icon.jpg'}))
                    output.writestr('assets/anticrash/icon.jpg', image)
            create('1.18.2', b'image fixture')
            self.assertEqual('mod.jar', module.inspect_jar(jar, '1.18.2', '1.0.0', icon)['name'])
            with self.assertRaises(ValueError): module.inspect_jar(jar, '1.21', '1.0.0', icon)
            create('1.18.2', b'wrong icon')
            with self.assertRaises(ValueError): module.inspect_jar(jar, '1.18.2', '1.0.0', icon)

    def test_no_unknown_or_duplicate_target(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / 'versions.json').write_text(json.dumps({'mod_version': '1.0.0',
                'versions': [{'minecraft': '../../escape'}]}))
            with self.assertRaises(ValueError): module.read_targets(root)

    def test_all_requested_folders_have_honest_status(self):
        _, targets = module.read_targets()
        self.assertEqual(31, len(targets))
        self.assertEqual(31, len({target['minecraft'] for target in targets}))
        for target in targets:
            self.assertIn(target['status'], module.BUILDABLE | {'NOT_PORTED', 'PORT_IN_PROGRESS'})
            self.assertFalse(target['integration_verified'])

if __name__ == '__main__': unittest.main()
