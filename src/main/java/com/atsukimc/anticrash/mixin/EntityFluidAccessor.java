//? if >=26.1 {
package com.atsukimc.anticrash.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityFluidInteraction;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Entity.class)
public interface EntityFluidAccessor {
    @Accessor("fluidInteraction")
    EntityFluidInteraction anticrash$fluids();
}
//?}
