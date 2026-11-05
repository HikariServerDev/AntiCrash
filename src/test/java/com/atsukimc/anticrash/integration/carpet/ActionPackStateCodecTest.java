package com.atsukimc.anticrash.integration.carpet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.atsukimc.anticrash.util.AtomicFiles;
import com.google.gson.JsonObject;

import net.minecraft.core.BlockPos;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

class ActionPackStateCodecTest {
    @Test
    void roundTripsNextAndTimingFieldsThroughJson() {
        FakeActionPack before = new FakeActionPack();
        before.next = 7;
        before.interval = 12;
        before.currentBlock = "minecraft:stone";
        before.child.limit = 99;

        JsonObject snapshot = ActionPackStateCodec.capture(before);
        JsonObject roundTripped = AtomicFiles.GSON.fromJson(snapshot.toString(), JsonObject.class);

        FakeActionPack after = new FakeActionPack();
        after.next = 0;
        after.interval = 1;
        after.currentBlock = "minecraft:dirt";
        after.child.limit = 2;
        ActionPackStateCodec.restore(after, roundTripped);

        assertEquals(7, after.next);
        assertEquals(12, after.interval);
        assertEquals("minecraft:stone", after.currentBlock);
        assertEquals(99, after.child.limit);
    }

    @Test
    void rebuildsAnActionMapAndItsPrivateCarpetStyleAction() {
        ActionMapPack before = new ActionMapPack();
        FakeAction action = new FakeAction(9, 4, 1, true);
        action.next = 2;
        action.count = 6;
        action.done = true;
        before.actions.put(ActionType.USE, action);

        JsonObject snapshot = ActionPackStateCodec.capture(before);
        JsonObject roundTripped = AtomicFiles.GSON.fromJson(snapshot.toString(), JsonObject.class);
        ActionMapPack after = new ActionMapPack();
        FakeAction preexisting = new FakeAction(1, 20, 0, false);
        after.actions.put(ActionType.USE, preexisting);
        assertFalse(ActionPackStateCodec.matches(roundTripped, after, true, true, true, true));

        ActionPackStateCodec.restore(after, roundTripped);

        FakeAction restored = after.actions.get(ActionType.USE);
        assertNotSame(preexisting, restored);
        assertEquals(1, after.actions.size());
        assertEquals(9, restored.limit);
        assertEquals(4, restored.interval);
        assertEquals(1, restored.offset);
        assertTrue(restored.isContinuous);
        assertEquals(2, restored.next);
        assertEquals(6, restored.count);
        assertTrue(restored.done);
        assertTrue(ActionPackStateCodec.matches(roundTripped, after, true, true, true, true));
    }

    /**
     * The real restore target is a freshly created FakePlayer whose action map is empty, so the key
     * must be rebuilt from its snapshot. Carpet's ActionType constants have bodies; recording their
     * anonymous class made the key unrecoverable and every action was silently dropped.
     */
    @Test
    void rebuildsActionsIntoAnEmptyMapKeyedByAnEnumWithConstantBodies() {
        BodyActionMapPack before = new BodyActionMapPack();
        FakeAction action = new FakeAction(-1, 1, 0, true);
        action.next = 1;
        action.count = 1155;
        before.actions.put(BodyActionType.USE, action);

        JsonObject snapshot = ActionPackStateCodec.capture(before, true, true, true, true);
        JsonObject fromFile =
                AtomicFiles.GSON.fromJson(AtomicFiles.GSON.toJson(snapshot), JsonObject.class);
        BodyActionMapPack after = new BodyActionMapPack();
        ActionPackStateCodec.restore(after, fromFile, true, true, true, true);

        assertEquals(1, after.actions.size());
        assertEquals(1155, after.actions.get(BodyActionType.USE).count);
        assertTrue(after.actions.get(BodyActionType.USE).isContinuous);
        // currentBlock is null on both sides but absent from the file; it must still verify.
        assertTrue(ActionPackStateCodec.matches(fromFile, after, true, true, true, true));
    }

    /** A snapshot taken by an earlier build recorded the constant's anonymous class name. */
    @Test
    void acceptsLegacyEnumKeysRecordedWithTheConstantClass() {
        BodyActionMapPack before = new BodyActionMapPack();
        before.actions.put(BodyActionType.USE, new FakeAction(-1, 1, 0, true));
        JsonObject snapshot = ActionPackStateCodec.capture(before, true, true, true, true);
        String legacy =
                snapshot.toString()
                        .replace(
                                BodyActionType.class.getName() + "\"",
                                BodyActionType.USE.getClass().getName() + "\"");
        assertTrue(legacy.contains("$1"));
        BodyActionMapPack after = new BodyActionMapPack();
        JsonObject legacySnapshot = AtomicFiles.GSON.fromJson(legacy, JsonObject.class);
        ActionPackStateCodec.restore(after, legacySnapshot, true, true, true, true);
        assertEquals(1, after.actions.size());
        // The live pack now records BodyActionType; the legacy snapshot says BodyActionType$1.
        assertTrue(ActionPackStateCodec.matches(legacySnapshot, after, true, true, true, true));
    }

