package com.atsukimc.anticrash.mixin;

import net.minecraft.world.World;
import net.minecraft.world.chunk.BlockEntityTickInvoker;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(World.class)
public interface WorldAccessorMixin {
    @Accessor("blockEntityTickers")
    List<BlockEntityTickInvoker> anticrash$getBlockEntityTickers();

    @Accessor("pendingBlockEntityTickers")
    List<BlockEntityTickInvoker> anticrash$getPendingBlockEntityTickers();

    /** Next global sub-tick sequence value. Calling World#getTickOrder would increment it. */
    @Accessor("tickOrder")
    long anticrash$getNextTickOrder();

    @Accessor("tickOrder")
    void anticrash$setNextTickOrder(long nextTickOrder);

    /** Mapped Minecraft 1.18.2 field; unlike java.util.Random internals this is safe to expose. */
    @Accessor("lcgBlockSeed")
    int anticrash$getLcgBlockSeed();

    @Accessor("lcgBlockSeed")
    void anticrash$setLcgBlockSeed(int seed);

    @Accessor("rainGradient")
    float anticrash$getRainGradient();

    @Accessor("rainGradient")
    void anticrash$setRainGradientValue(float value);

    @Accessor("rainGradientPrev")
    void anticrash$setRainGradientPrev(float value);

    @Accessor("thunderGradient")
    float anticrash$getThunderGradient();

    @Accessor("thunderGradient")
    void anticrash$setThunderGradientValue(float value);

    @Accessor("thunderGradientPrev")
    void anticrash$setThunderGradientPrev(float value);
}
