package com.atsukimc.anticrash.integration.carpet;

import com.atsukimc.anticrash.util.AtomicFiles;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class FakePlayerSnapshotRuntimeTest {
    @Test void kineticStateSurvivesJsonAndChangesTheFingerprint() throws Exception {
        var snapshot=player();
        snapshot.motionFrameNbt=com.atsukimc.anticrash.player.NbtCodec.encode(new com.atsukimc.anticrash.runtime.EntityMotionFrame(
                new net.minecraft.util.math.Vec3d(1,0,0),null,true).write());
        snapshot.lastKineticAttackTime=1337L;
        var decoded=AtomicFiles.GSON.fromJson(AtomicFiles.GSON.toJson(snapshot),FakePlayerSnapshot.class);
        assertEquals(snapshot.canonical(),decoded.canonical());
        assertEquals(snapshot.motionFrameNbt,decoded.motionFrameNbt);
        decoded.lastKineticAttackTime=1338L;
        assertNotEquals(snapshot.canonical(),decoded.canonical());
    }
    private static FakePlayerSnapshot player() {
        FakePlayerSnapshot snapshot = new FakePlayerSnapshot();
        snapshot.uuid = "8116f12a-d05a-3066-be0f-7636524e0077";
        snapshot.name = "bow_bot";
        snapshot.dimension = "minecraft:overworld";
        snapshot.gamemode = "survival";
        return snapshot;
    }

    @Test
    void snapshotsWithoutTheNewFieldsKeepTheirOldFingerprint() {
        FakePlayerSnapshot old = player();
        String before = old.canonical();
        // Exactly the pre-existing 25 fields; a checkpoint written before must still verify.
        assertEquals(24, before.chars().filter(c -> c == '|').count());
        FakePlayerSnapshot reloaded = AtomicFiles.GSON.fromJson(AtomicFiles.GSON.toJson(old), FakePlayerSnapshot.class);
        assertNull(reloaded.activeHand);
        assertNull(reloaded.itemCooldowns);
        assertEquals(before, reloaded.canonical());
    }

    @Test
    void useStateIsPartOfTheFingerprintAndSurvivesTheFile() {
        FakePlayerSnapshot drawing = player();
        drawing.activeHand = "MAIN_HAND";
        drawing.itemUseTimeLeft = 71990;
        drawing.lastAttackedTicks = 12;
        drawing.itemCooldowns = new ArrayList<>();
        FakePlayerSnapshot.ItemCooldown pearl = new FakePlayerSnapshot.ItemCooldown();
        pearl.item = "minecraft:ender_pearl";
        pearl.remainingTicks = 14;
        drawing.itemCooldowns.add(pearl);

        FakePlayerSnapshot reloaded = AtomicFiles.GSON.fromJson(AtomicFiles.GSON.toJson(drawing), FakePlayerSnapshot.class);
        assertEquals(drawing.canonical(), reloaded.canonical());
        assertEquals(14, reloaded.itemCooldowns.get(0).remainingTicks);

        FakePlayerSnapshot later = AtomicFiles.GSON.fromJson(AtomicFiles.GSON.toJson(drawing), FakePlayerSnapshot.class);
        later.itemUseTimeLeft = 71980;
        assertNotEquals(drawing.canonical(), later.canonical());
        assertFalse(drawing.canonical().equals(player().canonical()));
    }
}
