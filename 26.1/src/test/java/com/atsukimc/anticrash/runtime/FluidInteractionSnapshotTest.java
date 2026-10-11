package com.atsukimc.anticrash.runtime;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import net.minecraft.nbt.*;
import net.minecraft.world.phys.Vec3;
class FluidInteractionSnapshotTest {
    @Test void trackerRoundTripPreservesAllSemanticValues() {
        var state=new FluidInteractionSnapshot.State("minecraft:water",0.875,true,new Vec3(0.5,-1,2),17);
        assertEquals(state,FluidInteractionSnapshot.State.read(state.write()));
        var n=new ListTag(); n.add(state.write());
        assertEquals(java.util.List.of(state),FluidInteractionSnapshot.decode(n));
    }
    @Test void duplicateWrongTypeAndInvalidFlowAreRejected() {
        var state=new FluidInteractionSnapshot.State("minecraft:water",0,false,Vec3.ZERO,0);
        var duplicate=new ListTag(); duplicate.add(state.write()); duplicate.add(state.write());
        assertThrows(IllegalArgumentException.class,()->FluidInteractionSnapshot.decode(duplicate));
        var wrong=new ListTag(); wrong.add(StringTag.valueOf("invalid"));
        assertThrows(IllegalArgumentException.class,()->FluidInteractionSnapshot.decode(wrong));
        assertThrows(IllegalArgumentException.class,()->new FluidInteractionSnapshot.State("minecraft:water",0,false,new Vec3(Double.NaN,0,0),0));
    }
    @Test void decodedOrderingIsStableWithoutMutatingInput() {
        var water=new FluidInteractionSnapshot.State("minecraft:water",1,true,Vec3.ZERO,0);
        var lava=new FluidInteractionSnapshot.State("minecraft:lava",0,false,Vec3.ZERO,0);
        var n=new ListTag(); n.add(water.write()); n.add(lava.write());
        assertEquals(java.util.List.of(lava,water),FluidInteractionSnapshot.decode(n));
        assertEquals(water,FluidInteractionSnapshot.State.read((CompoundTag)n.get(0)));
    }
}
