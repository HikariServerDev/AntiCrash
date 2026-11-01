package com.atsukimc.anticrash.mixin;

import net.minecraft.entity.TntEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(TntEntity.class)
public interface TntRuntimeAccessorMixin {
    @Accessor("teleported")
    boolean anticrash$teleported();

    @Accessor("teleported")
    void anticrash$setTeleported(boolean value);
}
