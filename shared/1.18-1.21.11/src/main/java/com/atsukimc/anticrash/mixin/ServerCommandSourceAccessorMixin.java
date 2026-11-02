package com.atsukimc.anticrash.mixin;

import net.minecraft.server.command.CommandOutput;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ServerCommandSource.class)
public interface ServerCommandSourceAccessorMixin {
    @Accessor("output")
    CommandOutput anticrash$getOutput();

    @Accessor("silent")
    boolean anticrash$isSilent();

    @Invoker("sendToOps")
    void anticrash$sendToOps(Text message);
}
