package com.atsukimc.anticrash.mixin;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.TickingBlockEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(Level.class)
public interface WorldAccessorMixin {
    @Accessor("blockEntityTickers")
    List<TickingBlockEntity> anticrash$getBlockEntityTickers();

    @Accessor("pendingBlockEntityTickers")
    List<TickingBlockEntity> anticrash$getPendingBlockEntityTickers();

    /** Next global sub-tick sequence value. Calling World#getTickOrder would increment it. */
    @Accessor("subTickCount")
    long anticrash$getNextTickOrder();

    @Accessor("subTickCount")
    void anticrash$setNextTickOrder(long nextTickOrder);

    /** Mapped Minecraft 1.18.2 field; unlike java.util.Random internals this is safe to expose. */
    @Accessor("randValue")
    int anticrash$getLcgBlockSeed();

    @Accessor("randValue")
    void anticrash$setLcgBlockSeed(int seed);

    @Accessor("rainLevel")
    float anticrash$getRainGradient();

    @Accessor("rainLevel")
    void anticrash$setRainGradientValue(float value);

    @Accessor("oRainLevel")
    void anticrash$setRainGradientPrev(float value);

    @Accessor("thunderLevel")
    float anticrash$getThunderGradient();

    @Accessor("thunderLevel")
    void anticrash$setThunderGradientValue(float value);

    @Accessor("oThunderLevel")
    void anticrash$setThunderGradientPrev(float value);
}
