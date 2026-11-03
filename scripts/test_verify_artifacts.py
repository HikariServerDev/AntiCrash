import json
import tempfile
import shutil
import unittest
import zipfile
from pathlib import Path

from verify_artifacts import verify


class ReleaseCollectionTest(unittest.TestCase):
    def test_verified_jars_go_to_dist_without_recreating_root_build(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            (root / 'LICENSE').write_bytes(b'fixture license')
            icon = root / 'src/main/resources/icon.jpg'
            icon.parent.mkdir(parents=True)
            icon.write_bytes(b'fixture icon')
            for supervisor in [False, True]:
                prefix = 'AntiCrash-Supervisor' if supervisor else 'AntiCrash'
                directory = root / ('supervisor/build/libs' if supervisor else 'versions/1.18.2/build/libs')
                directory.mkdir(parents=True)
                with zipfile.ZipFile(directory / f'{prefix}-1.0.2+mc1.18.2.jar', 'w') as jar:
                    jar.writestr('LICENSE_' + prefix, b'fixture license')
                    if supervisor:
                        jar.writestr('META-INF/MANIFEST.MF',
                                     'Main-Class: com.atsukimc.anticrash.supervisor.SupervisorMain\n')
                    else:
                        jar.writestr('fabric.mod.json', json.dumps({
                            'id': 'anticrash', 'version': '1.0.2', 'environment': 'server',
                            'license': 'LGPL-3.0-only', 'depends': {'minecraft': '=1.18.2'},
                            'icon': 'icon.jpg'}))
                        jar.writestr('icon.jpg', b'fixture icon')
                        jar.writestr('anticrash.mixins.json', json.dumps({'mixins': ['UserCacheMixin']}))
            result = verify(root, '1.18.2', '1.0.2', collect=True)
            self.assertEqual(2, len(result['artifacts']))
            self.assertFalse((root / 'build').exists())
            self.assertEqual({artifact['name'] for artifact in result['artifacts']},
                             {path.name for path in (root / 'dist/refactor/1.0.2').glob('*.jar')})
            # A reordered Mixin list must fail before bytecode comparison runs.
            baseline = root / 'baseline'
            baseline.mkdir()
            for artifact in result['artifacts']:
                original = root / ('supervisor/build/libs' if 'Supervisor' in artifact['name']
                                   else 'versions/1.18.2/build/libs') / artifact['name']
                shutil.copy2(original, baseline / original.name)
            mod_jar = root / 'versions/1.18.2/build/libs/AntiCrash-1.0.2+mc1.18.2.jar'
            def mixins(path, names):
                with zipfile.ZipFile(path) as jar:
                    entries = {name: jar.read(name) for name in jar.namelist()}
                entries['anticrash.mixins.json'] = json.dumps({'mixins': names}).encode()
                with zipfile.ZipFile(path, 'w') as jar:
                    for name, content in entries.items():
                        jar.writestr(name, content)
            mixins(baseline / mod_jar.name, ['UserCacheMixin', 'OtherMixin'])
            mixins(mod_jar, ['OtherMixin', 'UserCacheMixin'])
            with self.assertRaises(AssertionError) as failure:
                verify(root, '1.18.2', '1.0.2', baseline='1.0.2', baseline_dir=baseline,
                       allow_tool_metadata=True)
            self.assertIn('anticrash.mixins.json', str(failure.exception))



if __name__ == '__main__':
    unittest.main()


class BuildManifestTest(unittest.TestCase):
    def test_only_known_build_information_is_permitted(self):
        from verify_artifacts import compare_build_manifest
        old = b'Manifest-Version: 1.0\nFabric-Loom-Version: 1.7.4\nFabric-Mapping-Namespace: intermediary\n'
        new = old.replace(b'1.7.4', b'1.17.21') + b'Stonecutter-Plugin-Version: 0.9.8\nStonecutter-Node-Version: 1.18.2\n'
        compare_build_manifest(old, new, '1.18.2')
        for bad in [new.replace(b'intermediary', b'official'), new.replace(b'0.9.8', b'9.9.9'),
                    new.replace(b'Node-Version: 1.18.2', b'Node-Version: 26.3'),
                    new + b'Main-Class: unexpected.Main\n']:
            with self.subTest(manifest=bad), self.assertRaises(AssertionError):
                compare_build_manifest(old, bad, '1.18.2')

    def test_duplicate_and_dangling_continuations_are_rejected(self):
        from verify_artifacts import manifest_attributes
        for invalid in [b' continuation\n', b'Name: first\nName: second\n']:
            with self.subTest(manifest=invalid), self.assertRaises(ValueError):
                manifest_attributes(invalid)


class RefmapParityTest(unittest.TestCase):
    def test_mapping_names_may_change_but_runtime_targets_must_match(self):
        from verify_artifacts import compare_refmap
        before = {
            'mappings': {'example/Mixin': {'tick()V': 'Lgame/World;method_1()V'}},
            'data': {'named:intermediary': {
                'example/Mixin': {'tick()V': 'Lgame/World;method_1()V'}}},
        }
        after = {
            'mappings': {'example/Mixin': {'serverTick()V': 'Lgame/World;method_1()V'}},
            'data': {'named:intermediary': {
                'example/Mixin': {'serverTick()V': 'Lgame/World;method_1()V'}}},
        }
        compare_refmap(json.dumps(before).encode(), json.dumps(after).encode())

        after['data']['named:intermediary']['example/Mixin']['serverTick()V'] = 'Lgame/World;method_2()V'
        with self.assertRaises(AssertionError):
            compare_refmap(json.dumps(before).encode(), json.dumps(after).encode())

    def test_added_mixin_is_rejected(self):
        from verify_artifacts import compare_refmap
        before = {
            'mappings': {'example/Mixin': {'tick()V': 'Lgame/World;method_1()V'}},
            'data': {'named:intermediary': {
                'example/Mixin': {'tick()V': 'Lgame/World;method_1()V'}}},
        }
        after = json.loads(json.dumps(before))
        after['mappings']['example/NewMixin'] = {}
        with self.assertRaises(AssertionError):
            compare_refmap(json.dumps(before).encode(), json.dumps(after).encode())
