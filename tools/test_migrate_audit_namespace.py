import tempfile
import unittest
from pathlib import Path
from migrate_audit_namespace import migrate

class MappingAuditMigrationTest(unittest.TestCase):
    def test_owner_aware_fields_and_no_global_member_string_rewrite(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp); target=root/'26.1'
            resources=target/'src/test/resources/runtime-audit'; resources.mkdir(parents=True)
            audit=target/'src/test/java/com/atsukimc/anticrash/audit'; audit.mkdir(parents=True)
            old=root/'old.tiny'; new=root/'new.tiny'
            old.write_text('tiny\t2\t0\tofficial\tintermediary\tnamed\nc\ta\tclass_1\tnet/minecraft/block/entity/FurnaceBlockEntity\n\tf\tI\tx\tfield_1\tworldChunk\n')
            new.write_text('tiny\t2\t0\tofficial\tintermediary\tnamed\nc\ta\tclass_1\tnet/minecraft/world/level/block/entity/FurnaceBlockEntity\n\tf\tI\tx\tfield_1\ttime\n')
            resource=resources/'block-entities.tsv'
            resource.write_text('FurnaceBlockEntity\tworldChunk\tPERSISTED_BY_VANILLA\tNONE\treason\n')
            java=audit/'Audit.java'; java.write_text('"net/minecraft/block/entity/FurnaceBlockEntity"; "worldChunk";')
            migrate(old,new,target)
            self.assertIn('FurnaceBlockEntity\ttime\t',resource.read_text())
            self.assertIn('"worldChunk"',java.read_text())
            self.assertIn('net/minecraft/world/level/block/entity/FurnaceBlockEntity',java.read_text())
    def test_different_game_mapping_identity_is_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp); old=root/'a'; new=root/'b'
            old.write_text('tiny\t2\t0\tofficial\tintermediary\tnamed\nc\ta\tclass_1\tFoo\n')
            new.write_text('tiny\t2\t0\tofficial\tintermediary\tnamed\nc\tb\tclass_2\tBar\n')
            with self.assertRaises(ValueError): migrate(old,new,root/'26.1')
