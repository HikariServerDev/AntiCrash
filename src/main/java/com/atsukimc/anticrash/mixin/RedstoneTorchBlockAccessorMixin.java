package com.atsukimc.anticrash.mixin;

//? if (>=1.18 && <=1.21.11) {
import net.minecraft.world.level.block.RedstoneTorchBlock;
//?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.world.level.BlockGetter;
*///?}

//? if (>=1.18 && <=1.21.11) {
import net.minecraft.world.level.BlockGetter;
//?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.world.level.block.RedstoneTorchBlock;
*///?}

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;
import java.util.Map;

/** Read/write is restricted to replacing the new world's own burnout history during recovery. */
@Mixin(RedstoneTorchBlock.class)
public interface RedstoneTorchBlockAccessorMixin {

    //? if (>=1.18 && <=1.21.11) {
    @Accessor("RECENT_TOGGLES")
    static Map<BlockGetter, List<RedstoneTorchBlock.Toggle>> anticrash$getBurnoutMap()
                //?}
                //? if (>=26.1 && <=26.3) {
                /*@Accessor("RECENT_TOGGLES")
                    static Map<BlockGetter, List<RedstoneTorchBlock.Toggle>> anticrash$getBurnoutMap()
*///?}
            {
        throw new AssertionError("Mixin accessor");
    }
}
