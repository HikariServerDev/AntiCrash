package com.atsukimc.anticrash.mixin;
import net.minecraft.world.entity.item.PrimedTnt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(PrimedTnt.class)
public interface TntRuntimeAccessorMixin {
    @Accessor("usedPortal") boolean anticrash$teleported();
    @Accessor("usedPortal") void anticrash$setTeleported(boolean value);
}
