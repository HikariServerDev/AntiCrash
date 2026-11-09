package com.atsukimc.anticrash.runtime;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class VersionNbtCompatibilityTest {
    @BeforeAll
    static void initializeRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void typedReadsPreserveSavedValues() {
        CompoundTag nbt = new CompoundTag();
        nbt.putInt("int", -37);
        nbt.putLong("long", 1234567890123L);
        nbt.putFloat("float", 0.375f);
        nbt.putDouble("double", -12.125);
        nbt.putBoolean("boolean", true);
        nbt.putString("string", "anticrash:saved");
        assertEquals(-37, VersionNbt.getInt(nbt, "int"));
        assertEquals(1234567890123L, VersionNbt.getLong(nbt, "long"));
        assertEquals(0.375f, VersionNbt.getFloat(nbt, "float"));
        assertEquals(-12.125, VersionNbt.getDouble(nbt, "double"));
        assertTrue(VersionNbt.getBoolean(nbt, "boolean"));
        assertEquals("anticrash:saved", VersionNbt.getString(nbt, "string"));
        assertTrue(VersionNbt.contains(nbt, "int", 99));
        assertFalse(VersionNbt.contains(nbt, "string", 99));
    }

    @Test
    void missingAndWrongTypedValuesKeepTheVersionSpecificPolicy() {
        CompoundTag nbt = new CompoundTag();
        nbt.putString("wrong", "not a number");
        //? if <=1.21.4 {
        /*assertEquals(0, VersionNbt.getInt(nbt, "missing"));
        assertEquals(0, VersionNbt.getInt(nbt, "wrong"));
        assertEquals("", VersionNbt.getString(nbt, "missing"));
        assertTrue(VersionNbt.getCompound(nbt, "missing").isEmpty());
        *///?} else {
        assertThrows(RuntimeException.class, () -> VersionNbt.getInt(nbt, "missing"));
        assertThrows(RuntimeException.class, () -> VersionNbt.getInt(nbt, "wrong"));
        assertThrows(RuntimeException.class, () -> VersionNbt.getString(nbt, "missing"));
        assertThrows(RuntimeException.class, () -> VersionNbt.getCompound(nbt, "missing"));
        //?}
        assertTrue(VersionNbt.getCompoundOrEmpty(nbt, "missing").isEmpty());
        assertTrue(VersionNbt.getCompoundOrEmpty(nbt, "wrong").isEmpty());
    }

    @Test
    void compoundAndListReadsKeepTheStoredObjects() {
        CompoundTag child = new CompoundTag();
        child.putInt("phase", 2);
        ListTag children = new ListTag();
        children.add(child);
        ListTag strings = new ListTag();
        strings.add(StringTag.valueOf("saved"));
        CompoundTag root = new CompoundTag();
        root.put("child", child);
        root.put("children", children);
        assertSame(child, VersionNbt.getCompound(root, "child"));
        assertSame(children, VersionNbt.list(root, "children", 10));
        assertSame(child, VersionNbt.getCompound(children, 0));
        assertEquals("saved", VersionNbt.getString(strings, 0));
        assertTrue(VersionNbt.list(root, "missing", 10).isEmpty());
        assertTrue(VersionNbt.listOrEmpty(root, "missing", 10).isEmpty());
    }

    @Test
    void invalidRecoveryListsKeepTheExistingValidationPolicy() {
        CompoundTag root = new CompoundTag();
        root.putString("wrong", "not a list");
        ListTag strings = new ListTag();
        strings.add(StringTag.valueOf("not a compound"));
        root.put("strings", strings);
        //? if <=1.21.4 {
        /*assertTrue(VersionNbt.list(root, "wrong", 10).isEmpty());
        assertTrue(VersionNbt.list(root, "strings", 10).isEmpty());
        *///?} else {
        assertThrows(IllegalArgumentException.class, () -> VersionNbt.list(root, "wrong", 10));
        assertThrows(IllegalArgumentException.class, () -> VersionNbt.list(root, "strings", 10));
        //?}
        assertTrue(VersionNbt.listOrEmpty(root, "wrong", 10).isEmpty());
    }

    @Test
    void uuidReadsUseThePersistedIntArrayLayout() {
        UUID expected = UUID.fromString("12345678-9abc-def0-8123-456789abcdef");
        CompoundTag root = new CompoundTag();
        root.putIntArray("UUID", new int[] {0x12345678, 0x9abcdef0, 0x81234567, 0x89abcdef});
        assertEquals(expected, VersionNbt.getUUID(root, "UUID"));
        root.putIntArray("bad", new int[] {1, 2});
        assertThrows(RuntimeException.class, () -> VersionNbt.getUUID(root, "bad"));
    }

    @Test
    void potionEffectsPreserveTimingAndFlagsAcrossTheNbtCodec() throws java.io.IOException {
        MobEffectInstance effect =
                new MobEffectInstance(MobEffects.GLOWING, 37, 2, true, false, false);
        CompoundTag saved = assertInstanceOf(CompoundTag.class, VersionNbt.effect(effect));
        CompoundTag wire =
                com.atsukimc.anticrash.player.NbtCodec.decodeCompound(
                        com.atsukimc.anticrash.player.NbtCodec.encode(saved));
        MobEffectInstance restored = VersionNbt.effect(wire);
        assertNotNull(restored);
        assertEquals(effect.getEffect(), restored.getEffect());
        assertEquals(37, restored.getDuration());
        assertEquals(2, restored.getAmplifier());
        assertTrue(restored.isAmbient());
        assertFalse(restored.isVisible());
        assertFalse(restored.showIcon());
    }
}
