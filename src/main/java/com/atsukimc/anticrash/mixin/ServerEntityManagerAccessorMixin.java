package com.atsukimc.anticrash.mixin;

//? if (>=1.18 && <=1.21.11) {
/*import net.minecraft.world.level.entity.PersistentEntitySectionManager;
*///?}
//? if (>=26.1 && <=26.3) {
import net.minecraft.world.level.entity.EntityPersistentStorage;
//?}

//? if (>=1.18 && <=1.21.11) {
/*import net.minecraft.world.level.entity.EntityPersistentStorage;
*///?}
//? if (>=26.1 && <=26.3) {
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
//?}

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

//? if (>=1.18 && <=1.21.11) {
/*@Mixin(PersistentEntitySectionManager.class)
public interface ServerEntityManagerAccessorMixin {
    @Accessor("permanentStorage")
    EntityPersistentStorage<?> anticrash$getDataAccess();
    *///?} else if (>=26.1 && <=26.3) {
@Mixin(PersistentEntitySectionManager.class)
public interface ServerEntityManagerAccessorMixin {
    @Accessor("permanentStorage")
    EntityPersistentStorage<?> anticrash$getDataAccess();
    //?}

}
