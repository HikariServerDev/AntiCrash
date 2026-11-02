package com.atsukimc.anticrash.mixin;

import net.minecraft.world.tick.TickManager;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(TickManager.class)
public interface NativeTickManagerAccessor {
    @Accessor("shouldTick")
    void anticrash$setShouldTick(boolean value);
}
