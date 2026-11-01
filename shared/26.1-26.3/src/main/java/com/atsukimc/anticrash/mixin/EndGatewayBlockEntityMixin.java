package com.atsukimc.anticrash.mixin;

import com.atsukimc.anticrash.runtime.RuntimeExtensionNbt;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.TheEndGatewayBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TheEndGatewayBlockEntity.class)
public abstract class EndGatewayBlockEntityMixin {
    @Shadow private int teleportCooldown;

    /**
     * "Age" is saved by Vanilla but advances every tick without the chunk being marked for
     * saving, so a checkpoint kept whatever age the chunk was last saved with. Every 2400 ticks
     * of age the gateway starts a 40-tick teleport cooldown; a stale age moves that moment.
     */
    @Inject(method = "portalTick", at = @At("TAIL"))
    private static void anticrash$markChunkDirty(Level world, BlockPos pos, BlockState state,
                                                  TheEndGatewayBlockEntity gateway, CallbackInfo callbackInfo) {
        world.blockEntityChanged(pos);
    }

    /** The cooldown itself is not saved: after a restart a gateway on cooldown teleports at once. */
    @Inject(method = "saveAdditional", at = @At("TAIL"))
    private void anticrash$saveTeleportCooldown(net.minecraft.world.level.storage.ValueOutput nbt, CallbackInfo callbackInfo) {
        if (!RuntimeExtensionNbt.savingLoadedChunk()) return;
        RuntimeExtensionNbt.write(nbt, extension -> {
            if (teleportCooldown > 0) extension.putInt("TeleportCooldown", teleportCooldown);
            else extension.remove("TeleportCooldown");
        });
    }

    @Inject(method = "loadAdditional", at = @At("TAIL"))
    private void anticrash$loadTeleportCooldown(net.minecraft.world.level.storage.ValueInput nbt, CallbackInfo callbackInfo) {
        CompoundTag extension = RuntimeExtensionNbt.read(nbt);
        if (extension != null && com.atsukimc.anticrash.runtime.VersionNbt.contains(extension, "TeleportCooldown", 99)) {
            teleportCooldown = Math.max(0, extension.getInt("TeleportCooldown").orElseThrow());
        }
    }
}
