package com.atsukimc.anticrash.mixin;

import com.atsukimc.anticrash.runtime.RuntimeExtensionNbt;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BrushableBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 1.19.4 preview archaeology omits brushing progress, cooldowns and direction from writeNbt. */
@Mixin(BrushableBlockEntity.class)
public abstract class ArchaeologyRuntimeMixin {
    @Shadow private int brushCount;
    @Shadow private long brushCountResetsAtTick;
    @Shadow private long coolDownEndsAtTick;
    @Shadow private Direction hitDirection;
    @Inject(method = "saveAdditional", at = @At("TAIL"))
    private void anticrash$saveBrush(net.minecraft.world.level.storage.ValueOutput nbt, CallbackInfo ci) {
        if (!RuntimeExtensionNbt.savingLoadedChunk()) return;
        RuntimeExtensionNbt.write(nbt, extension -> {
            extension.putInt("BrushCount", brushCount);
            extension.putLong("BrushNextDust", brushCountResetsAtTick);
            extension.putLong("BrushNextUse", coolDownEndsAtTick);
            extension.putInt("BrushDirection", hitDirection == null ? -1 : hitDirection.get3DDataValue());
        });
    }
    @Inject(method = "loadAdditional", at = @At("TAIL"))
    private void anticrash$loadBrush(net.minecraft.world.level.storage.ValueInput nbt, CallbackInfo ci) {
        CompoundTag extension = RuntimeExtensionNbt.read(nbt);
        if (extension == null || !com.atsukimc.anticrash.runtime.VersionNbt.contains(extension, "BrushCount", 3)) return;
        brushCount = extension.getInt("BrushCount").orElseThrow();
        brushCountResetsAtTick = extension.getLong("BrushNextDust").orElseThrow();
        coolDownEndsAtTick = extension.getLong("BrushNextUse").orElseThrow();
        int direction = extension.getInt("BrushDirection").orElseThrow();
        if (direction < -1 || direction > 5) throw new IllegalStateException("Invalid saved brush direction");
        hitDirection = direction == -1 ? null : Direction.from3DDataValue(direction);
    }
    @Inject(method = "checkReset", at = @At("HEAD"))
    private void anticrash$dirtyDecay(CallbackInfo ci) {
        ((BrushableBlockEntity)(Object)this).setChanged();
    }
    @Inject(method = "brush", at = @At("RETURN"))
    private void anticrash$dirtyBrush(CallbackInfoReturnable<Boolean> ci) {
        ((BrushableBlockEntity)(Object)this).setChanged();
    }
}
