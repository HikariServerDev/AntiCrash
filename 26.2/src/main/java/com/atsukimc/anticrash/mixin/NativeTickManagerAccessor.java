package com.atsukimc.anticrash.mixin;

import net.minecraft.world.TickRateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(TickRateManager.class)
public interface NativeTickManagerAccessor {
    @Accessor("runGameElements") void anticrash$setShouldTick(boolean value);
}
