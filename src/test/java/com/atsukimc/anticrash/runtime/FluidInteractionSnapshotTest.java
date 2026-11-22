//? if >=26.1 {
package com.atsukimc.anticrash.runtime;

import com.atsukimc.anticrash.util.VersionIds;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.nbt.*;
import net.minecraft.world.phys.Vec3;

import org.junit.jupiter.api.Test;

import java.util.List;

class FluidInteractionSnapshotTest {
    @Test
    void trackerRoundTripPreservesAllSemanticValues() {
        //? if <=26.2 {
        /*var state =
                new FluidInteractionSnapshot.State(
                        "minecraft:water", 0.875, true, new Vec3(0.5, -1, 2), 17);
        *///?} else {
        var state =
                new FluidInteractionSnapshot.State(
                        "minecraft:water", 0.875, false, new Vec3(0.5, -1, 2), 17);
        //?}

        assertEquals(state, FluidInteractionSnapshot.State.read(state.write()));
        var n = new ListTag();
        n.add(state.write());
        assertEquals(List.of(state), FluidInteractionSnapshot.decode(n));
    }

    @Test
    void duplicateWrongTypeAndInvalidFlowAreRejected() {
        var state = new FluidInteractionSnapshot.State("minecraft:water", 0, false, Vec3.ZERO, 0);
        var duplicate = new ListTag();
        duplicate.add(state.write());
        duplicate.add(state.write());
        assertThrows(
                IllegalArgumentException.class, () -> FluidInteractionSnapshot.decode(duplicate));
        var wrong = new ListTag();
        wrong.add(StringTag.valueOf("invalid"));
        assertThrows(IllegalArgumentException.class, () -> FluidInteractionSnapshot.decode(wrong));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new FluidInteractionSnapshot.State(
                                "minecraft:water", 0, false, new Vec3(Double.NaN, 0, 0), 0));
    }

    //? if <=26.2 {
    /*@Test
    void decodedOrderingIsStableWithoutMutatingInput() {
        var water = new FluidInteractionSnapshot.State("minecraft:water", 1, true, Vec3.ZERO, 0);
    *///?} else {
    @Test
    void decodedOrderingPreservesNativeFirstMatchingTagWithoutMutatingInput() {
        var water = new FluidInteractionSnapshot.State("minecraft:water", 1, false, Vec3.ZERO, 0);
    //?}

        var lava = new FluidInteractionSnapshot.State("minecraft:lava", 0, false, Vec3.ZERO, 0);
        var n = new ListTag();
        n.add(water.write());
        n.add(lava.write());

        //? if <=26.2 {
        /*assertEquals(List.of(lava, water), FluidInteractionSnapshot.decode(n));
        *///?} else {
        assertEquals(List.of(water, lava), FluidInteractionSnapshot.decode(n));
        //?}

        assertEquals(water, FluidInteractionSnapshot.State.read((CompoundTag) n.get(0)));
    }

    //? if >=26.3 {
    @Test
    void contactAndCurrentWithSameIdentifierRemainDistinct() {
        var contact =
                new FluidInteractionSnapshot.State(
                        FluidInteractionSnapshot.Kind.FLUID,
                        "minecraft:water",
                        1,
                        true,
                        Vec3.ZERO,
                        0);

        var current =
                new FluidInteractionSnapshot.State(
                        "minecraft:water", 0.5, false, new Vec3(0.5, 0, 0), 2);

        var n = new ListTag();

        n.add(contact.write());

        n.add(current.write());

        assertEquals(List.of(contact, current), FluidInteractionSnapshot.decode(n));
    }

    @Test
    void wrongDomainOrOrderCannotBeApplied() {
        var water = new FluidInteractionSnapshot.State("minecraft:water", 0, false, Vec3.ZERO, 0);

        var lava = new FluidInteractionSnapshot.State("minecraft:lava", 0, false, Vec3.ZERO, 0);

        var expected = List.of(water, lava);

        assertDoesNotThrow(() ->
                FluidInteractionSnapshot.validateDomains(
                        expected, List.of(), List.of("minecraft:water", "minecraft:lava")));

        assertThrows(
                IllegalStateException.class,
                () ->
                        FluidInteractionSnapshot.validateDomains(
                                expected, List.of(), List.of("minecraft:lava", "minecraft:water")));

        assertThrows(
                IllegalStateException.class,
                () ->
                        FluidInteractionSnapshot.validateDomains(
                                expected,
                                List.of("minecraft:water"),
                                List.of("minecraft:water", "minecraft:lava")));
    }

    @Test
    void auditedNativeFactoryReusesTrackerWithoutReplacingCollection() throws Exception {
        var interaction = new net.minecraft.world.entity.EntityFluidInteraction(java.util.Set.of());

        var fluid =
                VersionIds.fluids()
                        .get(
                                net.minecraft.resources.ResourceKey.create(
                                        net.minecraft.core.registries.Registries.FLUID,
                                        VersionIds.parse("minecraft:water")))
                        .orElseThrow();

        var first = FluidInteractionSnapshot.createTracker(interaction, fluid);

        assertSame(first, FluidInteractionSnapshot.createTracker(interaction, fluid));

        var list = interaction.getClass().getDeclaredField("fluidTrackers");

        list.setAccessible(true);

        assertEquals(1, ((List<?>) list.get(interaction)).size());
    }
    //?}

}
//?}
