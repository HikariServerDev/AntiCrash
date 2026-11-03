package com.atsukimc.anticrash.mixin;

//? if (>=1.18 && <=1.21.11) {
import net.minecraft.commands.CommandSource;
//?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.commands.CommandSource;
*///?}

//? if (>=1.18 && <=1.21.11) {
import net.minecraft.commands.CommandSourceStack;
//?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.commands.CommandSourceStack;
*///?}

//? if (>=1.18 && <=1.21.11) {
import net.minecraft.network.chat.Component;
//?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.network.chat.Component;
*///?}

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

//? if (>=1.18 && <=1.21.11) {
@Mixin(CommandSourceStack.class)
public interface ServerCommandSourceAccessorMixin {
    @Accessor("source")
    CommandSource anticrash$getOutput();

    //?}
    //? if (>=26.1 && <=26.3) {
    /*@Mixin(CommandSourceStack.class)
    public interface ServerCommandSourceAccessorMixin {
        @Accessor("source")
        CommandSource anticrash$getOutput();
*///?}

    @Accessor("silent")
    boolean anticrash$isSilent();

    //? if (>=1.18 && <=1.21.11) {
    @Invoker("broadcastToAdmins")
    void anticrash$sendToOps(Component message);
    //?}
    //? if (>=26.1 && <=26.3) {
    /*@Invoker("broadcastToAdmins")
        void anticrash$sendToOps(Component message);
*///?}

}
