package com.atsukimc.anticrash.mixin;

import com.atsukimc.anticrash.runtime.RuntimeExtensionNbt;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.EndGatewayBlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EndGatewayBlockEntity.class)
public abstract class EndGatewayBlockEntityMixin {
    @Shadow private int teleportCooldown;

    /**
     * "Age" is saved by Vanilla but advances every tick without the chunk being marked for saving,
     * so a checkpoint kept whatever age the chunk was last saved with. Every 2400 ticks of age the
     * gateway starts a 40-tick teleport cooldown; a stale age moves that moment.
     */
    @Inject(method = "serverTick", at = @At("TAIL"))
    private static void anticrash$markChunkDirty(
            World world,
            BlockPos pos,
            BlockState state,
            EndGatewayBlockEntity gateway,
            CallbackInfo callbackInfo) {
        world.markDirty(pos);
    }

    /**
     * The cooldown itself is not saved: after a restart a gateway on cooldown teleports at once.
     */
    @Inject(method = "writeNbt", at = @At("TAIL"))
    private void anticrash$saveTeleportCooldown(NbtCompound nbt, CallbackInfo callbackInfo) {
        if (!RuntimeExtensionNbt.savingLoadedChunk()) return;
        RuntimeExtensionNbt.write(
                nbt,
                extension -> {
                    if (teleportCooldown > 0)
                        extension.putInt("TeleportCooldown", teleportCooldown);
                    else extension.remove("TeleportCooldown");
                });
    }

    @Inject(method = "readNbt", at = @At("TAIL"))
    private void anticrash$loadTeleportCooldown(NbtCompound nbt, CallbackInfo callbackInfo) {
        NbtCompound extension = RuntimeExtensionNbt.read(nbt);
        if (extension != null && extension.contains("TeleportCooldown", 99)) {
            teleportCooldown = Math.max(0, extension.getInt("TeleportCooldown"));
        }
    }
}
