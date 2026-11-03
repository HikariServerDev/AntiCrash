//? if (>=1.21.6 && <=26.3) {
package com.atsukimc.anticrash.runtime;

import static org.junit.jupiter.api.Assertions.*;

//? if (>=1.21.6 && <=1.21.11) {
/*import net.minecraft.world.entity.player.Abilities;
*///?}
//? if (>=26.1 && <=26.3) {
import net.minecraft.core.HolderLookup;
//?}

import net.minecraft.nbt.*;
//? if (>=1.21.6 && <=1.21.11) {
/*import net.minecraft.core.HolderLookup;
*///?}
//? if (>=26.1 && <=26.3) {
import net.minecraft.world.entity.player.Abilities;
//?}

//? if (>=1.21.6 && <=1.21.11) {
/*import net.minecraft.world.level.storage.*;
*///?}
//? if (>=26.1 && <=26.3) {
import net.minecraft.world.level.storage.*;
//?}

//? if (>=26.1 && <=26.3) {
import net.minecraft.world.level.storage.TagValueInput;
//?}

import org.junit.jupiter.api.Test;

class VersionStorageTest {
    @org.junit.jupiter.api.AfterEach
    void clearScope() {
        RuntimeExtensionNbt.loadedChunkSave(false);
        RuntimeExtensionNbt.entitySave(false);
    }

    //? if (>=1.21.6 && <=1.21.11) {
    /*private final HolderLookup.Provider registries =
            HolderLookup.Provider.create(java.util.stream.Stream.empty());
*///?} else if (>=26.1 && <=26.3) {
    private final HolderLookup.Provider registries =
            HolderLookup.Provider.create(java.util.stream.Stream.empty());

    //?}

    @Test
    void viewExtensionRoundTripPreservesPistonStateAndMergesKeys() {
        RuntimeExtensionNbt.loadedChunkSave(true);
        var view = VersionStorage.writer(registries);
        RuntimeExtensionNbt.write(view, n -> n.putFloat("Progress", 0.5f));
        RuntimeExtensionNbt.write(view, n -> n.putLong("SavedWorldTime", 31));

        //? if (>=1.21.6 && <=1.21.11) {
        /*var read =
                TagValueInput.create(
                        new VersionStorage.ThrowingReporter("test"), registries, view.buildResult());
*///?} else if (>=26.1 && <=26.3) {
        var read =
                TagValueInput.create(
                        new VersionStorage.ThrowingReporter("test"),
                        registries,
                        view.buildResult());
        //?}

        var extension = RuntimeExtensionNbt.read(read);
        assertNotNull(extension);
        assertEquals(0.5f, extension.getFloat("Progress").orElseThrow());
        assertEquals(31, extension.getLong("SavedWorldTime").orElseThrow());
    }

    @Test
    void malformedViewDataCannotBeIgnored() {

        //? if (>=1.21.6 && <=1.21.11) {
        /*var nbt = new CompoundTag();
*///?} else if (>=26.1 && <=26.3) {
        var nbt = new CompoundTag();
        //?}

        nbt.putString(RuntimeExtensionNbt.KEY, "invalid");

        //? if (>=1.21.6 && <=1.21.11) {
        /*var read = TagValueInput.create(new VersionStorage.ThrowingReporter("test"), registries, nbt);
*///?} else if (>=26.1 && <=26.3) {
        var read =
                TagValueInput.create(new VersionStorage.ThrowingReporter("test"), registries, nbt);
        //?}

        assertThrows(IllegalStateException.class, () -> RuntimeExtensionNbt.read(read));
    }

    @Test
    void encodingFailureIsNotAnIncompleteSuccessfulSnapshot() {
        var view = VersionStorage.writer(registries);
        var rejecting =
                com.mojang.serialization.Codec.STRING.flatXmap(
                        s -> com.mojang.serialization.DataResult.error(() -> "reject"),
                        s -> com.mojang.serialization.DataResult.error(() -> "reject"));

        //? if (>=1.21.6 && <=1.21.11) {
        /*assertThrows(IllegalStateException.class, () -> view.store("invalid", rejecting, "bad"));
*///?} else if (>=26.1 && <=26.3) {
        assertThrows(IllegalStateException.class, () -> view.store("invalid", rejecting, "bad"));
        //?}

    }

