//? if (>=26.1 && <=26.3) {
/*package com.atsukimc.anticrash.runtime;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.nbt.*;
import net.minecraft.world.phys.Vec3;

import org.junit.jupiter.api.Test;

class FluidInteractionSnapshotTest {
    @Test
    void trackerRoundTripPreservesAllSemanticValues() {

//? if (>=26.1 && <=26.2) {
/^var state =
                new FluidInteractionSnapshot.State(
                        "minecraft:water", 0.875, true, new Vec3(0.5, -1, 2), 17);
^///?}
//? if 26.3 {
/^var state =
                new FluidInteractionSnapshot.State(
                        "minecraft:water", 0.875, false, new Vec3(0.5, -1, 2), 17);
^///?}

        assertEquals(state, FluidInteractionSnapshot.State.read(state.write()));
        var n = new ListTag();
        n.add(state.write());
        assertEquals(java.util.List.of(state), FluidInteractionSnapshot.decode(n));
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


//? if (>=26.1 && <=26.2) {
/^@Test
    void decodedOrderingIsStableWithoutMutatingInput()
^///?}
//? if 26.3 {
/^@Test
    void decodedOrderingPreservesNativeFirstMatchingTagWithoutMutatingInput()
^///?}
{

//? if (>=26.1 && <=26.2) {
/^var water = new FluidInteractionSnapshot.State("minecraft:water", 1, true, Vec3.ZERO, 0);
^///?}
//? if 26.3 {
/^var water = new FluidInteractionSnapshot.State("minecraft:water", 1, false, Vec3.ZERO, 0);
^///?}

        var lava = new FluidInteractionSnapshot.State("minecraft:lava", 0, false, Vec3.ZERO, 0);
        var n = new ListTag();
        n.add(water.write());
        n.add(lava.write());

//? if (>=26.1 && <=26.2) {
/^assertEquals(java.util.List.of(lava, water), FluidInteractionSnapshot.decode(n));
^///?}
//? if 26.3 {
/^assertEquals(java.util.List.of(water, lava), FluidInteractionSnapshot.decode(n));
^///?}

        assertEquals(water, FluidInteractionSnapshot.State.read((CompoundTag) n.get(0)));
    }


//? if 26.3 {
/^@Test
    void contactAndCurrentWithSameIdentifierRemainDistinct() {
        var contact =
                new FluidInteractionSnapshot.State(
                        FluidInteractionSnapshot.Kind.FLUID,
                        "minecraft:water",
                        1,
                        true,
                        Vec3.ZERO,
                        0);
^///?}


//? if 26.3 {
/^var current =
                new FluidInteractionSnapshot.State(
                        "minecraft:water", 0.5, false, new Vec3(0.5, 0, 0), 2);
^///?}


//? if 26.3 {
/^var n = new ListTag();
^///?}


//? if 26.3 {
/^n.add(contact.write());
^///?}


//? if 26.3 {
/^n.add(current.write());
^///?}


//? if 26.3 {
/^assertEquals(java.util.List.of(contact, current), FluidInteractionSnapshot.decode(n));
    }
^///?}



//? if 26.3 {
/^@Test
    void wrongDomainOrOrderCannotBeApplied() {
        var water = new FluidInteractionSnapshot.State("minecraft:water", 0, false, Vec3.ZERO, 0);
^///?}


//? if 26.3 {
/^var lava = new FluidInteractionSnapshot.State("minecraft:lava", 0, false, Vec3.ZERO, 0);
^///?}


//? if 26.3 {
/^var expected = java.util.List.of(water, lava);
^///?}


//? if 26.3 {
/^assertDoesNotThrow(() ->
                FluidInteractionSnapshot.validateDomains(
                        expected,
                        java.util.List.of(),
                        java.util.List.of("minecraft:water", "minecraft:lava")));
^///?}


//? if 26.3 {
/^assertThrows(
                IllegalStateException.class,
                () ->
                        FluidInteractionSnapshot.validateDomains(
                                expected,
                                java.util.List.of(),
                                java.util.List.of("minecraft:lava", "minecraft:water")));
^///?}


//? if 26.3 {
/^assertThrows(
                IllegalStateException.class,
                () ->
                        FluidInteractionSnapshot.validateDomains(
                                expected,
                                java.util.List.of("minecraft:water"),
                                java.util.List.of("minecraft:water", "minecraft:lava")));
    }
^///?}



//? if 26.3 {
/^@Test
    void auditedNativeFactoryReusesTrackerWithoutReplacingCollection() throws Exception {
        var interaction = new net.minecraft.world.entity.EntityFluidInteraction(java.util.Set.of());
^///?}


//? if 26.3 {
/^var fluid =
                net.minecraft.core.registries.BuiltInRegistries.FLUID
                        .get(
                                net.minecraft.resources.ResourceKey.create(
                                        net.minecraft.core.registries.Registries.FLUID,
                                        net.minecraft.resources.Identifier.parse(
                                                "minecraft:water")))
                        .orElseThrow();
^///?}


//? if 26.3 {
/^var first = FluidInteractionSnapshot.createTracker(interaction, fluid);
^///?}


//? if 26.3 {
/^assertSame(first, FluidInteractionSnapshot.createTracker(interaction, fluid));
^///?}


//? if 26.3 {
/^var list = interaction.getClass().getDeclaredField("fluidTrackers");
^///?}


//? if 26.3 {
/^list.setAccessible(true);
^///?}


//? if 26.3 {
/^assertEquals(1, ((java.util.List<?>) list.get(interaction)).size());
    }
^///?}

}
*///?}
