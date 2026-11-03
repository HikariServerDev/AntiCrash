package com.atsukimc.anticrash.mixin;

//? if (>=1.18 && <=1.21.11) {
/*import net.minecraft.world.level.block.entity.TheEndGatewayBlockEntity;
*///?}
//? if (>=26.1 && <=26.3) {
import net.minecraft.world.level.block.entity.TheEndGatewayBlockEntity;
//?}

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

//? if (>=1.18 && <=1.21.11) {
/*@Mixin(TheEndGatewayBlockEntity.class)
public interface EndGatewayBlockEntityAccessorMixin {

    *///?} else if (>=26.1 && <=26.3) {
@Mixin(TheEndGatewayBlockEntity.class)
public interface EndGatewayBlockEntityAccessorMixin {
    //?}
    @Accessor("teleportCooldown")
    int anticrash$getTeleportCooldown();
}