    @Test
    void abilitiesPreserveFlyingAndSpeedsWithoutAWorld() {

        //? if (>=1.21.6 && <=1.21.11) {
        /*var abilities = new Abilities();
*///?} else if (>=26.1 && <=26.3) {
        var abilities = new Abilities();
        //?}

        abilities.flying = true;

        //? if (>=1.21.6 && <=1.21.11) {
        /*abilities.mayfly = true;
*///?} else if (>=26.1 && <=26.3) {
        abilities.mayfly = true;
        //?}

        //? if (>=1.21.6 && <=1.21.11) {
        /*abilities.setFlyingSpeed(0.3f);
*///?} else if (>=26.1 && <=26.3) {
        abilities.setFlyingSpeed(0.3f);
        //?}

        //? if (>=1.21.6 && <=1.21.11) {
        /*abilities.setWalkingSpeed(0.17f);
*///?} else if (>=26.1 && <=26.3) {
        abilities.setWalkingSpeed(0.17f);
        //?}

        //? if (>=1.21.6 && <=1.21.11) {
        /*var restored = new Abilities();
*///?} else if (>=26.1 && <=26.3) {
        var restored = new Abilities();
        //?}

        //? if (>=1.21.6 && <=1.21.11) {
        /*restored.apply(VersionStorage.abilities(VersionStorage.abilities(abilities)));
*///?} else if (>=26.1 && <=26.3) {
        restored.apply(VersionStorage.abilities(VersionStorage.abilities(abilities)));
        //?}

        assertTrue(restored.flying);

        //? if (>=1.21.6 && <=1.21.11) {
        /*assertTrue(restored.mayfly);
*///?} else if (>=26.1 && <=26.3) {
        assertTrue(restored.mayfly);
        //?}

        //? if (>=1.21.6 && <=1.21.11) {
        /*assertEquals(0.3f, restored.getFlyingSpeed());
*///?} else if (>=26.1 && <=26.3) {
        assertEquals(0.3f, restored.getFlyingSpeed());
        //?}

        //? if (>=1.21.6 && <=1.21.11) {
        /*assertEquals(0.17f, restored.getWalkingSpeed());
*///?} else if (>=26.1 && <=26.3) {
        assertEquals(0.17f, restored.getWalkingSpeed());
        //?}

        //? if (>=1.21.6 && <=1.21.11) {
        /*assertThrows(
                IllegalArgumentException.class, () -> VersionStorage.abilities(new CompoundTag()));
*///?} else if (>=26.1 && <=26.3) {
        assertThrows(
                IllegalArgumentException.class, () -> VersionStorage.abilities(new CompoundTag()));
        //?}

    }

    @Test
    void invalidInventoryDoesNotPartiallyApply() {

        //? if (>=1.21.6 && <=1.21.11) {
        /*var inventory = new net.minecraft.world.SimpleContainer(27);
*///?} else if (>=26.1 && <=26.3) {
        var inventory = new net.minecraft.world.SimpleContainer(27);
        //?}

        //? if (>=1.21.6 && <=1.21.11) {
        /*var nbt = new ListTag();
*///?} else if (>=26.1 && <=26.3) {
        var nbt = new ListTag();
        //?}

        //? if (>=1.21.6 && <=1.21.11) {
        /*nbt.add(StringTag.valueOf("invalid"));
*///?} else if (>=26.1 && <=26.3) {
        nbt.add(StringTag.valueOf("invalid"));
        //?}

        assertThrows(RuntimeException.class, () -> VersionStorage.inventory(nbt, 27, registries));
        assertTrue(inventory.isEmpty());
    }

    @Test
    void emptyEnderChestRoundTripsItsFixedSlotShape() {

        //? if (>=1.21.6 && <=1.21.11) {
        /*var inventory = new net.minecraft.world.SimpleContainer(27);
*///?} else if (>=26.1 && <=26.3) {
        var inventory = new net.minecraft.world.SimpleContainer(27);
        //?}

        var encoded = VersionStorage.inventory(inventory, registries);
        var decoded = VersionStorage.inventory(encoded, 27, registries);
        assertEquals(27, decoded.size());
        VersionStorage.applyInventory(inventory, decoded);
        assertTrue(inventory.isEmpty());
    }
}
//?}
