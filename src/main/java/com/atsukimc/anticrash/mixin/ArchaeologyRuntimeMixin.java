//? if (>=1.19.4 && <=26.3) {
/*package com.atsukimc.anticrash.mixin;

import com.atsukimc.anticrash.runtime.RuntimeExtensionNbt;

//? if 1.19.4 {
/^import net.minecraft.block.entity.SuspiciousSandBlockEntity;
^///?}
//? if (>=1.20 && <=1.21.11) {
/^import net.minecraft.block.entity.BrushableBlockEntity;
^///?}
//? if (>=26.1 && <=26.3) {
/^import net.minecraft.core.Direction;
^///?}

//? if (>=1.19.4 && <=1.21.11) {
/^import net.minecraft.nbt.NbtCompound;
^///?}
//? if (>=26.1 && <=26.3) {
/^import net.minecraft.nbt.CompoundTag;
^///?}

//? if (>=1.19.4 && <=1.21.11) {
/^import net.minecraft.util.math.Direction;
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

/^* 1.19.4 preview archaeology omits brushing progress, cooldowns and direction from writeNbt. ^/
//? if 1.19.4 {
/^@Mixin(SuspiciousSandBlockEntity.class)
public abstract class ArchaeologyRuntimeMixin {

^///?}
//? if (>=1.20 && <=26.3) {
/^@Mixin(BrushableBlockEntity.class)
public abstract class ArchaeologyRuntimeMixin {

^///?}
//? if (>=1.19.4 && <=1.21.11) {
/^@Shadow private int brushesCount;
^///?}
//? if (>=26.1 && <=26.3) {
/^@Shadow private int brushCount;
^///?}


//? if (>=1.19.4 && <=1.21.11) {
/^@Shadow private long nextDustTime;
^///?}
//? if (>=26.1 && <=26.3) {
/^@Shadow private long brushCountResetsAtTick;
^///?}


//? if (>=1.19.4 && <=1.21.11) {
/^@Shadow private long nextBrushTime;
^///?}
//? if (>=26.1 && <=26.3) {
/^@Shadow private long coolDownEndsAtTick;
^///?}

    @Shadow private Direction hitDirection;


//? if (>=1.19.4 && <=1.20.4) {
/^@Inject(method = "writeNbt", at = @At("TAIL"))
    private void anticrash$saveBrush(NbtCompound nbt, CallbackInfo ci)
^///?}
//? if (>=1.20.5 && <=1.21.5) {
/^@Inject(method = "writeNbt", at = @At("TAIL"))
    private void anticrash$saveBrush(
            NbtCompound nbt,
            net.minecraft.registry.RegistryWrapper.WrapperLookup registries,
            CallbackInfo ci)
^///?}
//? if (>=1.21.6 && <=1.21.11) {
/^@Inject(method = "writeData", at = @At("TAIL"))
    private void anticrash$saveBrush(net.minecraft.storage.WriteView nbt, CallbackInfo ci)
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
/^extension.putInt("BrushCount", brushesCount);
^///?}
//? if (>=26.1 && <=26.3) {
/^extension.putInt("BrushCount", brushCount);
^///?}


//? if (>=1.19.4 && <=1.21.11) {
/^extension.putLong("BrushNextDust", nextDustTime);
^///?}
//? if (>=26.1 && <=26.3) {
/^extension.putLong("BrushNextDust", brushCountResetsAtTick);
^///?}


//? if (>=1.19.4 && <=1.21.11) {
/^extension.putLong("BrushNextUse", nextBrushTime);
^///?}
//? if (>=26.1 && <=26.3) {
/^extension.putLong("BrushNextUse", coolDownEndsAtTick);
^///?}


//? if (>=1.19.4 && <=1.21.4) {
/^extension.putInt(
                            "BrushDirection", hitDirection == null ? -1 : hitDirection.getId());
^///?}
//? if (>=1.21.5 && <=1.21.11) {
/^extension.putInt(
                            "BrushDirection", hitDirection == null ? -1 : hitDirection.getIndex());
^///?}
//? if (>=26.1 && <=26.3) {
/^extension.putInt(
                            "BrushDirection",
                            hitDirection == null ? -1 : hitDirection.get3DDataValue());
^///?}

                });
    }


//? if (>=1.19.4 && <=1.20.4) {
/^@Inject(method = "readNbt", at = @At("TAIL"))
    private void anticrash$loadBrush(NbtCompound nbt, CallbackInfo ci)
^///?}
//? if (>=1.20.5 && <=1.21.5) {
/^@Inject(method = "readNbt", at = @At("TAIL"))
    private void anticrash$loadBrush(
            NbtCompound nbt,
            net.minecraft.registry.RegistryWrapper.WrapperLookup registries,
            CallbackInfo ci)
^///?}
//? if (>=1.21.6 && <=1.21.11) {
/^@Inject(method = "readData", at = @At("TAIL"))
    private void anticrash$loadBrush(net.minecraft.storage.ReadView nbt, CallbackInfo ci)
^///?}
//? if (>=26.1 && <=26.3) {
/^@Inject(method = "loadAdditional", at = @At("TAIL"))
    private void anticrash$loadBrush(
            net.minecraft.world.level.storage.ValueInput nbt, CallbackInfo ci)
^///?}
{

//? if (>=1.19.4 && <=1.21.11) {
/^NbtCompound extension = RuntimeExtensionNbt.read(nbt);
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
/^brushesCount = extension.getInt("BrushCount");
^///?}
//? if (>=1.21.5 && <=1.21.11) {
/^brushesCount = extension.getInt("BrushCount").orElseThrow();
^///?}
//? if (>=26.1 && <=26.3) {
/^brushCount = extension.getInt("BrushCount").orElseThrow();
^///?}


//? if (>=1.19.4 && <=1.21.4) {
/^nextDustTime = extension.getLong("BrushNextDust");
^///?}
//? if (>=1.21.5 && <=1.21.11) {
/^nextDustTime = extension.getLong("BrushNextDust").orElseThrow();
^///?}
//? if (>=26.1 && <=26.3) {
/^brushCountResetsAtTick = extension.getLong("BrushNextDust").orElseThrow();
^///?}


//? if (>=1.19.4 && <=1.21.4) {
/^nextBrushTime = extension.getLong("BrushNextUse");
^///?}
//? if (>=1.21.5 && <=1.21.11) {
/^nextBrushTime = extension.getLong("BrushNextUse").orElseThrow();
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
/^hitDirection = direction == -1 ? null : Direction.byId(direction);
^///?}
//? if (>=1.21.5 && <=1.21.11) {
/^hitDirection = direction == -1 ? null : Direction.byIndex(direction);
^///?}
//? if (>=26.1 && <=26.3) {
/^hitDirection = direction == -1 ? null : Direction.from3DDataValue(direction);
^///?}

    }


//? if (>=1.19.4 && <=1.21.11) {
/^@Inject(method = "scheduledTick", at = @At("HEAD"))
    private void anticrash$dirtyDecay(CallbackInfo ci)
^///?}
//? if (>=26.1 && <=26.3) {
/^@Inject(method = "checkReset", at = @At("HEAD"))
    private void anticrash$dirtyDecay(CallbackInfo ci)
^///?}
{

//? if 1.19.4 {
/^((SuspiciousSandBlockEntity) (Object) this).markDirty();
^///?}
//? if (>=1.20 && <=1.21.11) {
/^((BrushableBlockEntity) (Object) this).markDirty();
^///?}
//? if (>=26.1 && <=26.3) {
/^((BrushableBlockEntity) (Object) this).setChanged();
^///?}

    }

    @Inject(method = "brush", at = @At("RETURN"))
    private void anticrash$dirtyBrush(CallbackInfoReturnable<Boolean> ci) {

//? if 1.19.4 {
/^((SuspiciousSandBlockEntity) (Object) this).markDirty();
^///?}
//? if (>=1.20 && <=1.21.11) {
/^((BrushableBlockEntity) (Object) this).markDirty();
^///?}
//? if (>=26.1 && <=26.3) {
/^((BrushableBlockEntity) (Object) this).setChanged();
^///?}

    }
}
*///?}
