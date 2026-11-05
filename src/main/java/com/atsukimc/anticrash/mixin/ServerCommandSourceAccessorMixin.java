package com.atsukimc.anticrash.mixin;

import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(CommandSourceStack.class)
public interface ServerCommandSourceAccessorMixin {
    @Accessor("source")
    CommandSource anticrash$getOutput();

    @Accessor("silent")
    boolean anticrash$isSilent();

    @Invoker("broadcastToAdmins")
    void anticrash$sendToOps(Component message);
}
