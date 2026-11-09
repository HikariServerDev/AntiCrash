package com.atsukimc.anticrash.mixin;

import com.atsukimc.anticrash.runtime.RuntimeExtensionNbt;
import com.atsukimc.anticrash.runtime.VersionNbt;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.BlockPos;

//? if (>=1.18 && <=1.21.4) {
/*import net.minecraft.nbt.NbtUtils;
*///?}

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

//? if (>=1.18 && <=1.21.5) {
/*import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

*///?}

/**
 * Entity state Vanilla does not save although the next tick depends on it. Written only while
 * entities that stay loaded are saved (see {@link RuntimeExtensionNbt#savingLoadedEntities()}) and
 * only when it differs from what a freshly loaded entity would have, so ordinary entity data and
 * the unchanged-entity-chunk detection stay as they were.
 */
@Mixin(Entity.class)
public abstract class EntityRuntimeStateMixin {

    //? if 1.18 {
    /*@Shadow public boolean blocksBuilding;
    *///?}

    //? if (>=1.18 && <=1.20.6) {
    /*@Shadow protected boolean isInsidePortal;
    @Shadow protected int portalTime;
    @Shadow protected BlockPos portalEntrancePos;
    *///?} else{
    @Shadow public net.minecraft.world.entity.PortalProcessor portalProcess;
    //?}

    @Shadow protected int boardingCooldown;
    @Shadow protected boolean firstTick;
    @Shadow protected boolean wasTouchingWater;
    @Shadow public int invulnerableTime;
    @Shadow public int tickCount;

    //? if (>=1.20 && <=26.3) {
    @Shadow public java.util.Optional<BlockPos> mainSupportingBlockPos;
    @Shadow private boolean onGroundNoBlocks;
    @Shadow public boolean verticalCollisionBelow;

    //?}

