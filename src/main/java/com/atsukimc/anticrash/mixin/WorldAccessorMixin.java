package com.atsukimc.anticrash.mixin;

//? if (>=1.18 && <=1.21.11) {
import net.minecraft.world.level.Level;
//?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.world.level.Level;
*///?}

//? if (>=1.18 && <=1.21.11) {
import net.minecraft.world.level.block.entity.TickingBlockEntity;
//?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.world.level.block.entity.TickingBlockEntity;
*///?}

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

//? if (>=1.18 && <=1.21.11) {
@Mixin(Level.class)
public interface WorldAccessorMixin {
    @Accessor("blockEntityTickers")
    List<TickingBlockEntity> anticrash$getBlockEntityTickers();

    //?}
    //? if (>=26.1 && <=26.3) {
    /*@Mixin(Level.class)
    public interface WorldAccessorMixin {
        @Accessor("blockEntityTickers")
        List<TickingBlockEntity> anticrash$getBlockEntityTickers();
*///?}

    //? if (>=1.18 && <=1.21.11) {
    @Accessor("pendingBlockEntityTickers")
    List<TickingBlockEntity> anticrash$getPendingBlockEntityTickers();

    //?}
    //? if (>=26.1 && <=26.3) {
    /*@Accessor("pendingBlockEntityTickers")
        List<TickingBlockEntity> anticrash$getPendingBlockEntityTickers();
*///?}

    /** Next global sub-tick sequence value. Calling World#getTickOrder would increment it. */

    //? if (>=1.18 && <=1.21.11) {
    @Accessor("subTickCount")
    long anticrash$getNextTickOrder();

    //?}
    //? if (>=26.1 && <=26.3) {
    /*@Accessor("subTickCount")
        long anticrash$getNextTickOrder();
*///?}

    //? if (>=1.18 && <=1.21.11) {
    @Accessor("subTickCount")
    void anticrash$setNextTickOrder(long nextTickOrder);

    //?}
    //? if (>=26.1 && <=26.3) {
    /*@Accessor("subTickCount")
        void anticrash$setNextTickOrder(long nextTickOrder);
*///?}

    /** Mapped Minecraft 1.18.2 field; unlike java.util.Random internals this is safe to expose. */

    //? if (>=1.18 && <=1.21.11) {
    @Accessor("randValue")
    int anticrash$getLcgBlockSeed();

    //?}
    //? if (>=26.1 && <=26.3) {
    /*@Accessor("randValue")
        int anticrash$getLcgBlockSeed();
*///?}

    //? if (>=1.18 && <=1.21.11) {
    @Accessor("randValue")
    void anticrash$setLcgBlockSeed(int seed);

    //?}
    //? if (>=26.1 && <=26.3) {
    /*@Accessor("randValue")
        void anticrash$setLcgBlockSeed(int seed);
*///?}

    //? if (>=1.18 && <=1.21.11) {
    @Accessor("rainLevel")
    float anticrash$getRainGradient();

    //?}
    //? if (>=26.1 && <=26.3) {
    /*@Accessor("rainLevel")
        float anticrash$getRainGradient();
*///?}

    //? if (>=1.18 && <=1.21.11) {
    @Accessor("rainLevel")
    void anticrash$setRainGradientValue(float value);

    //?}
    //? if (>=26.1 && <=26.3) {
    /*@Accessor("rainLevel")
        void anticrash$setRainGradientValue(float value);
*///?}

    //? if (>=1.18 && <=1.21.4) {
    @Accessor("oRainLevel")
    void anticrash$setRainGradientPrev(float value);

    //?}
    //? if (>=1.21.5 && <=1.21.11) {
    /*@Accessor("oRainLevel")
        void anticrash$setRainGradientPrev(float value);
*///?}
    //? if (>=26.1 && <=26.3) {
    /*@Accessor("oRainLevel")
        void anticrash$setRainGradientPrev(float value);
*///?}

    //? if (>=1.18 && <=1.21.11) {
    @Accessor("thunderLevel")
    float anticrash$getThunderGradient();

    //?}
    //? if (>=26.1 && <=26.3) {
    /*@Accessor("thunderLevel")
        float anticrash$getThunderGradient();
*///?}

    //? if (>=1.18 && <=1.21.11) {
    @Accessor("thunderLevel")
    void anticrash$setThunderGradientValue(float value);

    //?}
    //? if (>=26.1 && <=26.3) {
    /*@Accessor("thunderLevel")
        void anticrash$setThunderGradientValue(float value);
*///?}

    //? if (>=1.18 && <=1.21.4) {
    @Accessor("oThunderLevel")
    void anticrash$setThunderGradientPrev(float value);
    //?}
    //? if (>=1.21.5 && <=1.21.11) {
    /*@Accessor("oThunderLevel")
        void anticrash$setThunderGradientPrev(float value);
*///?}
    //? if (>=26.1 && <=26.3) {
    /*@Accessor("oThunderLevel")
        void anticrash$setThunderGradientPrev(float value);
*///?}

}
