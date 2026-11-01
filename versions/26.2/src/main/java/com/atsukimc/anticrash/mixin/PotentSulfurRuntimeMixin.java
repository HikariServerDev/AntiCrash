package com.atsukimc.anticrash.mixin;

import com.atsukimc.anticrash.runtime.RuntimeExtensionNbt;
import com.atsukimc.anticrash.runtime.SulfurRuntimeFrame;
import net.minecraft.world.level.block.entity.PotentSulfurBlockEntity;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PotentSulfurBlockEntity.class)
public abstract class PotentSulfurRuntimeMixin {
    @Shadow public int waitingCountdown;
    @Shadow public long eruptionTick;

    @Inject(method = "saveAdditional", at = @At("TAIL"))
    private void anticrash$savePhase(ValueOutput output, CallbackInfo ci) {
        if (!RuntimeExtensionNbt.savingLoadedChunk()) return;
        RuntimeExtensionNbt.write(output, extension -> extension.put("SulfurFrame",
                new SulfurRuntimeFrame(waitingCountdown, eruptionTick).write()));
    }

    @Inject(method = "loadAdditional", at = @At("TAIL"))
    private void anticrash$restorePhase(ValueInput input, CallbackInfo ci) {
        var extension = RuntimeExtensionNbt.read(input);
        // Ordinary Vanilla data/upgrades have no extension. Never synthesize checkpoint state.
        if (extension == null || !extension.contains("SulfurFrame")) return;
        var frame = SulfurRuntimeFrame.read(extension.getCompound("SulfurFrame").orElseThrow());
        waitingCountdown = frame.countdown();
        eruptionTick = frame.eruptionTick();
    }
}
