//? if (>=1.19.4 && <=26.3) {
/*package com.atsukimc.anticrash.mixin;

import com.atsukimc.anticrash.runtime.RuntimeExtensionNbt;

//? if 1.19.4 {
/^import net.minecraft.world.level.block.entity.SuspiciousSandBlockEntity;
^///?}
//? if (>=1.20 && <=1.21.11) {
/^import net.minecraft.world.level.block.entity.BrushableBlockEntity;
^///?}
//? if (>=26.1 && <=26.3) {
/^import net.minecraft.core.Direction;
^///?}

//? if (>=1.19.4 && <=1.21.11) {
/^import net.minecraft.nbt.CompoundTag;
^///?}
//? if (>=26.1 && <=26.3) {
/^import net.minecraft.nbt.CompoundTag;
^///?}

//? if (>=1.19.4 && <=1.21.11) {
/^import net.minecraft.core.Direction;
^///?}
//? if (>=26.1 && <=26.3) {
/^import net.minecraft.world.level.block.entity.BrushableBlockEntity;
^///?}


import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/^* 1.19.4 preview archaeology omits brushing progress, cooldowns and direction from saveAdditional. ^/
//? if 1.19.4 {
/^@Mixin(SuspiciousSandBlockEntity.class)
public abstract class ArchaeologyRuntimeMixin {

^///?}
//? if (>=1.20 && <=26.3) {
/^@Mixin(BrushableBlockEntity.class)
public abstract class ArchaeologyRuntimeMixin {

^///?}
//? if (>=1.19.4 && <=1.21.11) {
/^@Shadow private int brushCount;
^///?}
//? if (>=26.1 && <=26.3) {
/^@Shadow private int brushCount;
^///?}


//? if (>=1.19.4 && <=1.21.11) {
/^@Shadow private long brushCountResetsAtTick;
^///?}
//? if (>=26.1 && <=26.3) {
/^@Shadow private long brushCountResetsAtTick;
^///?}


//? if (>=1.19.4 && <=1.21.11) {
/^@Shadow private long coolDownEndsAtTick;
^///?}
//? if (>=26.1 && <=26.3) {
/^@Shadow private long coolDownEndsAtTick;
^///?}

    @Shadow private Direction hitDirection;


//? if (>=1.19.4 && <=1.20.4) {
/^@Inject(method = "saveAdditional", at = @At("TAIL"))
    private void anticrash$saveBrush(CompoundTag nbt, CallbackInfo ci)
^///?}
//? if (>=1.20.5 && <=1.21.5) {
/^@Inject(method = "saveAdditional", at = @At("TAIL"))
    private void anticrash$saveBrush(
            CompoundTag nbt,
            net.minecraft.core.HolderLookup.Provider registries,
            CallbackInfo ci)
^///?}
//? if (>=1.21.6 && <=1.21.11) {
/^@Inject(method = "saveAdditional", at = @At("TAIL"))
    private void anticrash$saveBrush(net.minecraft.world.level.storage.ValueOutput nbt, CallbackInfo ci)
^///?}
//? if (>=26.1 && <=26.3) {
/^@Inject(method = "saveAdditional", at = @At("TAIL"))
    private void anticrash$saveBrush(
            net.minecraft.world.level.storage.ValueOutput nbt, CallbackInfo ci)
^///?}
{
        if (!RuntimeExtensionNbt.savingLoadedChunk()) return;
        RuntimeExtensionNbt.write(
                nbt,
                extension -> {

//? if (>=1.19.4 && <=1.21.11) {
/^extension.putInt("BrushCount", brushCount);
^///?}
//? if (>=26.1 && <=26.3) {
/^extension.putInt("BrushCount", brushCount);
^///?}


//? if (>=1.19.4 && <=1.21.11) {
/^extension.putLong("BrushNextDust", brushCountResetsAtTick);
^///?}
//? if (>=26.1 && <=26.3) {
/^extension.putLong("BrushNextDust", brushCountResetsAtTick);
^///?}


//? if (>=1.19.4 && <=1.21.11) {
/^extension.putLong("BrushNextUse", coolDownEndsAtTick);
^///?}
//? if (>=26.1 && <=26.3) {
/^extension.putLong("BrushNextUse", coolDownEndsAtTick);
^///?}


//? if (>=1.19.4 && <=1.21.4) {
/^extension.putInt(
                            "BrushDirection", hitDirection == null ? -1 : hitDirection.get3DDataValue());
^///?}
//? if (>=1.21.5 && <=1.21.11) {
/^extension.putInt(
                            "BrushDirection", hitDirection == null ? -1 : hitDirection.get3DDataValue());
^///?}
//? if (>=26.1 && <=26.3) {
/^extension.putInt(
                            "BrushDirection",
                            hitDirection == null ? -1 : hitDirection.get3DDataValue());
^///?}

                });
    }


//? if (>=1.19.4 && <=1.20.4) {
/^@Inject(method = "load", at = @At("TAIL"))
    private void anticrash$loadBrush(CompoundTag nbt, CallbackInfo ci)
^///?}
//? if (>=1.20.5 && <=1.21.5) {
/^@Inject(method = "loadAdditional", at = @At("TAIL"))
    private void anticrash$loadBrush(
            CompoundTag nbt,
            net.minecraft.core.HolderLookup.Provider registries,
            CallbackInfo ci)
^///?}
//? if (>=1.21.6 && <=1.21.11) {
/^@Inject(method = "loadAdditional", at = @At("TAIL"))
    private void anticrash$loadBrush(net.minecraft.world.level.storage.ValueInput nbt, CallbackInfo ci)
^///?}
//? if (>=26.1 && <=26.3) {
/^@Inject(method = "loadAdditional", at = @At("TAIL"))
    private void anticrash$loadBrush(
            net.minecraft.world.level.storage.ValueInput nbt, CallbackInfo ci)
^///?}
{

//? if (>=1.19.4 && <=1.21.11) {
/^CompoundTag extension = RuntimeExtensionNbt.read(nbt);
^///?}
//? if (>=26.1 && <=26.3) {
/^CompoundTag extension = RuntimeExtensionNbt.read(nbt);
^///?}


//? if (>=1.19.4 && <=1.21.4) {
/^if (extension == null || !extension.contains("BrushCount", 3))
^///?}
//? if (>=1.21.5 && <=26.3) {
/^if (extension == null
                || !com.atsukimc.anticrash.runtime.VersionNbt.contains(extension, "BrushCount", 3))

^///?}
return;

//? if (>=1.19.4 && <=1.21.4) {
/^brushCount = extension.getInt("BrushCount");
^///?}
//? if (>=1.21.5 && <=1.21.11) {
/^brushCount = extension.getInt("BrushCount").orElseThrow();
^///?}
//? if (>=26.1 && <=26.3) {
/^brushCount = extension.getInt("BrushCount").orElseThrow();
^///?}


//? if (>=1.19.4 && <=1.21.4) {
/^brushCountResetsAtTick = extension.getLong("BrushNextDust");
^///?}
//? if (>=1.21.5 && <=1.21.11) {
/^brushCountResetsAtTick = extension.getLong("BrushNextDust").orElseThrow();
^///?}
//? if (>=26.1 && <=26.3) {
/^brushCountResetsAtTick = extension.getLong("BrushNextDust").orElseThrow();
^///?}


//? if (>=1.19.4 && <=1.21.4) {
/^coolDownEndsAtTick = extension.getLong("BrushNextUse");
^///?}
//? if (>=1.21.5 && <=1.21.11) {
/^coolDownEndsAtTick = extension.getLong("BrushNextUse").orElseThrow();
^///?}
//? if (>=26.1 && <=26.3) {
/^coolDownEndsAtTick = extension.getLong("BrushNextUse").orElseThrow();
^///?}


//? if (>=1.19.4 && <=1.21.4) {
/^int direction = extension.getInt("BrushDirection");
^///?}
//? if (>=1.21.5 && <=26.3) {
/^int direction = extension.getInt("BrushDirection").orElseThrow();
^///?}

        if (direction < -1 || direction > 5)
            throw new IllegalStateException("Invalid saved brush direction");

//? if (>=1.19.4 && <=1.21.4) {
/^hitDirection = direction == -1 ? null : Direction.from3DDataValue(direction);
^///?}
//? if (>=1.21.5 && <=1.21.11) {
/^hitDirection = direction == -1 ? null : Direction.from3DDataValue(direction);
^///?}
//? if (>=26.1 && <=26.3) {
/^hitDirection = direction == -1 ? null : Direction.from3DDataValue(direction);
^///?}

    }


//? if (>=1.19.4 && <=1.21.11) {
/^@Inject(method = "checkReset", at = @At("HEAD"))
    private void anticrash$dirtyDecay(CallbackInfo ci)
^///?}
//? if (>=26.1 && <=26.3) {
/^@Inject(method = "checkReset", at = @At("HEAD"))
    private void anticrash$dirtyDecay(CallbackInfo ci)
^///?}
{

//? if 1.19.4 {
/^((SuspiciousSandBlockEntity) (Object) this).setChanged();
^///?}
//? if (>=1.20 && <=1.21.11) {
/^((BrushableBlockEntity) (Object) this).setChanged();
^///?}
//? if (>=26.1 && <=26.3) {
/^((BrushableBlockEntity) (Object) this).setChanged();
^///?}

    }

    @Inject(method = "brush", at = @At("RETURN"))
    private void anticrash$dirtyBrush(CallbackInfoReturnable<Boolean> ci) {

//? if 1.19.4 {
/^((SuspiciousSandBlockEntity) (Object) this).setChanged();
^///?}
//? if (>=1.20 && <=1.21.11) {
/^((BrushableBlockEntity) (Object) this).setChanged();
^///?}
//? if (>=26.1 && <=26.3) {
/^((BrushableBlockEntity) (Object) this).setChanged();
^///?}

    }
}
*///?}
