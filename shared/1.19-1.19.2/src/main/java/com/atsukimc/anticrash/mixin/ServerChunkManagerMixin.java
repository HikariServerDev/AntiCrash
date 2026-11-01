package com.atsukimc.anticrash.mixin;

import com.atsukimc.anticrash.checkpoint.CutSave;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import com.atsukimc.anticrash.AntiCrashController;
import com.atsukimc.anticrash.AntiCrashMod;
import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerChunkManager;
import net.minecraft.world.SpawnDensityCapper;
import net.minecraft.world.SpawnHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Keeps optional consumers such as Carpet's mobcap HUD safe while AntiCrash is
 * deliberately preventing the first world tick after a recovery.
 *
 * <p>Vanilla initializes {@code ServerChunkManager.spawnInfo} from
 * {@code tickChunks}. Recovery freeze must not run that method just to satisfy
 * an observer, because doing so would advance the restored world. Instead this
 * mixin supplies an immutable, empty view until Vanilla has produced the real
 * value. The backing field itself is never changed.</p>
 */
@Mixin(ServerChunkManager.class)
public abstract class ServerChunkManagerMixin {
    @Unique
    private SpawnHelper.Info anticrash$emptySpawnInfo;

    @Inject(method = "getSpawnInfo", at = @At("RETURN"), cancellable = true)
    private void anticrash$nonNullSpawnInfoDuringRecovery(
            CallbackInfoReturnable<SpawnHelper.Info> callbackInfo) {
        if (callbackInfo.getReturnValue() != null) return;

        AntiCrashController controller = AntiCrashMod.controller();
        if (controller == null || !controller.needsSpawnInfoFallback()) return;

        if (anticrash$emptySpawnInfo == null) {
            ServerChunkManager manager = (ServerChunkManager) (Object) this;
            anticrash$emptySpawnInfo = SpawnHelper.setupSpawn(
                    0,
                    List.<Entity>of(),
                    (chunkPos, consumer) -> { },
                    new SpawnDensityCapper(manager.threadedAnvilChunkStorage));
        }
        callbackInfo.setReturnValue(anticrash$emptySpawnInfo);
    }

    /**
     * {@code save(boolean)} first processes pending ticket changes, which loads, promotes or
     * demotes chunks. At a checkpoint cut that would change the world after its Runtime Capsule
     * was taken; the changes are applied by the next tick exactly as they would have been.
     */
    @WrapOperation(method = "save", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/world/ServerChunkManager;tick()Z"))
    private boolean anticrash$keepTicketsAtCut(ServerChunkManager manager, Operation<Boolean> original) {
        if (CutSave.active()) return false;
        return original.call(manager);
    }
}
