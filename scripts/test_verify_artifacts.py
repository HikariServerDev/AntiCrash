import json
import tempfile
import unittest
import zipfile
from pathlib import Path

from verify_artifacts import verify


class ReleaseCollectionTest(unittest.TestCase):
    def test_verified_jars_go_to_dist_without_recreating_root_build(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            (root / 'LICENSE').write_bytes(b'fixture license')
            (root / 'anticrash-icon.jpg').write_bytes(b'fixture icon')
            for supervisor in [False, True]:
                prefix = 'AntiCrash-Supervisor' if supervisor else 'AntiCrash'
                directory = root / '1.18.2' / ('supervisor/build/libs' if supervisor else 'build/libs')
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
                             {path.name for path in (root / 'dist/1.0.2').glob('*.jar')})


if __name__ == '__main__':
    unittest.main()