    @Test
    void optionalInternalTimingDoesNotOverwriteRuntimePhase() {
        ActionMapPack before = new ActionMapPack();
        FakeAction action = new FakeAction(5, 8, 2, false);
        action.next = 3;
        action.count = 4;
        action.done = true;
        before.actions.put(ActionType.ATTACK, action);

        JsonObject snapshot = ActionPackStateCodec.capture(before, true, false, true, true);
        ActionMapPack after = new ActionMapPack();
        ActionPackStateCodec.restore(after, snapshot, true, false, true, true);

        FakeAction restored = after.actions.get(ActionType.ATTACK);
        assertEquals(5, restored.limit);
        assertEquals(8, restored.interval);
        assertEquals(2, restored.offset);
        assertFalse(restored.isContinuous);
        assertEquals(10, restored.next);
        assertEquals(0, restored.count);
        assertFalse(restored.done);
    }

    @Test
    void restoresCarpetCurrentBlockWhenTheNewActionPackStartsWithNull() {
        BlockProgressPack before = new BlockProgressPack();
        before.currentBlock = new BlockPos(-12, 64, 37);
        before.blockHitDelay = 4;
        before.isHittingBlock = true;
        before.curBlockDamageMP = 0.625F;

        JsonObject snapshot = ActionPackStateCodec.capture(before, true, true, true, true);
        BlockProgressPack after = new BlockProgressPack();
        ActionPackStateCodec.restore(after, snapshot, true, true, true, true);

        assertEquals(before.currentBlock, after.currentBlock);
        assertEquals(4, after.blockHitDelay);
        assertTrue(after.isHittingBlock);
        assertEquals(0.625F, after.curBlockDamageMP);
        assertTrue(ActionPackStateCodec.matches(snapshot, after, true, true, true, true));
    }

    @Test
    void rejectsAnActionSnapshotThatSilentlyLostItsRuntimePhase() {
        ActionMapPack pack = new ActionMapPack();
        pack.actions.put(ActionType.USE, new FakeAction(9, 4, 1, true));
        JsonObject snapshot = ActionPackStateCodec.capture(pack, true, true, false, false);
        JsonObject actionFields =
                snapshot.getAsJsonObject("fields")
                        .getAsJsonObject("actions")
                        .getAsJsonArray("entries")
                        .get(0)
                        .getAsJsonObject()
                        .getAsJsonObject("value")
                        .getAsJsonObject("fields");
        actionFields.remove("next");

        assertThrows(
                java.io.IOException.class,
                () -> ActionPackStateCodec.validateSnapshot(snapshot, true, true, false, false));
    }

    @Test
    void migratesOnlyAnUnambiguousLegacyNullCurrentBlock() throws Exception {
        BlockProgressPack idle = new BlockProgressPack();
        JsonObject idleSnapshot = ActionPackStateCodec.capture(idle, false, false, false, true);
        idleSnapshot.getAsJsonObject("fields").remove("currentBlock");

        ActionPackStateCodec.validateSnapshot(idleSnapshot, false, false, false, true);
        assertTrue(idleSnapshot.getAsJsonObject("fields").get("currentBlock").isJsonNull());
        BlockProgressPack restored = new BlockProgressPack();
        ActionPackStateCodec.restore(restored, idleSnapshot, false, false, false, true);
        assertTrue(ActionPackStateCodec.matches(idleSnapshot, restored, false, false, false, true));

        BlockProgressPack active = new BlockProgressPack();
        active.currentBlock = new BlockPos(1, 64, 2);
        active.isHittingBlock = true;
        active.curBlockDamageMP = 0.5F;
        JsonObject activeSnapshot = ActionPackStateCodec.capture(active, false, false, false, true);
        activeSnapshot.getAsJsonObject("fields").remove("currentBlock");
        assertThrows(
                java.io.IOException.class,
                () ->
                        ActionPackStateCodec.validateSnapshot(
                                activeSnapshot, false, false, false, true));
    }

    private static final class FakeActionPack {
        private int next = 2;
        private int interval = 5;
        private String currentBlock = "minecraft:air";
        private final Timing child = new Timing();
    }

    private static final class Timing {
        private int limit = 10;
    }

    private static final class BlockProgressPack {
        private BlockPos currentBlock;
        private int blockHitDelay;
        private boolean isHittingBlock;
        private float curBlockDamageMP;
    }

    private enum ActionType {
        USE,
        ATTACK
    }

    /** Like Carpet's ActionType: every constant has a body, so its class is ActionType$1 etc. */
    private enum BodyActionType {
        USE {
            @Override
            boolean continuous() {
                return true;
            }
        },
        ATTACK {
            @Override
            boolean continuous() {
                return false;
            }
        };

        abstract boolean continuous();
    }

    private static final class BodyActionMapPack {
        private final Map<BodyActionType, FakeAction> actions = new EnumMap<>(BodyActionType.class);
        private Object currentBlock;
    }

    private static final class ActionMapPack {
        private final Map<ActionType, FakeAction> actions = new EnumMap<>(ActionType.class);
    }

    private static final class FakeAction {
        private final int limit;
        private final int interval;
        private final int offset;
        private final boolean isContinuous;
        private int next;
        private int count;
        private boolean done;

        private FakeAction(int limit, int interval, int offset, boolean continuous) {
            this.limit = limit;
            this.interval = interval;
            this.offset = offset;
            this.isContinuous = continuous;
            this.next = interval + offset;
        }
    }
}
