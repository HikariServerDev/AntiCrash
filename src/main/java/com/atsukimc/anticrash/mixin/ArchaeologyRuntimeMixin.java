//? if (>=1.19.4 && <=26.3) {
package com.atsukimc.anticrash.mixin;

import com.atsukimc.anticrash.runtime.RuntimeExtensionNbt;
import com.atsukimc.anticrash.runtime.VersionNbt;

//? if <=1.19.4 {
/*import net.minecraft.world.level.block.entity.SuspiciousSandBlockEntity;
*///?} else {
import net.minecraft.world.level.block.entity.BrushableBlockEntity;
//?}

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 1.19.4 preview archaeology omits brushing progress, cooldowns and direction from saveAdditional. */
//? if 1.19.4 {
/*@Mixin(SuspiciousSandBlockEntity.class)
public abstract class ArchaeologyRuntimeMixin {

*///?} else {
@Mixin(BrushableBlockEntity.class)
public abstract class ArchaeologyRuntimeMixin {
//?}
    @Shadow private int brushCount;

    @Shadow private long brushCountResetsAtTick;

    @Shadow private long coolDownEndsAtTick;

    @Shadow private Direction hitDirection;

    //? if (>=1.19.4 && <=1.20.4) {
    /*@Inject(method = "saveAdditional", at = @At("TAIL"))
    private void anticrash$saveBrush(CompoundTag nbt, CallbackInfo ci)
    *///?} else if <= 1.21.5 {
    /*@Inject(method = "saveAdditional", at = @At("TAIL"))
    private void anticrash$saveBrush(
            CompoundTag nbt, net.minecraft.core.HolderLookup.Provider registries, CallbackInfo ci)
    *///?} else {
    @Inject(method = "saveAdditional", at = @At("TAIL"))
    private void anticrash$saveBrush(
            net.minecraft.world.level.storage.ValueOutput nbt, CallbackInfo ci)
    //?}
            {
        if (!RuntimeExtensionNbt.savingLoadedChunk()) return;
        RuntimeExtensionNbt.write(
                nbt,
                extension -> {
                    extension.putInt("BrushCount", brushCount);
                    extension.putLong("BrushNextDust", brushCountResetsAtTick);
                    extension.putLong("BrushNextUse", coolDownEndsAtTick);
                    extension.putInt(
                            "BrushDirection",
                            hitDirection == null ? -1 : hitDirection.get3DDataValue());
                });
    }

    //? if (>=1.19.4 && <=1.20.4) {
    /*@Inject(method = "load", at = @At("TAIL"))
    private void anticrash$loadBrush(CompoundTag nbt, CallbackInfo ci)
    *///?} else if <= 1.21.5 {
    /*@Inject(method = "loadAdditional", at = @At("TAIL"))
    private void anticrash$loadBrush(
            CompoundTag nbt, net.minecraft.core.HolderLookup.Provider registries, CallbackInfo ci)
    *///?} else {
    @Inject(method = "loadAdditional", at = @At("TAIL"))
    private void anticrash$loadBrush(
            net.minecraft.world.level.storage.ValueInput nbt, CallbackInfo ci)
    //?}
            {
        CompoundTag extension = RuntimeExtensionNbt.read(nbt);

        //? if (>=1.19.4 && <=1.21.4) {
        /*if (extension == null || !VersionNbt.contains(extension, "BrushCount", 3)) return;
        brushCount = VersionNbt.getInt(extension, "BrushCount");
        brushCountResetsAtTick = VersionNbt.getLong(extension, "BrushNextDust");
        coolDownEndsAtTick = VersionNbt.getLong(extension, "BrushNextUse");
        int direction = VersionNbt.getInt(extension, "BrushDirection");
        *///?} else {
        if (extension == null || !VersionNbt.contains(extension, "BrushCount", 3)) return;
        brushCount = VersionNbt.getInt(extension, "BrushCount");
        brushCountResetsAtTick = VersionNbt.getLong(extension, "BrushNextDust");
        coolDownEndsAtTick = VersionNbt.getLong(extension, "BrushNextUse");
        int direction = VersionNbt.getInt(extension, "BrushDirection");
        //?}

        if (direction < -1 || direction > 5)
            throw new IllegalStateException("Invalid saved brush direction");

        hitDirection = direction == -1 ? null : Direction.from3DDataValue(direction);
    }

    @Inject(method = "checkReset", at = @At("HEAD"))
    private void anticrash$dirtyDecay(CallbackInfo ci) {

        //? if <=1.19.4 {
        /*((SuspiciousSandBlockEntity) (Object) this).setChanged();
        *///?} else {
        ((BrushableBlockEntity) (Object) this).setChanged();
        //?}
    }

    @Inject(method = "brush", at = @At("RETURN"))
    private void anticrash$dirtyBrush(CallbackInfoReturnable<Boolean> ci) {

        //? if <=1.19.4 {
        /*((SuspiciousSandBlockEntity) (Object) this).setChanged();
        *///?} else {
        ((BrushableBlockEntity) (Object) this).setChanged();
        //?}
    }
}
//?}
