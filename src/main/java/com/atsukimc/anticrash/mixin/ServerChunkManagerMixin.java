package com.atsukimc.anticrash.mixin;

import com.atsukimc.anticrash.AntiCrashController;
import com.atsukimc.anticrash.AntiCrashMod;
import com.atsukimc.anticrash.checkpoint.CutSave;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

//? if (>=1.18 && <=1.21.11) {
import net.minecraft.entity.Entity;
//?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.server.level.ServerChunkCache;
*///?}

//? if (>=1.18 && <=1.21.11) {
import net.minecraft.server.world.ServerChunkManager;
//?}
//? if (>=26.1 && <=26.2) {
/*import net.minecraft.world.entity.Entity;
*///?}

//? if (>=1.18 && <=1.21.11) {
import net.minecraft.world.SpawnDensityCapper;
//?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.world.level.LocalMobCapCalculator;
*///?}

//? if (>=1.18 && <=1.21.11) {
import net.minecraft.world.SpawnHelper;
//?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.world.level.NaturalSpawner;
*///?}

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

//? if (>=1.18 && <=26.2) {
import java.util.List;

//?}

/**
 * Keeps optional consumers such as Carpet's mobcap HUD safe while AntiCrash is deliberately
 * preventing the first world tick after a recovery.
 *
 * <p>Vanilla initializes {@code ServerChunkManager.spawnInfo} from {@code tickChunks}. Recovery
 * freeze must not run that method just to satisfy an observer, because doing so would advance the
 * restored world. Instead this mixin supplies an immutable, empty view until Vanilla has produced
 * the real value. The backing field itself is never changed.
 */
//? if (>=1.18 && <=1.21.11) {
@Mixin(ServerChunkManager.class)
public abstract class ServerChunkManagerMixin {

    //?}
    //? if (>=1.18 && <=1.18.1) {
    /*@WrapOperation(
                method = "tick(Ljava/util/function/BooleanSupplier;)V",
                at =
                        @At(
                                value = "INVOKE",
                                target =
                                        "Lnet/minecraft/server/world/ServerChunkManager;tickChunks()V"))
        private void anticrash$skipSimulationInLifecycleScope(
                ServerChunkManager manager, Operation<Void> original) {
            if (!com.atsukimc.anticrash.recovery.VersionChunkLifecycle.simulationDisabled()) {
                original.call(manager);
            }
        }
*///?}
    //? if (>=26.1 && <=26.3) {
    /*@Mixin(ServerChunkCache.class)
    public abstract class ServerChunkManagerMixin {
*///?}
    //? if 26.3 {
    /*@org.spongepowered.asm.mixin.Shadow @org.spongepowered.asm.mixin.Final
        private net.minecraft.server.level.ServerLevel level;
*///?}

    //? if (>=26.1 && <=26.3) {
    /*@Unique private NaturalSpawner.SpawnState anticrash$emptySpawnInfo;
*///?}

    //? if (>=1.18 && <=1.21.11) {
    @Unique private SpawnHelper.Info anticrash$emptySpawnInfo;

    //?}

    //? if (>=1.18 && <=1.21.11) {
    @Inject(method = "getSpawnInfo", at = @At("RETURN"), cancellable = true)
    private void anticrash$nonNullSpawnInfoDuringRecovery(
            CallbackInfoReturnable<SpawnHelper.Info> callbackInfo)
                //?}
                //? if (>=26.1 && <=26.3) {
                /*@Inject(method = "getLastSpawnState", at = @At("RETURN"), cancellable = true)
                    private void anticrash$nonNullSpawnInfoDuringRecovery(
                            CallbackInfoReturnable<NaturalSpawner.SpawnState> callbackInfo)
*///?}
            {
        if (callbackInfo.getReturnValue() != null) return;

        AntiCrashController controller = AntiCrashMod.controller();
        if (controller == null || !controller.needsSpawnInfoFallback()) return;

        if (anticrash$emptySpawnInfo == null) {

            //? if (>=1.18 && <=1.21.11) {
            ServerChunkManager manager = (ServerChunkManager) (Object) this;
            //?}
            //? if (>=26.1 && <=26.3) {
            /*ServerChunkCache manager = (ServerChunkCache) (Object) this;
*///?}

            //? if (>=1.18 && <=1.21.11) {
            anticrash$emptySpawnInfo =
                    SpawnHelper.setupSpawn(
                            0,
                            List.<Entity>of(),
                            (chunkPos, consumer) ->
                            //?}
                            //? if (>=26.1 && <=26.2) {
                            /*anticrash$emptySpawnInfo =
                                                NaturalSpawner.createState(
                                                        0,
                                                        List.<Entity>of(),
                                                        (chunkPos, consumer) ->
*///?}
                            //? if 26.3 {
                            /*anticrash$emptySpawnInfo =
                                                NaturalSpawner.createState(
                                                        0,
                                                        level,
                                                        (chunkPos, consumer) ->
*///?}
                            {}
                            //? if (>=1.18 && <=1.20.6) {
                            ,
                            new SpawnDensityCapper(manager.threadedAnvilChunkStorage));
            //?}
            //? if (>=1.21 && <=1.21.11) {
            /*,
                                        new SpawnDensityCapper(manager.chunkLoadingManager));
*///?}
            //? if (>=26.1 && <=26.3) {
            /*,
                                        new LocalMobCapCalculator(manager.chunkMap));
*///?}

        }
        callbackInfo.setReturnValue(anticrash$emptySpawnInfo);
    }

    /**
     * {@code save(boolean)} first processes pending ticket changes, which loads, promotes or
     * demotes chunks. At a checkpoint cut that would change the world after its Runtime Capsule was
     * taken; the changes are applied by the next tick exactly as they would have been.
     */

    //? if (>=1.18 && <=1.20.1) {
    @WrapOperation(
            method = "save",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/server/world/ServerChunkManager;tick()Z"))
    private boolean anticrash$keepTicketsAtCut(
            ServerChunkManager manager, Operation<Boolean> original)
                //?}
                //? if (>=1.20.2 && <=1.21.11) {
                /*@WrapOperation(
                            method = "save",
                            at =
                                    @At(
                                            value = "INVOKE",
                                            target =
                                                    "Lnet/minecraft/server/world/ServerChunkManager;updateChunks()Z"))
                    private boolean anticrash$keepTicketsAtCut(
                            ServerChunkManager manager, Operation<Boolean> original)
*///?}
                //? if (>=26.1 && <=26.3) {
                /*@WrapOperation(
                            method = "save",
                            at =
                                    @At(
                                            value = "INVOKE",
                                            target =
                                                    "Lnet/minecraft/server/level/ServerChunkCache;runDistanceManagerUpdates()Z"))
                    private boolean anticrash$keepTicketsAtCut(
                            ServerChunkCache manager, Operation<Boolean> original)
*///?}
            {
        if (CutSave.active()) return false;
        return original.call(manager);
    }
}
