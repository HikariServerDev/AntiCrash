//? if >=1.19.4 {
package com.atsukimc.anticrash.mixin;

//? if <=1.20.1 {
/*import net.minecraft.server.network.ServerGamePacketListenerImpl;
*///?} else {
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
//?}

import net.minecraft.network.Connection;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

//? if <=1.20.1 {
/*@Mixin(ServerGamePacketListenerImpl.class)
public interface ServerPlayConnectionAccessorMixin {
*///?} else {
@Mixin(ServerCommonPacketListenerImpl.class)
public interface ServerPlayConnectionAccessorMixin {
//?}

    @Accessor("connection")
    Connection anticrash$getConnection();
}
//?}
