package com.atsukimc.anticrash.mixin;

import io.netty.channel.Channel;

//? if (>=1.18 && <=1.21.11) {
/*import net.minecraft.network.Connection;
*///?}
//? if (>=26.1 && <=26.3) {
import net.minecraft.network.Connection;
//?}

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

//? if (>=1.18 && <=1.21.11) {
/*@Mixin(Connection.class)
public interface ClientConnectionAccessorMixin {
    /^* Null for a connection that never had a network channel (a Carpet FakePlayer). ^/

    *///?} else if (>=26.1 && <=26.3) {
@Mixin(Connection.class)
public interface ClientConnectionAccessorMixin {
    /** Null for a connection that never had a network channel (a Carpet FakePlayer). */
    //?}
    @Accessor("channel")
    Channel anticrash$getChannel();
}
