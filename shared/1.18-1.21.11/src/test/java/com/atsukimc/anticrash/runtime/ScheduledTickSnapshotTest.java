package com.atsukimc.anticrash.runtime;

import com.atsukimc.anticrash.integration.carpet.TickSpeedSnapshot;
import com.atsukimc.anticrash.util.AtomicFiles;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ScheduledTickSnapshotTest {
    @Test
    void runtimeCapsuleJsonPreservesEveryOrderingFieldExactly() {
        RuntimeCapsule capsule = new RuntimeCapsule();
        RuntimeCapsule.WorldRuntime world = new RuntimeCapsule.WorldRuntime();
        world.dimension = "minecraft:overworld";
        world.nextTickOrder = Long.MAX_VALUE - 3;
        capsule.worlds.add(world);
        ScheduledTickSnapshot before = new ScheduledTickSnapshot();
        before.dimension = "minecraft:overworld";
        before.kind = ScheduledTickSnapshot.BLOCK;
        before.type = "minecraft:observer";
        before.x = -5235;
        before.y = 128;
        before.z = 1473;
        before.triggerTick = Long.MAX_VALUE - 12;
        before.priority = -2;
        before.subTickOrder = Long.MIN_VALUE + 34;
        capsule.scheduledTicks.add(before);
        MovingPistonTickSnapshot piston = new MovingPistonTickSnapshot();
        piston.dimension = "minecraft:overworld";
        piston.x = -5234;
        piston.y = 129;
        piston.z = 1473;
        capsule.movingPistonTickOrder.add(piston);
        TickSpeedSnapshot tickSpeed = new TickSpeedSnapshot();
        tickSpeed.tickrate = 500.0F;
        tickSpeed.mspt = 2.0F;
        tickSpeed.processEntities = false;
        tickSpeed.paused = true;
        tickSpeed.deepFrozen = true;
        tickSpeed.timeBias = 0L;
        tickSpeed.timeWarpStartTime = 0L;
        tickSpeed.timeWarpScheduledTicks = 0L;
        tickSpeed.playerActiveTimeout = 7;
        tickSpeed.superHot = true;
        tickSpeed.timeWarpActive = false;
        capsule.carpetTickSpeed = tickSpeed;

        RuntimeCapsule restored = AtomicFiles.GSON.fromJson(
                AtomicFiles.GSON.toJson(capsule), RuntimeCapsule.class);
        ScheduledTickSnapshot after = restored.scheduledTicks.get(0);

        assertEquals(RuntimeCapsule.CURRENT_FORMAT_VERSION, restored.formatVersion);
        assertEquals(world.nextTickOrder, restored.worlds.get(0).nextTickOrder);
        assertEquals(before.dimension, after.dimension);
        assertEquals(before.kind, after.kind);
        assertEquals(before.type, after.type);
        assertEquals(before.x, after.x);
        assertEquals(before.y, after.y);
        assertEquals(before.z, after.z);
        assertEquals(before.triggerTick, after.triggerTick);
        assertEquals(before.priority, after.priority);
        assertEquals(before.subTickOrder, after.subTickOrder);
        assertEquals(tickSpeed.tickrate, restored.carpetTickSpeed.tickrate);
        assertEquals(tickSpeed.mspt, restored.carpetTickSpeed.mspt);
        assertEquals(tickSpeed.processEntities, restored.carpetTickSpeed.processEntities);
        assertEquals(tickSpeed.paused, restored.carpetTickSpeed.paused);
        assertEquals(tickSpeed.deepFrozen, restored.carpetTickSpeed.deepFrozen);
        assertEquals(tickSpeed.playerActiveTimeout, restored.carpetTickSpeed.playerActiveTimeout);
        assertEquals(tickSpeed.superHot, restored.carpetTickSpeed.superHot);
        assertEquals(tickSpeed.timeWarpActive, restored.carpetTickSpeed.timeWarpActive);
        MovingPistonTickSnapshot restoredPiston = restored.movingPistonTickOrder.get(0);
        assertEquals(piston.dimension, restoredPiston.dimension);
        assertEquals(piston.x, restoredPiston.x);
        assertEquals(piston.y, restoredPiston.y);
        assertEquals(piston.z, restoredPiston.z);
    }

    @Test
    void nextTickCounterAlwaysFollowsExistingRestoredOrders() {
        assertEquals(42L, RuntimeCapsuleManager.normalizeNextTickOrderValue(42L, 12L));
        assertEquals(13L, RuntimeCapsuleManager.normalizeNextTickOrderValue(0L, 12L));
        assertEquals(13L, RuntimeCapsuleManager.normalizeNextTickOrderValue(12L, 12L));
        assertThrows(IllegalStateException.class, () ->
                RuntimeCapsuleManager.normalizeNextTickOrderValue(0L, Long.MAX_VALUE));
    }
}
