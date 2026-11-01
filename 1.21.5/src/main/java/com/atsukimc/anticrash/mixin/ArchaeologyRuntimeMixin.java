package com.atsukimc.anticrash.mixin;

import com.atsukimc.anticrash.runtime.RuntimeExtensionNbt;

import net.minecraft.block.entity.BrushableBlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.Direction;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 1.19.4 preview archaeology omits brushing progress, cooldowns and direction from writeNbt. */
@Mixin(BrushableBlockEntity.class)
public abstract class ArchaeologyRuntimeMixin {
    @Shadow private int brushesCount;
    @Shadow private long nextDustTime;
    @Shadow private long nextBrushTime;
    @Shadow private Direction hitDirection;

    @Inject(method = "writeNbt", at = @At("TAIL"))
    private void anticrash$saveBrush(
            NbtCompound nbt,
            net.minecraft.registry.RegistryWrapper.WrapperLookup registries,
            CallbackInfo ci) {
        if (!RuntimeExtensionNbt.savingLoadedChunk()) return;
        RuntimeExtensionNbt.write(
                nbt,
                extension -> {
                    extension.putInt("BrushCount", brushesCount);
                    extension.putLong("BrushNextDust", nextDustTime);
                    extension.putLong("BrushNextUse", nextBrushTime);
                    extension.putInt(
                            "BrushDirection", hitDirection == null ? -1 : hitDirection.getIndex());
                });
    }

    @Inject(method = "readNbt", at = @At("TAIL"))
    private void anticrash$loadBrush(
            NbtCompound nbt,
            net.minecraft.registry.RegistryWrapper.WrapperLookup registries,
            CallbackInfo ci) {
        NbtCompound extension = RuntimeExtensionNbt.read(nbt);
        if (extension == null
                || !com.atsukimc.anticrash.runtime.VersionNbt.contains(extension, "BrushCount", 3))
            return;
        brushesCount = extension.getInt("BrushCount").orElseThrow();
        nextDustTime = extension.getLong("BrushNextDust").orElseThrow();
        nextBrushTime = extension.getLong("BrushNextUse").orElseThrow();
        int direction = extension.getInt("BrushDirection").orElseThrow();
        if (direction < -1 || direction > 5)
            throw new IllegalStateException("Invalid saved brush direction");
        hitDirection = direction == -1 ? null : Direction.byIndex(direction);
    }

    @Inject(method = "scheduledTick", at = @At("HEAD"))
    private void anticrash$dirtyDecay(CallbackInfo ci) {
        ((BrushableBlockEntity) (Object) this).markDirty();
    }

    @Inject(method = "brush", at = @At("RETURN"))
    private void anticrash$dirtyBrush(CallbackInfoReturnable<Boolean> ci) {
        ((BrushableBlockEntity) (Object) this).markDirty();
    }
}
