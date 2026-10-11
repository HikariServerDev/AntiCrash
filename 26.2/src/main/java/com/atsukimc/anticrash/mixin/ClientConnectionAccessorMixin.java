package com.atsukimc.anticrash.mixin;

import io.netty.channel.Channel;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Connection.class)
public interface ClientConnectionAccessorMixin {
    /** Null for a connection that never had a network channel (a Carpet FakePlayer). */
    @Accessor("channel")
    Channel anticrash$getChannel();
}
