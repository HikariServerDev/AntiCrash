package com.atsukimc.anticrash.mixin;

import com.atsukimc.anticrash.runtime.RuntimeExtensionNbt;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
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
    @Shadow public net.minecraft.world.entity.PortalProcessor portalProcess;
    @Shadow protected int boardingCooldown;
    @Shadow protected boolean firstTick;
    @Shadow protected boolean wasTouchingWater;
    @Shadow public int invulnerableTime;
    @Shadow public int tickCount;
    @Shadow public java.util.Optional<BlockPos> mainSupportingBlockPos;
    @Shadow private boolean onGroundNoBlocks;
    @Shadow public boolean verticalCollisionBelow;

    @Inject(method = "saveWithoutId", at = @At("RETURN"))
    private void anticrash$saveRuntimeState(net.minecraft.world.level.storage.ValueOutput nbt, CallbackInfo callbackInfo) {
        if (!RuntimeExtensionNbt.savingLoadedEntities()) return;
        Object self = this;
        RuntimeExtensionNbt.write(nbt, extension -> {
            extension.put("FluidTrackers",com.atsukimc.anticrash.runtime.FluidInteractionSnapshot.capture((Entity)self));
            extension.put("MotionFrame",com.atsukimc.anticrash.runtime.EntityMotionFrame.capture((Entity)(Object)this).write());
            if(self instanceof LivingEntity)
                extension.putLong("LastKineticAttackTime",((LivingEntityAccessorMixin)self).anticrash$lastKineticAttackTime());
            extension.putBoolean("GroundCollision", verticalCollisionBelow);
            extension.putBoolean("ForceSupportUpdate", onGroundNoBlocks);
            if (mainSupportingBlockPos.isPresent()) extension.store("SupportingBlock", net.minecraft.core.BlockPos.CODEC, mainSupportingBlockPos.get());
            // Touched a nether portal this tick: the dimension change happens at the start of the
            // next tick. Without this the entity has to touch the portal again, one tick late.
            if (portalProcess != null) extension.put("Portal",
                    com.atsukimc.anticrash.runtime.PortalRuntime.write(portalProcess));
            // 60 ticks after leaving a vehicle during which the entity cannot board another.
            if (boardingCooldown > 0) extension.putInt("RidingCooldown", boardingCooldown);
            // A loaded entity is "new" for one tick: Entity#isInLava is false and it does not
            // splash. An entity that had already ticked must not go through that tick again.
            if (!firstTick) {
                extension.putBoolean("Ticked", true);
                if (wasTouchingWater) extension.putBoolean("Water", true);
            }
            // Damage immunity window; LivingEntity compares new damage with lastDamageTaken in it.
            if (invulnerableTime > 0) {
                extension.putInt("Regen", invulnerableTime);
                if (self instanceof LivingEntity living) {
                    extension.putFloat("LastDamage", ((LivingEntityAccessorMixin) living).anticrash$getLastDamageTaken());
                }
            }
            // Items merge when age % 40 (or % 2) is 0 and resting items re-check movement when
            // (age + id) % 4 is 0; orbs look for a player when age % 20 is 1. Their data already
            // changes every tick (Vanilla's own "Age"), so this adds no extra chunk writes. Other
            // entities are left alone: saving age for all of them would rewrite every entity
            // chunk at every checkpoint.
            if (self instanceof ItemEntity || self instanceof ExperienceOrb) extension.putInt("Age", tickCount);
            if (self instanceof com.atsukimc.anticrash.runtime.ItemPhaseHolder item) extension.putInt("IdPhase", item.anticrash$idPhase());
            if (self instanceof net.minecraft.world.entity.item.PrimedTnt) extension.putBoolean("TeleportedTnt",
                    ((TntRuntimeAccessorMixin) self).anticrash$teleported());
            if (self instanceof net.minecraft.world.entity.item.FallingBlockEntity falling)
                extension.putBoolean("ShouldDupe", falling.forceTickAfterTeleportToDuplicate);
            if (self instanceof net.minecraft.world.entity.projectile.Projectile)
                extension.putBoolean("CheckedForLeftOwner", ((ProjectileRuntimeAccessorMixin)self).anticrash$checkedForLeftOwner());
        });
    }

    @Inject(method = "load", at = @At("TAIL"))
    private void anticrash$loadRuntimeState(net.minecraft.world.level.storage.ValueInput nbt, CallbackInfo callbackInfo) {
        CompoundTag extension = RuntimeExtensionNbt.read(nbt);
        if (extension == null) return;
        Object self = this;
        // Old normal-world entity NBT may predate 26.1. Cross-game checkpoint restoration is
        // rejected separately; do not prevent Vanilla's ordinary world upgrade on this basis.
        if(extension.contains("FluidTrackers"))
            com.atsukimc.anticrash.runtime.FluidInteractionSnapshot.restore((Entity)self,
                    com.atsukimc.anticrash.runtime.VersionNbt.list(extension,"FluidTrackers",10));
        if(self instanceof LivingEntity && extension.contains("LastKineticAttackTime"))
            ((LivingEntityAccessorMixin)self).anticrash$setLastKineticAttackTime(extension.getLong("LastKineticAttackTime").orElseThrow());
        if(extension.contains("MotionFrame"))
            com.atsukimc.anticrash.runtime.EntityMotionFrame.read(extension.getCompound("MotionFrame").orElseThrow()).apply((Entity)self);
        if (self instanceof net.minecraft.world.entity.projectile.Projectile)
            ((ProjectileRuntimeAccessorMixin)self).anticrash$setCheckedForLeftOwner(extension.getBoolean("CheckedForLeftOwner").orElseThrow());
        if (self instanceof net.minecraft.world.entity.item.PrimedTnt && com.atsukimc.anticrash.runtime.VersionNbt.contains(extension, "TeleportedTnt", 1))
            ((TntRuntimeAccessorMixin) self).anticrash$setTeleported(extension.getBoolean("TeleportedTnt").orElseThrow());
        if (self instanceof net.minecraft.world.entity.item.FallingBlockEntity falling && com.atsukimc.anticrash.runtime.VersionNbt.contains(extension, "ShouldDupe", 1))
            falling.forceTickAfterTeleportToDuplicate = extension.getBoolean("ShouldDupe").orElseThrow();
        if (com.atsukimc.anticrash.runtime.VersionNbt.contains(extension, "ForceSupportUpdate", 1)) {
            onGroundNoBlocks = extension.getBoolean("ForceSupportUpdate").orElseThrow();
            verticalCollisionBelow = extension.getBoolean("GroundCollision").orElseThrow();
            mainSupportingBlockPos = extension.read("SupportingBlock", net.minecraft.core.BlockPos.CODEC);
        }
        if (com.atsukimc.anticrash.runtime.VersionNbt.contains(extension, "Portal", 10)) {
            portalProcess = com.atsukimc.anticrash.runtime.PortalRuntime.read(extension.getCompoundOrEmpty("Portal"));
        }
        if (com.atsukimc.anticrash.runtime.VersionNbt.contains(extension, "RidingCooldown", 99)) boardingCooldown = Math.max(0, extension.getInt("RidingCooldown").orElseThrow());
        if (com.atsukimc.anticrash.runtime.VersionNbt.sparseFlag(extension,"Ticked")) {
            firstTick = false;
            wasTouchingWater = com.atsukimc.anticrash.runtime.VersionNbt.sparseFlag(extension,"Water");
        }
        if (com.atsukimc.anticrash.runtime.VersionNbt.contains(extension, "Regen", 99)) {
            invulnerableTime = Math.max(0, extension.getInt("Regen").orElseThrow());
            if (self instanceof LivingEntity living && com.atsukimc.anticrash.runtime.VersionNbt.contains(extension, "LastDamage", 99)) {
                ((LivingEntityAccessorMixin) living).anticrash$setLastDamageTaken(extension.getFloat("LastDamage").orElseThrow());
            }
        }
        if ((self instanceof ItemEntity || self instanceof ExperienceOrb) && com.atsukimc.anticrash.runtime.VersionNbt.contains(extension, "Age", 99)) {
            tickCount = Math.max(0, extension.getInt("Age").orElseThrow());
        }
        if (self instanceof com.atsukimc.anticrash.runtime.ItemPhaseHolder item && com.atsukimc.anticrash.runtime.VersionNbt.contains(extension, "IdPhase", 99)) {
            item.anticrash$setIdPhase(extension.getInt("IdPhase").orElseThrow());
        }
    }
}
