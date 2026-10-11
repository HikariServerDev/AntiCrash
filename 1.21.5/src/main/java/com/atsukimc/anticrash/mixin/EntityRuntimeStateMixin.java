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
 * entities that stay loaded are saved (see {@link RuntimeExtensionNbt#savingLoadedEntities()}) and only
 * when it differs from what a freshly loaded entity would have, so ordinary entity data and the
 * unchanged-entity-chunk detection stay as they were.
 */
@Mixin(Entity.class)
public abstract class EntityRuntimeStateMixin {
    @Shadow public net.minecraft.world.dimension.PortalManager portalManager;
    @Shadow protected int ridingCooldown;
    @Shadow protected boolean firstUpdate;
    @Shadow protected boolean touchingWater;
    @Shadow public int timeUntilRegen;
    @Shadow public int age;
    @Shadow public java.util.Optional<BlockPos> supportingBlockPos;
    @Shadow private boolean forceUpdateSupportingBlockPos;
    @Shadow public boolean groundCollision;

    @Inject(method = "writeNbt", at = @At("RETURN"))
    private void anticrash$saveRuntimeState(NbtCompound nbt, CallbackInfoReturnable<NbtCompound> callbackInfo) {
        if (!RuntimeExtensionNbt.savingLoadedEntities()) return;
        Object self = this;
        RuntimeExtensionNbt.write(nbt, extension -> {
            extension.putBoolean("GroundCollision", groundCollision);
            extension.putBoolean("ForceSupportUpdate", forceUpdateSupportingBlockPos);
            if (supportingBlockPos.isPresent()) extension.put("SupportingBlock", net.minecraft.util.math.BlockPos.CODEC, supportingBlockPos.get());
            // Touched a nether portal this tick: the dimension change happens at the start of the
            // next tick. Without this the entity has to touch the portal again, one tick late.
            if (portalManager != null) extension.put("Portal",
                    com.atsukimc.anticrash.runtime.PortalRuntime.write(portalManager));
            // 60 ticks after leaving a vehicle during which the entity cannot board another.
            if (ridingCooldown > 0) extension.putInt("RidingCooldown", ridingCooldown);
            // A loaded entity is "new" for one tick: Entity#isInLava is false and it does not
            // splash. An entity that had already ticked must not go through that tick again.
            if (!firstUpdate) {
                extension.putBoolean("Ticked", true);
                if (touchingWater) extension.putBoolean("Water", true);
            }
            // Damage immunity window; LivingEntity compares new damage with lastDamageTaken in it.
            if (timeUntilRegen > 0) {
                extension.putInt("Regen", timeUntilRegen);
                if (self instanceof LivingEntity living) {
                    extension.putFloat("LastDamage", ((LivingEntityAccessorMixin) living).anticrash$getLastDamageTaken());
                }
            }
            // Items merge when age % 40 (or % 2) is 0 and resting items re-check movement when
            // (age + id) % 4 is 0; orbs look for a player when age % 20 is 1. Their data already
            // changes every tick (Vanilla's own "Age"), so this adds no extra chunk writes. Other
            // entities are left alone: saving age for all of them would rewrite every entity
            // chunk at every checkpoint.
            if (self instanceof ItemEntity || self instanceof ExperienceOrbEntity) extension.putInt("Age", age);
            if (self instanceof com.atsukimc.anticrash.runtime.ItemPhaseHolder item) extension.putInt("IdPhase", item.anticrash$idPhase());
            if (self instanceof net.minecraft.entity.TntEntity) extension.putBoolean("TeleportedTnt",
                    ((TntRuntimeAccessorMixin) self).anticrash$teleported());
            if (self instanceof net.minecraft.entity.FallingBlockEntity falling)
                extension.putBoolean("ShouldDupe", falling.shouldDupe);
        });
    }

    @Inject(method = "readNbt", at = @At("TAIL"))
    private void anticrash$loadRuntimeState(NbtCompound nbt, CallbackInfo callbackInfo) {
        NbtCompound extension = RuntimeExtensionNbt.read(nbt);
        if (extension == null) return;
        Object self = this;
        if (self instanceof net.minecraft.entity.TntEntity && com.atsukimc.anticrash.runtime.VersionNbt.contains(extension, "TeleportedTnt", 1))
            ((TntRuntimeAccessorMixin) self).anticrash$setTeleported(extension.getBoolean("TeleportedTnt").orElseThrow());
        if (self instanceof net.minecraft.entity.FallingBlockEntity falling && com.atsukimc.anticrash.runtime.VersionNbt.contains(extension, "ShouldDupe", 1))
            falling.shouldDupe = extension.getBoolean("ShouldDupe").orElseThrow();
        if (com.atsukimc.anticrash.runtime.VersionNbt.contains(extension, "ForceSupportUpdate", 1)) {
            forceUpdateSupportingBlockPos = extension.getBoolean("ForceSupportUpdate").orElseThrow();
            groundCollision = extension.getBoolean("GroundCollision").orElseThrow();
            supportingBlockPos = extension.get("SupportingBlock", net.minecraft.util.math.BlockPos.CODEC);
        }
        if (com.atsukimc.anticrash.runtime.VersionNbt.contains(extension, "Portal", 10)) {
            portalManager = com.atsukimc.anticrash.runtime.PortalRuntime.read(extension.getCompoundOrEmpty("Portal"));
        }
        if (com.atsukimc.anticrash.runtime.VersionNbt.contains(extension, "RidingCooldown", 99)) ridingCooldown = Math.max(0, extension.getInt("RidingCooldown").orElseThrow());
        if (com.atsukimc.anticrash.runtime.VersionNbt.sparseFlag(extension,"Ticked")) {
            firstUpdate = false;
            touchingWater = com.atsukimc.anticrash.runtime.VersionNbt.sparseFlag(extension,"Water");
        }
        if (com.atsukimc.anticrash.runtime.VersionNbt.contains(extension, "Regen", 99)) {
            timeUntilRegen = Math.max(0, extension.getInt("Regen").orElseThrow());
            if (self instanceof LivingEntity living && com.atsukimc.anticrash.runtime.VersionNbt.contains(extension, "LastDamage", 99)) {
                ((LivingEntityAccessorMixin) living).anticrash$setLastDamageTaken(extension.getFloat("LastDamage").orElseThrow());
            }
        }
        if ((self instanceof ItemEntity || self instanceof ExperienceOrbEntity) && com.atsukimc.anticrash.runtime.VersionNbt.contains(extension, "Age", 99)) {
            age = Math.max(0, extension.getInt("Age").orElseThrow());
        }
        if (self instanceof com.atsukimc.anticrash.runtime.ItemPhaseHolder item && com.atsukimc.anticrash.runtime.VersionNbt.contains(extension, "IdPhase", 99)) {
            item.anticrash$setIdPhase(extension.getInt("IdPhase").orElseThrow());
        }
    }
}
