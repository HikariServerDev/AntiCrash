//? if (>=1.21 && <=26.3) {
/*package com.atsukimc.anticrash.mixin;

//? if (>=1.21 && <=1.21.11) {
/^import net.minecraft.world.entity.Entity;
^///?}
//? if (>=26.1 && <=26.3) {
/^import net.minecraft.world.entity.Entity;
^///?}

//? if (>=1.21 && <=1.21.11) {
/^import net.minecraft.world.entity.projectile.Projectile;
^///?}
//? if (>=26.1 && <=26.3) {
/^import net.minecraft.world.entity.projectile.Projectile;
^///?}


import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

//? if (>=1.21 && <=1.21.11) {
/^@Mixin(Projectile.class)
public interface ProjectileRuntimeAccessorMixin {
    @Accessor("lastDeflectedBy")
    Entity anticrash$lastDeflectedEntity();
^///?}
//? if (>=26.1 && <=26.3) {
/^@Mixin(Projectile.class)
public interface ProjectileRuntimeAccessorMixin {
    @Accessor("lastDeflectedBy")
    Entity anticrash$lastDeflectedEntity();
^///?}



//? if (>=1.21.9 && <=1.21.11) {
/^@Accessor("leftOwnerChecked")
    boolean anticrash$checkedForLeftOwner();
^///?}
//? if (>=26.1 && <=26.3) {
/^@Accessor("leftOwnerChecked")
    boolean anticrash$checkedForLeftOwner();
^///?}



//? if (>=1.21.9 && <=1.21.11) {
/^@Accessor("leftOwnerChecked")
    void anticrash$setCheckedForLeftOwner(boolean value);
^///?}
//? if (>=26.1 && <=26.3) {
/^@Accessor("leftOwnerChecked")
    void anticrash$setCheckedForLeftOwner(boolean value);
^///?}

}
*///?}
