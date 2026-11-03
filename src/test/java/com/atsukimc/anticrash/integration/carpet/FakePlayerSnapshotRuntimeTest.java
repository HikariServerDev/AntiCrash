package com.atsukimc.anticrash.integration.carpet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.atsukimc.anticrash.util.AtomicFiles;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;

class FakePlayerSnapshotRuntimeTest {

    //? if (>=1.21.11 && <=26.3) {
    @Test
    void kineticStateSurvivesJsonAndChangesTheFingerprint() throws Exception {
        var snapshot = player();
        //?}

        //? if 1.21.11 {
        /*snapshot.motionFrameNbt =
                    com.atsukimc.anticrash.player.NbtCodec.encode(
                            new com.atsukimc.anticrash.runtime.EntityMotionFrame(
                                            new net.minecraft.world.phys.Vec3(1, 0, 0), null, true)
                                    .write());
*///?} else if (>=26.1 && <=26.3) {
        snapshot.motionFrameNbt =
                com.atsukimc.anticrash.player.NbtCodec.encode(
                        new com.atsukimc.anticrash.runtime.EntityMotionFrame(
                                        new net.minecraft.world.phys.Vec3(1, 0, 0), null, true)
                                .write());
        //?}

        //? if (>=1.21.11 && <=26.3) {
        snapshot.lastKineticAttackTime = 1337L;
        //?}

        //? if (>=1.21.11 && <=26.3) {
        var decoded =
                AtomicFiles.GSON.fromJson(
                        AtomicFiles.GSON.toJson(snapshot), FakePlayerSnapshot.class);
        //?}

        //? if (>=1.21.11 && <=26.3) {
        assertEquals(snapshot.canonical(), decoded.canonical());
        //?}

        //? if (>=1.21.11 && <=26.3) {
        assertEquals(snapshot.motionFrameNbt, decoded.motionFrameNbt);
        //?}

        //? if (>=1.21.11 && <=26.3) {
        decoded.lastKineticAttackTime = 1338L;
        //?}

        //? if (>=1.21.11 && <=26.3) {
        assertNotEquals(snapshot.canonical(), decoded.canonical());
    }

    //?}

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
        FakePlayerSnapshot reloaded =
                AtomicFiles.GSON.fromJson(AtomicFiles.GSON.toJson(old), FakePlayerSnapshot.class);
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

        FakePlayerSnapshot reloaded =
                AtomicFiles.GSON.fromJson(
                        AtomicFiles.GSON.toJson(drawing), FakePlayerSnapshot.class);
        assertEquals(drawing.canonical(), reloaded.canonical());
        assertEquals(14, reloaded.itemCooldowns.get(0).remainingTicks);

