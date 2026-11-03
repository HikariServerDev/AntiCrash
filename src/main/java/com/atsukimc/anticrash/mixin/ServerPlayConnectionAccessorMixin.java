//? if (>=1.19.4 && <=26.3) {
/*package com.atsukimc.anticrash.mixin;

//? if (>=1.19.4 && <=1.21.11) {
/^import net.minecraft.network.Connection;
^///?}
//? if (>=26.1 && <=26.3) {
/^import net.minecraft.network.Connection;
^///?}

//? if (>=1.19.4 && <=1.20.1) {
/^import net.minecraft.server.network.ServerGamePacketListenerImpl;
^///?}
//? if (>=1.20.2 && <=1.21.11) {
/^import net.minecraft.server.network.ServerCommonPacketListenerImpl;
^///?}
//? if (>=26.1 && <=26.3) {
/^import net.minecraft.server.network.ServerCommonPacketListenerImpl;
^///?}


import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

//? if (>=1.19.4 && <=1.20.1) {
/^@Mixin(ServerGamePacketListenerImpl.class)
public interface ServerPlayConnectionAccessorMixin {

^///?}
//? if (>=1.20.2 && <=1.21.11) {
/^@Mixin(ServerCommonPacketListenerImpl.class)
public interface ServerPlayConnectionAccessorMixin {

^///?}
//? if (>=1.19.4 && <=1.21.11) {
/^@Accessor("connection")
    Connection anticrash$getConnection();
^///?}
//? if (>=26.1 && <=26.3) {
/^@Mixin(ServerCommonPacketListenerImpl.class)
public interface ServerPlayConnectionAccessorMixin {
    @Accessor("connection")
    Connection anticrash$getConnection();
^///?}

}
*///?}
