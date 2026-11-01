package com.atsukimc.anticrash.mixin;

import com.atsukimc.anticrash.checkpoint.CutSave;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import com.atsukimc.anticrash.AntiCrashController;
import com.atsukimc.anticrash.AntiCrashMod;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LocalMobCapCalculator;
import net.minecraft.world.level.NaturalSpawner;

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
@Mixin(ServerChunkCache.class)
public abstract class ServerChunkManagerMixin {
    @Unique
    private NaturalSpawner.SpawnState anticrash$emptySpawnInfo;

    @Inject(method = "getLastSpawnState", at = @At("RETURN"), cancellable = true)
    private void anticrash$nonNullSpawnInfoDuringRecovery(
            CallbackInfoReturnable<NaturalSpawner.SpawnState> callbackInfo) {
        if (callbackInfo.getReturnValue() != null) return;

        AntiCrashController controller = AntiCrashMod.controller();
        if (controller == null || !controller.needsSpawnInfoFallback()) return;

        if (anticrash$emptySpawnInfo == null) {
            ServerChunkCache manager = (ServerChunkCache) (Object) this;
            anticrash$emptySpawnInfo = NaturalSpawner.createState(
                    0,
                    List.<Entity>of(),
                    (chunkPos, consumer) -> { },
                    new LocalMobCapCalculator(manager.chunkMap));
        }
        callbackInfo.setReturnValue(anticrash$emptySpawnInfo);
    }

    /**
     * {@code save(boolean)} first processes pending ticket changes, which loads, promotes or
     * demotes chunks. At a checkpoint cut that would change the world after its Runtime Capsule
     * was taken; the changes are applied by the next tick exactly as they would have been.
     */
    @WrapOperation(method = "save", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerChunkCache;runDistanceManagerUpdates()Z"))
    private boolean anticrash$keepTicketsAtCut(ServerChunkCache manager, Operation<Boolean> original) {
        if (CutSave.active()) return false;
        return original.call(manager);
    }
}
