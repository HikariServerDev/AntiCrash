package com.atsukimc.anticrash.mixin;

import com.atsukimc.anticrash.runtime.RuntimeExtensionNbt;

//? if (>=1.18 && <=1.21.11) {
/*import net.minecraft.world.level.block.state.BlockState;
*///?}
//? if (>=26.1 && <=26.3) {
import net.minecraft.core.BlockPos;
//?}

//? if (>=1.18 && <=1.21.11) {
/*import net.minecraft.world.level.block.entity.TheEndGatewayBlockEntity;
*///?}
//? if (>=26.1 && <=26.3) {
import net.minecraft.nbt.CompoundTag;
//?}

//? if (>=1.18 && <=1.21.11) {
/*import net.minecraft.nbt.CompoundTag;
*///?}
//? if (>=26.1 && <=26.3) {
import net.minecraft.world.level.Level;
//?}

//? if (>=1.18 && <=1.21.11) {
/*import net.minecraft.core.BlockPos;
*///?}
//? if (>=26.1 && <=26.3) {
import net.minecraft.world.level.block.entity.TheEndGatewayBlockEntity;
//?}

//? if (>=1.18 && <=1.21.11) {
/*import net.minecraft.world.level.Level;
*///?}
//? if (>=26.1 && <=26.3) {
import net.minecraft.world.level.block.state.BlockState;
//?}

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

//? if (>=1.18 && <=1.21.11) {
/*@Mixin(TheEndGatewayBlockEntity.class)
public abstract class EndGatewayBlockEntityMixin {

    *///?} else if (>=26.1 && <=26.3) {
@Mixin(TheEndGatewayBlockEntity.class)
public abstract class EndGatewayBlockEntityMixin {
    //?}
    @Shadow private int teleportCooldown;

    /**
     * "Age" is saved by Vanilla but advances every tick without the chunk being marked for saving,
     * so a checkpoint kept whatever age the chunk was last saved with. Every 2400 ticks of age the
     * gateway starts a 40-tick teleport cooldown; a stale age moves that moment.
     */

    //? if (>=1.18 && <=1.21.11) {
    /*//? if (>=1.18 && <=1.20.6) {
    /^@Inject(method = "teleportTick", at = @At("TAIL"))
    ^///?} else if (>=1.21 && <=1.21.11) {
    /^@Inject(method = "portalTick", at = @At("TAIL"))
    ^///?}
    private static void anticrash$markChunkDirty(
            Level world,
            BlockPos pos,
            BlockState state,
            TheEndGatewayBlockEntity gateway,
            CallbackInfo callbackInfo)
                *///?} else if (>=26.1 && <=26.3) {
    @Inject(method = "portalTick", at = @At("TAIL"))
    private static void anticrash$markChunkDirty(
            Level world,
            BlockPos pos,
            BlockState state,
            TheEndGatewayBlockEntity gateway,
            CallbackInfo callbackInfo)
                //?}
            {

        //? if (>=1.18 && <=1.21.11) {
        /*world.blockEntityChanged(pos);
        *///?} else if (>=26.1 && <=26.3) {
        world.blockEntityChanged(pos);
        //?}

    }

    /**
     * The cooldown itself is not saved: after a restart a gateway on cooldown teleports at once.
     */

    //? if (>=1.18 && <=1.20.4) {
    /*@Inject(method = "saveAdditional", at = @At("TAIL"))
    private void anticrash$saveTeleportCooldown(CompoundTag nbt, CallbackInfo callbackInfo)
                *///?} else if (>=1.20.5 && <=1.21.5) {
    /*@Inject(method = "saveAdditional", at = @At("TAIL"))
                    private void anticrash$saveTeleportCooldown(
                            CompoundTag nbt,
                            net.minecraft.core.HolderLookup.Provider registries,
                            CallbackInfo callbackInfo)
*///?} else if (>=1.21.6 && <=1.21.11) {
    /*@Inject(method = "saveAdditional", at = @At("TAIL"))
                    private void anticrash$saveTeleportCooldown(
                            net.minecraft.world.level.storage.ValueOutput nbt, CallbackInfo callbackInfo)
*///?} else if (>=26.1 && <=26.3) {
    @Inject(method = "saveAdditional", at = @At("TAIL"))
    private void anticrash$saveTeleportCooldown(
            net.minecraft.world.level.storage.ValueOutput nbt, CallbackInfo callbackInfo)
                //?}
            {
        if (!RuntimeExtensionNbt.savingLoadedChunk()) return;
        RuntimeExtensionNbt.write(
                nbt,
                extension -> {
                    if (teleportCooldown > 0)
                        extension.putInt("TeleportCooldown", teleportCooldown);
                    else extension.remove("TeleportCooldown");
                });
    }

    //? if (>=1.18 && <=1.20.4) {
    /*@Inject(method = "load", at = @At("TAIL"))
    private void anticrash$loadTeleportCooldown(CompoundTag nbt, CallbackInfo callbackInfo)
                *///?} else if (>=1.20.5 && <=1.21.5) {
    /*@Inject(method = "loadAdditional", at = @At("TAIL"))
                    private void anticrash$loadTeleportCooldown(
                            CompoundTag nbt,
                            net.minecraft.core.HolderLookup.Provider registries,
                            CallbackInfo callbackInfo)
*///?} else if (>=1.21.6 && <=1.21.11) {
    /*@Inject(method = "loadAdditional", at = @At("TAIL"))
                    private void anticrash$loadTeleportCooldown(
                            net.minecraft.world.level.storage.ValueInput nbt, CallbackInfo callbackInfo)
*///?} else if (>=26.1 && <=26.3) {
    @Inject(method = "loadAdditional", at = @At("TAIL"))
    private void anticrash$loadTeleportCooldown(
            net.minecraft.world.level.storage.ValueInput nbt, CallbackInfo callbackInfo)
                //?}
            {

        //? if (>=1.18 && <=1.21.11) {
        /*CompoundTag extension = RuntimeExtensionNbt.read(nbt);
        *///?} else if (>=26.1 && <=26.3) {
        CompoundTag extension = RuntimeExtensionNbt.read(nbt);
        //?}

        //? if (>=1.18 && <=1.21.4) {
        /*if (extension != null && extension.contains("TeleportCooldown", 99))
        *///?} else if (>=1.21.5 && <=26.3) {
        if (extension != null
                && com.atsukimc.anticrash.runtime.VersionNbt.contains(
                        extension, "TeleportCooldown", 99))
        //?}
        {

            //? if (>=1.18 && <=1.21.4) {
            /*teleportCooldown = Math.max(0, extension.getInt("TeleportCooldown"));
            *///?} else if (>=1.21.5 && <=26.3) {
            teleportCooldown = Math.max(0, extension.getInt("TeleportCooldown").orElseThrow());
            //?}

        }
    }
}
