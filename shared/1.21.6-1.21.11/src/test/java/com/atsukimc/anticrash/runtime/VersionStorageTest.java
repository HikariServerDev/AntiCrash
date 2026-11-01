package com.atsukimc.anticrash.runtime;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.entity.player.PlayerAbilities;
import net.minecraft.nbt.*;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.storage.*;

import org.junit.jupiter.api.Test;

class VersionStorageTest {
    @org.junit.jupiter.api.AfterEach
    void clearScope() {
        RuntimeExtensionNbt.loadedChunkSave(false);
        RuntimeExtensionNbt.entitySave(false);
    }

    private final RegistryWrapper.WrapperLookup registries =
            RegistryWrapper.WrapperLookup.of(java.util.stream.Stream.empty());

    @Test
    void viewExtensionRoundTripPreservesPistonStateAndMergesKeys() {
        RuntimeExtensionNbt.loadedChunkSave(true);
        var view = VersionStorage.writer(registries);
        RuntimeExtensionNbt.write(view, n -> n.putFloat("Progress", 0.5f));
        RuntimeExtensionNbt.write(view, n -> n.putLong("SavedWorldTime", 31));
        var read =
                NbtReadView.create(
                        new VersionStorage.ThrowingReporter("test"), registries, view.getNbt());
        var extension = RuntimeExtensionNbt.read(read);
        assertNotNull(extension);
        assertEquals(0.5f, extension.getFloat("Progress").orElseThrow());
        assertEquals(31, extension.getLong("SavedWorldTime").orElseThrow());
    }

    @Test
    void malformedViewDataCannotBeIgnored() {
        var nbt = new NbtCompound();
        nbt.putString(RuntimeExtensionNbt.KEY, "invalid");
        var read = NbtReadView.create(new VersionStorage.ThrowingReporter("test"), registries, nbt);
        assertThrows(IllegalStateException.class, () -> RuntimeExtensionNbt.read(read));
    }

    @Test
    void encodingFailureIsNotAnIncompleteSuccessfulSnapshot() {
        var view = VersionStorage.writer(registries);
        var rejecting =
                com.mojang.serialization.Codec.STRING.flatXmap(
                        s -> com.mojang.serialization.DataResult.error(() -> "reject"),
                        s -> com.mojang.serialization.DataResult.error(() -> "reject"));
        assertThrows(IllegalStateException.class, () -> view.put("invalid", rejecting, "bad"));
    }

    @Test
    void abilitiesPreserveFlyingAndSpeedsWithoutAWorld() {
        var abilities = new PlayerAbilities();
        abilities.flying = true;
        abilities.allowFlying = true;
        abilities.setFlySpeed(0.3f);
        abilities.setWalkSpeed(0.17f);
        var restored = new PlayerAbilities();
        restored.unpack(VersionStorage.abilities(VersionStorage.abilities(abilities)));
        assertTrue(restored.flying);
        assertTrue(restored.allowFlying);
        assertEquals(0.3f, restored.getFlySpeed());
        assertEquals(0.17f, restored.getWalkSpeed());
        assertThrows(
                IllegalArgumentException.class, () -> VersionStorage.abilities(new NbtCompound()));
    }

    @Test
    void invalidInventoryDoesNotPartiallyApply() {
        var inventory = new net.minecraft.inventory.SimpleInventory(27);
        var nbt = new NbtList();
        nbt.add(NbtString.of("invalid"));
        assertThrows(RuntimeException.class, () -> VersionStorage.inventory(nbt, 27, registries));
        assertTrue(inventory.isEmpty());
    }

    @Test
    void emptyEnderChestRoundTripsItsFixedSlotShape() {
        var inventory = new net.minecraft.inventory.SimpleInventory(27);
        var encoded = VersionStorage.inventory(inventory, registries);
        var decoded = VersionStorage.inventory(encoded, 27, registries);
        assertEquals(27, decoded.size());
        VersionStorage.applyInventory(inventory, decoded);
        assertTrue(inventory.isEmpty());
    }
}