        FakePlayerSnapshot later =
                AtomicFiles.GSON.fromJson(
                        AtomicFiles.GSON.toJson(drawing), FakePlayerSnapshot.class);
        later.itemUseTimeLeft = 71980;
        assertNotEquals(drawing.canonical(), later.canonical());
        assertFalse(drawing.canonical().equals(player().canonical()));
    }

    //? if (>=1.21.11 && <=26.3) {
    @Test
    void nativeNeverHitSentinelPassesTheProductionSnapshotValidator() throws Exception {
        var game = new org.objectweb.asm.tree.ClassNode();
        //?}

        //? if 1.21.11 {
        /*try (var input =
                    net.minecraft.world.entity.LivingEntity.class.getResourceAsStream("LivingEntity.class"))
*///?} else if (>=26.1 && <=26.3) {
        try (var input =
                net.minecraft.world.entity.LivingEntity.class.getResourceAsStream(
                        "LivingEntity.class"))
        //?}
        //? if (>=1.21.11 && <=26.3) {
        {
            new org.objectweb.asm.ClassReader(input).accept(game, 0);
        }
        //?}

        //? if (>=1.21.11 && <=26.3) {
        boolean initialized =
                game.methods.stream()
                        .filter(m -> m.name.equals("<init>"))
                        .anyMatch(m -> {
                            for (var instruction : m.instructions) {
                                //?}
                                //? if 1.21.11 {
                                /*if (!(instruction
                                                    instanceof
                                                    org.objectweb.asm.tree.FieldInsnNode field)
                                            || field.getOpcode() != org.objectweb.asm.Opcodes.PUTFIELD
                                            || !field.name.equals("lastKineticHitFeedbackTime"))
*///?} else if (>=26.1 && <=26.3) {
                                if (!(instruction
                                                instanceof
                                                org.objectweb.asm.tree.FieldInsnNode field)
                                        || field.getOpcode() != org.objectweb.asm.Opcodes.PUTFIELD
                                        || !field.name.equals("lastKineticHitFeedbackTime"))
                                    //?}
                                    //? if (>=1.21.11 && <=26.3) {
                                    continue;
                                //?}

                                //? if (>=1.21.11 && <=26.3) {
                                var previous = instruction.getPrevious();
                                //?}

                                //? if (>=1.21.11 && <=26.3) {
                                return previous
                                                instanceof
                                                org.objectweb.asm.tree.LdcInsnNode constant
                                        && Long.valueOf(Integer.MIN_VALUE).equals(constant.cst);
                            }
                            //?}

                            //? if (>=1.21.11 && <=26.3) {
                            return false;
                        });
        //?}

        //? if (>=1.21.11 && <=26.3) {
        org.junit.jupiter.api.Assertions.assertTrue(initialized, "Verify the actual game sentinel");
        //?}

        //? if (>=1.21.11 && <=26.3) {
        var config = new com.atsukimc.anticrash.config.AntiCrashConfig();
        //?}

        //? if (>=1.21.11 && <=26.3) {
        var options = config.fakePlayer.snapshot;
        //?}

        //? if (>=1.21.11 && <=26.3) {
        options.inventory = false;
        //?}

        //? if (>=1.21.11 && <=26.3) {
        options.selectedSlot = false;
        //?}

        //? if (>=1.21.11 && <=26.3) {
        options.actions = false;
        //?}

        //? if (>=1.21.11 && <=26.3) {
        options.actionInternalTiming = false;
        //?}

        //? if (>=1.21.11 && <=26.3) {
        options.itemUseCooldown = false;
        //?}

        //? if (>=1.21.11 && <=26.3) {
        options.blockBreakProgress = false;
        //?}

        //? if (>=1.21.11 && <=26.3) {
        var validate =
                CarpetIntegration.class.getDeclaredMethod(
                        "validate",
                        FakePlayerSnapshot.class,
                        com.atsukimc.anticrash.config.AntiCrashConfig.class);
        //?}

        //? if (>=1.21.11 && <=26.3) {
        validate.setAccessible(true);
        //?}

        //? if (>=1.21.11 && <=26.3) {
        var snapshot = player();
        //?}

        //? if (>=1.21.11 && <=26.3) {
        for (long value : new long[] {Integer.MIN_VALUE, -1, 0, 1337}) {
            snapshot.lastKineticAttackTime = value;
            //?}

            //? if (>=1.21.11 && <=26.3) {
            org.junit.jupiter.api.Assertions.assertDoesNotThrow(
                    () -> validate.invoke(null, snapshot, config));
        }
        //?}

        //? if (>=1.21.11 && <=26.3) {
        for (long value : new long[] {-2, Long.MIN_VALUE}) {
            snapshot.lastKineticAttackTime = value;
            //?}

            //? if (>=1.21.11 && <=26.3) {
            var error =
                    org.junit.jupiter.api.Assertions.assertThrows(
                            java.lang.reflect.InvocationTargetException.class,
                            () -> validate.invoke(null, snapshot, config));
            //?}

            //? if (>=1.21.11 && <=26.3) {
            org.junit.jupiter.api.Assertions.assertInstanceOf(
                    IllegalArgumentException.class, error.getCause());
        }
    }
    //?}

}
