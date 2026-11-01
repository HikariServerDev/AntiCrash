package com.atsukimc.anticrash.mixin;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityTickList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(EntityTickList.class)
public interface EntityListAccessorMixin {
    @Accessor("active")
    Int2ObjectMap<Entity> anticrash$getEntities();

    @Accessor("iterated")
    Int2ObjectMap<Entity> anticrash$getIterating();
}
