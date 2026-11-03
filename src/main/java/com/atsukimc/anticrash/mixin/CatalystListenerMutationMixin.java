//? if (>=1.20 && <=26.3) {
/*package com.atsukimc.anticrash.mixin;

//? if (>=1.20 && <=1.21.11) {
/^import net.minecraft.block.entity.SculkCatalystBlockEntity;
^///?}
//? if (>=26.1 && <=26.3) {
/^import net.minecraft.core.BlockPos;
^///?}

//? if (>=1.20 && <=1.21.11) {
/^import net.minecraft.server.world.ServerWorld;
^///?}
//? if (>=26.1 && <=26.3) {
/^import net.minecraft.server.level.ServerLevel;
^///?}

//? if (>=1.20 && <=1.21.11) {
/^import net.minecraft.util.math.BlockPos;
^///?}
//? if (>=26.1 && <=26.3) {
/^import net.minecraft.world.level.block.entity.SculkCatalystBlockEntity;
^///?}

//? if (>=1.20 && <=1.21.11) {
/^import net.minecraft.util.math.Vec3d;
^///?}
//? if (>=26.1 && <=26.3) {
/^import net.minecraft.world.level.gameevent.GameEvent;
^///?}

//? if (>=1.20 && <=1.21.11) {
/^import net.minecraft.world.event.GameEvent;
^///?}
//? if (>=26.1 && <=26.3) {
/^import net.minecraft.world.level.gameevent.PositionSource;
^///?}

//? if (>=1.20 && <=1.21.11) {
/^import net.minecraft.world.event.PositionSource;
^///?}
//? if (>=26.1 && <=26.3) {
/^import net.minecraft.world.phys.Vec3;
^///?}


import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

//? if (>=1.20 && <=1.21.11) {
/^@Mixin(SculkCatalystBlockEntity.Listener.class)
public abstract class CatalystListenerMutationMixin {
    @Shadow
    public abstract PositionSource getPositionSource();
^///?}
//? if (>=26.1 && <=26.3) {
/^@Mixin(SculkCatalystBlockEntity.CatalystListener.class)
public abstract class CatalystListenerMutationMixin {
    @Shadow
    public abstract PositionSource getListenerSource();
^///?}



//? if (>=1.20 && <=1.20.4) {
/^@Inject(method = "listen", at = @At("RETURN"))
    private void anticrash$dirtyCursor(
            ServerWorld world,
            GameEvent event,
            GameEvent.Emitter emitter,
            Vec3d origin,
            CallbackInfoReturnable<Boolean> ci)
^///?}
//? if (>=1.20.5 && <=1.21.11) {
/^@Inject(method = "listen", at = @At("RETURN"))
    private void anticrash$dirtyCursor(
            ServerWorld world,
            net.minecraft.registry.entry.RegistryEntry<GameEvent> event,
            GameEvent.Emitter emitter,
            Vec3d origin,
            CallbackInfoReturnable<Boolean> ci)
^///?}
//? if (>=26.1 && <=26.3) {
/^@Inject(method = "handleGameEvent", at = @At("RETURN"))
    private void anticrash$dirtyCursor(
            ServerLevel world,
            net.minecraft.core.Holder<GameEvent> event,
            GameEvent.Context emitter,
            Vec3 origin,
            CallbackInfoReturnable<Boolean> ci)
^///?}
{
        if (ci.getReturnValueZ())

//? if (>=1.20 && <=1.21.11) {
/^getPositionSource()
                    .getPos(world)
                    .ifPresent(pos -> world.markDirty(BlockPos.ofFloored(pos)));
^///?}
//? if (>=26.1 && <=26.3) {
/^getListenerSource()
                    .getPosition(world)
                    .ifPresent(pos -> world.blockEntityChanged(BlockPos.containing(pos)));
^///?}

    }
}
*///?}
