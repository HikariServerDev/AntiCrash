package com.atsukimc.anticrash.mixin;

import com.atsukimc.anticrash.runtime.RuntimeExtensionNbt;

import net.minecraft.entity.Entity;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.util.math.BlockPos;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Entity state Vanilla does not save although the next tick depends on it. Written only while
 * entities that stay loaded are saved (see {@link RuntimeExtensionNbt#savingLoadedEntities()}) and
 * only when it differs from what a freshly loaded entity would have, so ordinary entity data and
 * the unchanged-entity-chunk detection stay as they were.
 */
@Mixin(Entity.class)
public abstract class EntityRuntimeStateMixin {
    @Shadow protected boolean inNetherPortal;
    @Shadow protected int netherPortalTime;
    @Shadow protected BlockPos lastNetherPortalPosition;
    @Shadow protected int ridingCooldown;
    @Shadow protected boolean firstUpdate;
    @Shadow protected boolean touchingWater;
    @Shadow public int timeUntilRegen;
    @Shadow public int age;

    @Inject(method = "writeNbt", at = @At("RETURN"))
    private void anticrash$saveRuntimeState(
            NbtCompound nbt, CallbackInfoReturnable<NbtCompound> callbackInfo) {
        if (!RuntimeExtensionNbt.savingLoadedEntities()) return;
        Object self = this;
        RuntimeExtensionNbt.write(
                nbt,
                extension -> {
                    // Touched a nether portal this tick: the dimension change happens at the start
                    // of the
                    // next tick. Without this the entity has to touch the portal again, one tick
                    // late.
                    if (inNetherPortal || netherPortalTime > 0) {
                        NbtCompound portal = new NbtCompound();
                        portal.putBoolean("In", inNetherPortal);
                        portal.putInt("Time", netherPortalTime);
                        if (lastNetherPortalPosition != null)
                            portal.put("Pos", NbtHelper.fromBlockPos(lastNetherPortalPosition));
                        extension.put("Portal", portal);
                    }
                    // 60 ticks after leaving a vehicle during which the entity cannot board
                    // another.
                    if (ridingCooldown > 0) extension.putInt("RidingCooldown", ridingCooldown);
                    // A loaded entity is "new" for one tick: Entity#isInLava is false and it does
                    // not
                    // splash. An entity that had already ticked must not go through that tick
                    // again.
                    if (!firstUpdate) {
                        extension.putBoolean("Ticked", true);
                        if (touchingWater) extension.putBoolean("Water", true);
                    }
                    // Damage immunity window; LivingEntity compares new damage with lastDamageTaken
                    // in it.
                    if (timeUntilRegen > 0) {
                        extension.putInt("Regen", timeUntilRegen);
                        if (self instanceof LivingEntity living) {
                            extension.putFloat(
                                    "LastDamage",
                                    ((LivingEntityAccessorMixin) living)
                                            .anticrash$getLastDamageTaken());
                        }
                    }
                    // Items merge when age % 40 (or % 2) is 0 and resting items re-check movement
                    // when
                    // (age + id) % 4 is 0; orbs look for a player when age % 20 is 1. Their data
                    // already
                    // changes every tick (Vanilla's own "Age"), so this adds no extra chunk writes.
                    // Other
                    // entities are left alone: saving age for all of them would rewrite every
                    // entity
                    // chunk at every checkpoint.
                    if (self instanceof ItemEntity || self instanceof ExperienceOrbEntity)
                        extension.putInt("Age", age);
                    if (self instanceof com.atsukimc.anticrash.runtime.ItemPhaseHolder item)
                        extension.putInt("IdPhase", item.anticrash$idPhase());
                });
    }

    @Inject(method = "readNbt", at = @At("TAIL"))
    private void anticrash$loadRuntimeState(NbtCompound nbt, CallbackInfo callbackInfo) {
        NbtCompound extension = RuntimeExtensionNbt.read(nbt);
        if (extension == null) return;
        Object self = this;
        if (extension.contains("Portal", 10)) {
            NbtCompound portal = extension.getCompound("Portal");
            inNetherPortal = portal.getBoolean("In");
            netherPortalTime = Math.max(0, portal.getInt("Time"));
            if (portal.contains("Pos", 10))
                lastNetherPortalPosition = NbtHelper.toBlockPos(portal.getCompound("Pos"));
        }
        if (extension.contains("RidingCooldown", 99))
            ridingCooldown = Math.max(0, extension.getInt("RidingCooldown"));
        if (extension.getBoolean("Ticked")) {
            firstUpdate = false;
            touchingWater = extension.getBoolean("Water");
        }
        if (extension.contains("Regen", 99)) {
            timeUntilRegen = Math.max(0, extension.getInt("Regen"));
            if (self instanceof LivingEntity living && extension.contains("LastDamage", 99)) {
                ((LivingEntityAccessorMixin) living)
                        .anticrash$setLastDamageTaken(extension.getFloat("LastDamage"));
            }
        }
        if ((self instanceof ItemEntity || self instanceof ExperienceOrbEntity)
                && extension.contains("Age", 99)) {
            age = Math.max(0, extension.getInt("Age"));
        }
        if (self instanceof com.atsukimc.anticrash.runtime.ItemPhaseHolder item
                && extension.contains("IdPhase", 99)) {
            item.anticrash$setIdPhase(extension.getInt("IdPhase"));
        }
    }
}
