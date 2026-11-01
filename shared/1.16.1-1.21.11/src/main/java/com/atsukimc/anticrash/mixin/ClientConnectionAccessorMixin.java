package com.atsukimc.anticrash.mixin;

import io.netty.channel.Channel;
import net.minecraft.network.ClientConnection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientConnection.class)
public interface ClientConnectionAccessorMixin {
    /** Null for a connection that never had a network channel (a Carpet FakePlayer). */
    @Accessor("channel")
    Channel anticrash$getChannel();
}