    //? if (>=1.18 && <=1.21.5) {
    /*@Inject(method = "saveWithoutId", at = @At("RETURN"))
    private void anticrash$saveRuntimeState(
            CompoundTag nbt, CallbackInfoReturnable<CompoundTag> callbackInfo) {
    *///?} else {
    @Inject(method = "saveWithoutId", at = @At("RETURN"))
    private void anticrash$saveRuntimeState(
            net.minecraft.world.level.storage.ValueOutput nbt, CallbackInfo callbackInfo) {
    //?}

        if (!RuntimeExtensionNbt.savingLoadedEntities()) return;
        Object self = this;
        RuntimeExtensionNbt.write(
                nbt,
                extension -> {
                    //? if 1.18 {
                    /*// 1.18 EntityGetter reads this for collision queries. Falling-block
                    // construction paths do not all set the same default; preserve both true and
                    // false at the cut.
                    extension.putBoolean("Inanimate", blocksBuilding);
                    *///?}

                    // Touched a nether portal this tick: the dimension change happens at the start
                    // of the
                    // next tick. Without this the entity has to touch the portal again, one tick
                    // late.

                    //? if (>=26.1 && <=26.3) {
                    extension.put(
                            "FluidTrackers",
                            com.atsukimc.anticrash.runtime.FluidInteractionSnapshot.capture(
                                    (Entity) self));
                    //?}

                    //? if (>=1.21.11 && <=26.3) {
                    extension.put(
                            "MotionFrame",
                            com.atsukimc.anticrash.runtime.EntityMotionFrame.capture(
                                            (Entity) (Object) this)
                                    .write());

                    if (self instanceof LivingEntity)
                        extension.putLong(
                                "LastKineticAttackTime",
                                ((LivingEntityAccessorMixin) self)
                                        .anticrash$lastKineticAttackTime());
                    //?}

                    //? if (>=1.20 && <=26.3) {
                    extension.putBoolean("GroundCollision", verticalCollisionBelow);
                    extension.putBoolean("ForceSupportUpdate", onGroundNoBlocks);
                    //?}

                    //? if (>=1.20 && <=1.21.4) {
                    /*if (mainSupportingBlockPos.isPresent())
                        extension.put(
                                "SupportingBlock",
                                NbtUtils.writeBlockPos(mainSupportingBlockPos.get()));
                    *///?}
                    //? if (>=1.21.5 && <=26.3) {
                    if (mainSupportingBlockPos.isPresent())
                        extension.store(
                                "SupportingBlock",
                                net.minecraft.core.BlockPos.CODEC,
                                mainSupportingBlockPos.get());
                    //?}

                    // Touched a nether portal this tick: the dimension change happens at the start
                    // of the
                    // next tick. Without this the entity has to touch the portal again, one tick
                    // late.

                    //? if (>=1.18 && <=1.20.6) {
                    /*if (isInsidePortal || portalTime > 0) {
                        CompoundTag portal = new CompoundTag();
                        portal.putBoolean("In", isInsidePortal);
                        portal.putInt("Time", portalTime);
                        if (portalEntrancePos != null)
                            portal.put("Pos", NbtUtils.writeBlockPos(portalEntrancePos));
                        extension.put("Portal", portal);
                    }
                    *///?} else {
                    if (portalProcess != null)
                        extension.put(
                                "Portal",
                                com.atsukimc.anticrash.runtime.PortalRuntime.write(portalProcess));
                    //?}

                    // 60 ticks after leaving a vehicle during which the entity cannot board
                    // another.

                    if (boardingCooldown > 0) extension.putInt("RidingCooldown", boardingCooldown);

                    // A loaded entity is "new" for one tick: Entity#isInLava is false and it does
                    // not
                    // splash. An entity that had already ticked must not go through that tick
                    // again.

                    if (!firstTick) {
                        extension.putBoolean("Ticked", true);

                        if (wasTouchingWater) extension.putBoolean("Water", true);
                    }
                    // Damage immunity window; LivingEntity compares new damage with lastDamageTaken
                    // in it.

                    if (invulnerableTime > 0) {

                        extension.putInt("Regen", invulnerableTime);

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

                    if (self instanceof ItemEntity || self instanceof ExperienceOrb)
                        extension.putInt("Age", tickCount);

                    if (self instanceof com.atsukimc.anticrash.runtime.ItemPhaseHolder item)
                        extension.putInt("IdPhase", item.anticrash$idPhase());

                    //? if (>=1.21 && <=26.3) {
                    if (self instanceof net.minecraft.world.entity.item.PrimedTnt)
                        extension.putBoolean(
                                "TeleportedTnt",
                                ((TntRuntimeAccessorMixin) self).anticrash$teleported());
                    if (self instanceof net.minecraft.world.entity.item.FallingBlockEntity falling)
                        extension.putBoolean(
                                "ShouldDupe", falling.forceTickAfterTeleportToDuplicate);
                    //?}

                    //? if (>=1.21.9 && <=26.3) {
                    if (self instanceof net.minecraft.world.entity.projectile.Projectile)
                        extension.putBoolean(
                                "CheckedForLeftOwner",
                                ((ProjectileRuntimeAccessorMixin) self)
                                        .anticrash$checkedForLeftOwner());
                    //?}
                });
    }

    //? if (>=1.18 && <=1.21.5) {
    /*@Inject(method = "load", at = @At("TAIL"))
    private void anticrash$loadRuntimeState(CompoundTag nbt, CallbackInfo callbackInfo) {
    *///?} else {
    @Inject(method = "load", at = @At("TAIL"))
    private void anticrash$loadRuntimeState(
            net.minecraft.world.level.storage.ValueInput nbt, CallbackInfo callbackInfo) {
    //?}

        CompoundTag extension = RuntimeExtensionNbt.read(nbt);

        if (extension == null) return;

        //? if 1.18 {
        /*if (extension.contains("Inanimate"))
            blocksBuilding = VersionNbt.getBoolean(extension, "Inanimate");
        *///?}
        //? if (>=1.20 && <=26.3) {
        Object self = this;
        //?}

        // Old normal-world entity NBT may predate 26.1. Cross-game checkpoint restoration is
        // rejected separately; do not prevent Vanilla's ordinary world upgrade on this basis.

        //? if (>=26.1 && <=26.3) {
        if (extension.contains("FluidTrackers"))
            com.atsukimc.anticrash.runtime.FluidInteractionSnapshot.restore(
                    (Entity) self, VersionNbt.list(extension, "FluidTrackers", 10));
        //?}

        //? if (>=1.18 && <=1.19.4) {
        /*Object self = this;
        *///?}
        //? if (>=1.21 && <=1.21.4) {
        /*if (self instanceof net.minecraft.world.entity.item.PrimedTnt
                && VersionNbt.contains(extension, "TeleportedTnt", 1))
            ((TntRuntimeAccessorMixin) self)
                    .anticrash$setTeleported(VersionNbt.getBoolean(extension, "TeleportedTnt"));
        *///?}
        //? if (>=1.21.11 && <=26.3) {
        if (self instanceof LivingEntity && extension.contains("LastKineticAttackTime"))
            ((LivingEntityAccessorMixin) self)
                    .anticrash$setLastKineticAttackTime(
                            VersionNbt.getLong(extension, "LastKineticAttackTime"));
        if (extension.contains("MotionFrame"))
            com.atsukimc.anticrash.runtime.EntityMotionFrame.read(
                            VersionNbt.getCompound(extension, "MotionFrame"))
                    .apply((Entity) self);
        //?}

        //? if (>=1.21.9 && <=26.3) {
        if (self instanceof net.minecraft.world.entity.projectile.Projectile)
            ((ProjectileRuntimeAccessorMixin) self)
                    .anticrash$setCheckedForLeftOwner(
                            VersionNbt.getBoolean(extension, "CheckedForLeftOwner"));
        //?}

        //? if (>=1.21.5 && <=26.3) {
        if (self instanceof net.minecraft.world.entity.item.PrimedTnt
                && VersionNbt.contains(extension, "TeleportedTnt", 1))
            ((TntRuntimeAccessorMixin) self)
                    .anticrash$setTeleported(VersionNbt.getBoolean(extension, "TeleportedTnt"));
        //?}

        //? if (>=1.21 && <=1.21.4) {
        /*if (self instanceof net.minecraft.world.entity.item.FallingBlockEntity falling
                && VersionNbt.contains(extension, "ShouldDupe", 1))
            falling.forceTickAfterTeleportToDuplicate =
                    VersionNbt.getBoolean(extension, "ShouldDupe");
        *///?}
        //? if (>=1.21.5 && <=26.3) {
        if (self instanceof net.minecraft.world.entity.item.FallingBlockEntity falling
                && VersionNbt.contains(extension, "ShouldDupe", 1))
            falling.forceTickAfterTeleportToDuplicate =
                    VersionNbt.getBoolean(extension, "ShouldDupe");
        //?}

        //? if (>=1.20 && <=1.21.4) {
        /*if (VersionNbt.contains(extension, "ForceSupportUpdate", 1)) {
            onGroundNoBlocks = VersionNbt.getBoolean(extension, "ForceSupportUpdate");
            verticalCollisionBelow = VersionNbt.getBoolean(extension, "GroundCollision");
            //? if <=1.20.4 {
            /^mainSupportingBlockPos =
                    VersionNbt.contains(extension, "SupportingBlock", 10)
                            ? java.util.Optional.of(
                                    NbtUtils.readBlockPos(
                                            VersionNbt.getCompound(extension, "SupportingBlock")))
                            : java.util.Optional.empty();
            ^///?} else {
            mainSupportingBlockPos = NbtUtils.readBlockPos(extension, "SupportingBlock");
            //?}
        }
        *///?}
        //? if (>=1.21.5 && <=26.3) {
        if (VersionNbt.contains(extension, "ForceSupportUpdate", 1)) {
            onGroundNoBlocks = VersionNbt.getBoolean(extension, "ForceSupportUpdate");
            verticalCollisionBelow = VersionNbt.getBoolean(extension, "GroundCollision");
            mainSupportingBlockPos =
                    extension.read("SupportingBlock", net.minecraft.core.BlockPos.CODEC);
        }
        //?}

        //? if (>=1.18 && <=1.21.4) {
        /*if (VersionNbt.contains(extension, "Portal", 10)) {
        *///?} else {
        if (VersionNbt.contains(extension, "Portal", 10)) {
        //?}
            //? if (>=1.18 && <=1.20.6) {
            /*CompoundTag portal = VersionNbt.getCompoundOrEmpty(extension, "Portal");
            isInsidePortal = VersionNbt.getBoolean(portal, "In");
            portalTime = Math.max(0, VersionNbt.getInt(portal, "Time"));
            *///?}

            //? if (>=1.18 && <=1.20.4) {
            /*if (VersionNbt.contains(portal, "Pos", 10))
                portalEntrancePos = NbtUtils.readBlockPos(VersionNbt.getCompound(portal, "Pos"));
            *///?} else if <= 1.20.6 {
            /*portalEntrancePos = NbtUtils.readBlockPos(portal, "Pos").orElse(portalEntrancePos);
            *///?} else if <= 1.21.4 {
            /*portalProcess =
                    com.atsukimc.anticrash.runtime.PortalRuntime.read(
                            VersionNbt.getCompoundOrEmpty(extension, "Portal"));
            *///?} else {
            portalProcess =
                    com.atsukimc.anticrash.runtime.PortalRuntime.read(
                            VersionNbt.getCompoundOrEmpty(extension, "Portal"));
            //?}
        }

        //? if (>=1.18 && <=1.21.4) {
        /*if (VersionNbt.contains(extension, "RidingCooldown", 99))
            boardingCooldown = Math.max(0, VersionNbt.getInt(extension, "RidingCooldown"));

        if (VersionNbt.sparseFlag(extension, "Ticked")) {
        *///?} else {
        if (VersionNbt.contains(extension, "RidingCooldown", 99))
            boardingCooldown = Math.max(0, VersionNbt.getInt(extension, "RidingCooldown"));

        if (VersionNbt.sparseFlag(extension, "Ticked")) {
        //?}

            firstTick = false;

            //? if (>=1.18 && <=1.21.4) {
            /*wasTouchingWater = VersionNbt.sparseFlag(extension, "Water");
            *///?} else {
            wasTouchingWater = VersionNbt.sparseFlag(extension, "Water");
            //?}

        }

        //? if (>=1.18 && <=1.21.4) {
        /*if (VersionNbt.contains(extension, "Regen", 99)) {
            invulnerableTime = Math.max(0, VersionNbt.getInt(extension, "Regen"));
            if (self instanceof LivingEntity living
                    && VersionNbt.contains(extension, "LastDamage", 99)) {
                ((LivingEntityAccessorMixin) living)
                        .anticrash$setLastDamageTaken(VersionNbt.getFloat(extension, "LastDamage"));
            }
        }
        if ((self instanceof ItemEntity || self instanceof ExperienceOrb)
                && VersionNbt.contains(extension, "Age", 99)) {
            tickCount = Math.max(0, VersionNbt.getInt(extension, "Age"));
        }
        if (self instanceof com.atsukimc.anticrash.runtime.ItemPhaseHolder item
                && VersionNbt.contains(extension, "IdPhase", 99)) {
            item.anticrash$setIdPhase(VersionNbt.getInt(extension, "IdPhase"));
        }
        *///?} else {
        if (VersionNbt.contains(extension, "Regen", 99)) {
            invulnerableTime = Math.max(0, VersionNbt.getInt(extension, "Regen"));
            if (self instanceof LivingEntity living
                    && VersionNbt.contains(extension, "LastDamage", 99)) {
                ((LivingEntityAccessorMixin) living)
                        .anticrash$setLastDamageTaken(VersionNbt.getFloat(extension, "LastDamage"));
            }
        }
        if ((self instanceof ItemEntity || self instanceof ExperienceOrb)
                && VersionNbt.contains(extension, "Age", 99)) {
            tickCount = Math.max(0, VersionNbt.getInt(extension, "Age"));
        }
        if (self instanceof com.atsukimc.anticrash.runtime.ItemPhaseHolder item
                && VersionNbt.contains(extension, "IdPhase", 99)) {
            item.anticrash$setIdPhase(VersionNbt.getInt(extension, "IdPhase"));
        }
        //?}
    }
}
