package com.atsukimc.anticrash.mixin;

import com.atsukimc.anticrash.runtime.RuntimeExtensionNbt;

//? if (>=1.18 && <=1.21.11) {
import net.minecraft.block.BlockState;
//?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.core.BlockPos;
*///?}

//? if (>=1.18 && <=1.21.11) {
import net.minecraft.block.entity.EndGatewayBlockEntity;
//?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.nbt.CompoundTag;
*///?}

//? if (>=1.18 && <=1.21.11) {
import net.minecraft.nbt.NbtCompound;
//?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.world.level.Level;
*///?}

//? if (>=1.18 && <=1.21.11) {
import net.minecraft.util.math.BlockPos;
//?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.world.level.block.entity.TheEndGatewayBlockEntity;
*///?}

//? if (>=1.18 && <=1.21.11) {
import net.minecraft.world.World;
//?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.world.level.block.state.BlockState;
*///?}

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

//? if (>=1.18 && <=1.21.11) {
@Mixin(EndGatewayBlockEntity.class)
public abstract class EndGatewayBlockEntityMixin {

    //?}
    //? if (>=26.1 && <=26.3) {
    /*@Mixin(TheEndGatewayBlockEntity.class)
    public abstract class EndGatewayBlockEntityMixin {
*///?}
    @Shadow private int teleportCooldown;

    /**
     * "Age" is saved by Vanilla but advances every tick without the chunk being marked for saving,
     * so a checkpoint kept whatever age the chunk was last saved with. Every 2400 ticks of age the
     * gateway starts a 40-tick teleport cooldown; a stale age moves that moment.
     */

    //? if (>=1.18 && <=1.21.11) {
    @Inject(method = "serverTick", at = @At("TAIL"))
    private static void anticrash$markChunkDirty(
            World world,
            BlockPos pos,
            BlockState state,
            EndGatewayBlockEntity gateway,
            CallbackInfo callbackInfo)
                //?}
                //? if (>=26.1 && <=26.3) {
                /*@Inject(method = "portalTick", at = @At("TAIL"))
                    private static void anticrash$markChunkDirty(
                            Level world,
                            BlockPos pos,
                            BlockState state,
                            TheEndGatewayBlockEntity gateway,
                            CallbackInfo callbackInfo)
*///?}
            {

        //? if (>=1.18 && <=1.21.11) {
        world.markDirty(pos);
        //?}
        //? if (>=26.1 && <=26.3) {
        /*world.blockEntityChanged(pos);
*///?}

    }

    /**
     * The cooldown itself is not saved: after a restart a gateway on cooldown teleports at once.
     */

    //? if (>=1.18 && <=1.20.4) {
    @Inject(method = "writeNbt", at = @At("TAIL"))
    private void anticrash$saveTeleportCooldown(NbtCompound nbt, CallbackInfo callbackInfo)
                //?}
                //? if (>=1.20.5 && <=1.21.5) {
                /*@Inject(method = "writeNbt", at = @At("TAIL"))
                    private void anticrash$saveTeleportCooldown(
                            NbtCompound nbt,
                            net.minecraft.registry.RegistryWrapper.WrapperLookup registries,
                            CallbackInfo callbackInfo)
*///?}
                //? if (>=1.21.6 && <=1.21.11) {
                /*@Inject(method = "writeData", at = @At("TAIL"))
                    private void anticrash$saveTeleportCooldown(
                            net.minecraft.storage.WriteView nbt, CallbackInfo callbackInfo)
*///?}
                //? if (>=26.1 && <=26.3) {
                /*@Inject(method = "saveAdditional", at = @At("TAIL"))
                    private void anticrash$saveTeleportCooldown(
                            net.minecraft.world.level.storage.ValueOutput nbt, CallbackInfo callbackInfo)
*///?}
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
    @Inject(method = "readNbt", at = @At("TAIL"))
    private void anticrash$loadTeleportCooldown(NbtCompound nbt, CallbackInfo callbackInfo)
                //?}
                //? if (>=1.20.5 && <=1.21.5) {
                /*@Inject(method = "readNbt", at = @At("TAIL"))
                    private void anticrash$loadTeleportCooldown(
                            NbtCompound nbt,
                            net.minecraft.registry.RegistryWrapper.WrapperLookup registries,
                            CallbackInfo callbackInfo)
*///?}
                //? if (>=1.21.6 && <=1.21.11) {
                /*@Inject(method = "readData", at = @At("TAIL"))
                    private void anticrash$loadTeleportCooldown(
                            net.minecraft.storage.ReadView nbt, CallbackInfo callbackInfo)
*///?}
                //? if (>=26.1 && <=26.3) {
                /*@Inject(method = "loadAdditional", at = @At("TAIL"))
                    private void anticrash$loadTeleportCooldown(
                            net.minecraft.world.level.storage.ValueInput nbt, CallbackInfo callbackInfo)
*///?}
            {

        //? if (>=1.18 && <=1.21.11) {
        NbtCompound extension = RuntimeExtensionNbt.read(nbt);
        //?}
        //? if (>=26.1 && <=26.3) {
        /*CompoundTag extension = RuntimeExtensionNbt.read(nbt);
*///?}

        //? if (>=1.18 && <=1.21.4) {
        if (extension != null && extension.contains("TeleportCooldown", 99))
        //?}
        //? if (>=1.21.5 && <=26.3) {
        /*if (extension != null
                        && com.atsukimc.anticrash.runtime.VersionNbt.contains(
                                extension, "TeleportCooldown", 99))
*///?}
        {

            //? if (>=1.18 && <=1.21.4) {
            teleportCooldown = Math.max(0, extension.getInt("TeleportCooldown"));
            //?}
            //? if (>=1.21.5 && <=26.3) {
            /*teleportCooldown = Math.max(0, extension.getInt("TeleportCooldown").orElseThrow());
*///?}

        }
    }
}
