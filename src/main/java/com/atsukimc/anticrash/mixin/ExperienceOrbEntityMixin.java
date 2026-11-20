package com.atsukimc.anticrash.mixin;

import com.atsukimc.anticrash.runtime.ItemPhaseHolder;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

import net.minecraft.world.entity.ExperienceOrb;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ExperienceOrb.class)
public abstract class ExperienceOrbEntityMixin implements ItemPhaseHolder {
    /**
     * Two orbs merge only when the difference of their network ids is a multiple of 40. The ids are
     * handed out again at every load, so which orbs of a pile merged changed with each restart. The
     * id's remainder at the time the orb first existed is kept and used for that comparison only;
     * the real network id is never touched.
     */
    @Unique private int anticrash$idPhase = -1;

    @Override
    public int anticrash$idPhase() {
        return anticrash$idPhase >= 0
                ? anticrash$idPhase
                : Math.floorMod(((ExperienceOrb) (Object) this).getId(), 40);
    }

    @Override
    public void anticrash$setIdPhase(int phase) {
        anticrash$idPhase = Math.floorMod(phase, 40);
    }

    @ModifyExpressionValue(
            method = "canMerge(Lnet/minecraft/world/entity/ExperienceOrb;)Z",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/world/entity/ExperienceOrb;getId()I"))
    private int anticrash$stableSeed(int networkId) {
        return anticrash$idPhase >= 0 ? anticrash$idPhase : networkId;
    }

    @ModifyExpressionValue(
            method = "canMerge(Lnet/minecraft/world/entity/ExperienceOrb;II)Z",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/world/entity/ExperienceOrb;getId()I"))
    private static int anticrash$stableCandidate(
            int networkId, ExperienceOrb orb, int seed, int amount) {
        int phase = ((ExperienceOrbEntityMixin) (Object) orb).anticrash$idPhase;
        return phase >= 0 ? phase : networkId;
    }
}
