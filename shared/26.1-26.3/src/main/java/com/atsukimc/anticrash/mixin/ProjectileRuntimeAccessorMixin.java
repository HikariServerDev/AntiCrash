package com.atsukimc.anticrash.mixin;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(Projectile.class)
public interface ProjectileRuntimeAccessorMixin {
    @Accessor("lastDeflectedBy") Entity anticrash$lastDeflectedEntity();
    @Accessor("leftOwnerChecked") boolean anticrash$checkedForLeftOwner();
    @Accessor("leftOwnerChecked") void anticrash$setCheckedForLeftOwner(boolean value);
}
