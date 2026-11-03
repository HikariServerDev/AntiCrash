package com.atsukimc.anticrash.mixin;

//? if (>=1.18 && <=1.21.11) {
import net.minecraft.world.level.block.RedstoneTorchBlock;
//?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.core.BlockPos;
*///?}

//? if (>=1.18 && <=1.21.11) {
import net.minecraft.core.BlockPos;
//?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.world.level.block.RedstoneTorchBlock;
*///?}

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

//? if (>=1.18 && <=1.21.11) {
@Mixin(RedstoneTorchBlock.Toggle.class)
public interface RedstoneTorchBurnoutEntryAccessorMixin {

    //?}
    //? if (>=26.1 && <=26.3) {
    /*@Mixin(RedstoneTorchBlock.Toggle.class)
    public interface RedstoneTorchBurnoutEntryAccessorMixin {
*///?}
    @Accessor("pos")
    BlockPos anticrash$getPos();

    //? if (>=1.18 && <=1.21.11) {
    @Accessor("when")
    long anticrash$getTime();
    //?}
    //? if (>=26.1 && <=26.3) {
    /*@Accessor("when")
        long anticrash$getTime();
*///?}

}
