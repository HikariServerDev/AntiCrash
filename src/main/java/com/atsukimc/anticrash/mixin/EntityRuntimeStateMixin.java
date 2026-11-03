package com.atsukimc.anticrash.mixin;

import com.atsukimc.anticrash.runtime.RuntimeExtensionNbt;

//? if (>=1.18 && <=1.21.11) {
import net.minecraft.entity.Entity;
//?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.core.BlockPos;
*///?}

//? if (>=1.18 && <=1.21.11) {
import net.minecraft.entity.ExperienceOrbEntity;
//?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.nbt.CompoundTag;
*///?}

//? if (>=1.18 && <=1.21.11) {
import net.minecraft.entity.ItemEntity;
//?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.world.entity.Entity;
*///?}

//? if (>=1.18 && <=1.21.11) {
import net.minecraft.entity.LivingEntity;
//?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.world.entity.ExperienceOrb;
*///?}

//? if (>=1.18 && <=1.21.11) {
import net.minecraft.nbt.NbtCompound;
//?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.world.entity.LivingEntity;
*///?}

//? if (>=1.18 && <=1.21.4) {
import net.minecraft.nbt.NbtHelper;
//?}

//? if (>=1.18 && <=1.21.11) {
import net.minecraft.util.math.BlockPos;
//?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.world.entity.item.ItemEntity;
*///?}

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//? if (>=1.18 && <=1.21.5) {
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

//?}

/**
 * Entity state Vanilla does not save although the next tick depends on it. Written only while
 * entities that stay loaded are saved (see {@link RuntimeExtensionNbt#savingLoadedEntities()}) and
 * only when it differs from what a freshly loaded entity would have, so ordinary entity data and
 * the unchanged-entity-chunk detection stay as they were.
 */
@Mixin(Entity.class)
public abstract class EntityRuntimeStateMixin {

    //? if 1.18 {
    /*@Shadow public boolean inanimate;
*///?}
    //? if (>=26.1 && <=26.3) {
    /*@Shadow public net.minecraft.world.entity.PortalProcessor portalProcess;
*///?}

    //? if (>=1.18 && <=1.20.6) {
    @Shadow protected boolean inNetherPortal;
    //?}
    //? if (>=26.1 && <=26.3) {
    /*@Shadow protected int boardingCooldown;
*///?}

    //? if (>=1.18 && <=1.20.6) {
    @Shadow protected int netherPortalTime;
    //?}
    //? if (>=26.1 && <=26.3) {
    /*@Shadow protected boolean firstTick;
*///?}

    //? if (>=1.18 && <=1.20.6) {
    @Shadow protected BlockPos lastNetherPortalPosition;
    //?}
    //? if (>=1.21 && <=1.21.11) {
    /*@Shadow public net.minecraft.world.dimension.PortalManager portalManager;
*///?}
    //? if (>=26.1 && <=26.3) {
    /*@Shadow protected boolean wasTouchingWater;
*///?}

    //? if (>=1.18 && <=1.21.11) {
    @Shadow protected int ridingCooldown;
    //?}
    //? if (>=26.1 && <=26.3) {
    /*@Shadow public int invulnerableTime;
*///?}

    //? if (>=1.18 && <=1.21.11) {
    @Shadow protected boolean firstUpdate;
    //?}
    //? if (>=26.1 && <=26.3) {
    /*@Shadow public int tickCount;
*///?}

    //? if (>=1.18 && <=1.21.11) {
    @Shadow protected boolean touchingWater;
    //?}
    //? if (>=26.1 && <=26.3) {
    /*@Shadow public java.util.Optional<BlockPos> mainSupportingBlockPos;
*///?}

    //? if (>=1.18 && <=1.21.11) {
    @Shadow public int timeUntilRegen;
    //?}
    //? if (>=26.1 && <=26.3) {
    /*@Shadow private boolean onGroundNoBlocks;
*///?}

    //? if (>=1.18 && <=1.21.11) {
    @Shadow public int age;

    //?}

    //? if (>=1.20 && <=1.21.11) {
    /*@Shadow public java.util.Optional<BlockPos> supportingBlockPos;
*///?}

    //? if (>=1.20 && <=1.21.11) {
    /*@Shadow private boolean forceUpdateSupportingBlockPos;
*///?}

    //? if (>=1.20 && <=1.21.11) {
    /*@Shadow public boolean groundCollision;
*///?}
    //? if (>=26.1 && <=26.3) {
    /*@Shadow public boolean verticalCollisionBelow;
*///?}

    //? if (>=1.18 && <=1.21.5) {
    @Inject(method = "writeNbt", at = @At("RETURN"))
    private void anticrash$saveRuntimeState(
            NbtCompound nbt, CallbackInfoReturnable<NbtCompound> callbackInfo)
                //?}
                //? if (>=1.21.6 && <=1.21.11) {
                /*@Inject(method = "writeData", at = @At("RETURN"))
                    private void anticrash$saveRuntimeState(
                            net.minecraft.storage.WriteView nbt, CallbackInfo callbackInfo)
*///?}
                //? if (>=26.1 && <=26.3) {
                /*@Inject(method = "saveWithoutId", at = @At("RETURN"))
                    private void anticrash$saveRuntimeState(
                            net.minecraft.world.level.storage.ValueOutput nbt, CallbackInfo callbackInfo)
*///?}
            {
        if (!RuntimeExtensionNbt.savingLoadedEntities()) return;
        Object self = this;
        RuntimeExtensionNbt.write(
                nbt,
                extension ->
                //? if (>=1.18.1 && <=1.19.4) {
                {
                    // Touched a nether portal this tick: the dimension change happens at the start
                    // of the
                    // next tick. Without this the entity has to touch the portal again, one tick
                    // late.

                    //?}
                    //? if 1.18 {
                    /*{
                                        // 1.18 EntityView reads this for collision queries. Falling-block construction
                                        // paths do not all set the same default; preserve both true and false at the
                                        // cut.
                                        extension.putBoolean("Inanimate", inanimate);
*///?}
                    //? if (>=1.20 && <=26.3) {
                    /*{
*///?}
                    //? if (>=26.1 && <=26.3) {
                    /*extension.put(
                                                "FluidTrackers",
                                                com.atsukimc.anticrash.runtime.FluidInteractionSnapshot.capture(
                                                        (Entity) self));
*///?}

                    //? if (>=1.21.11 && <=26.3) {
                    /*extension.put(
                                                "MotionFrame",
                                                com.atsukimc.anticrash.runtime.EntityMotionFrame.capture(
                                                                (Entity) (Object) this)
                                                        .write());
*///?}

                    //? if (>=1.21.11 && <=26.3) {
                    /*if (self instanceof LivingEntity)
                                            extension.putLong(
                                                    "LastKineticAttackTime",
                                                    ((LivingEntityAccessorMixin) self)
                                                            .anticrash$lastKineticAttackTime());
*///?}

                    //? if (>=1.20 && <=1.21.11) {
                    /*extension.putBoolean("GroundCollision", groundCollision);
*///?}
                    //? if (>=26.1 && <=26.3) {
                    /*extension.putBoolean("GroundCollision", verticalCollisionBelow);
*///?}

                    //? if (>=1.20 && <=1.21.11) {
                    /*extension.putBoolean("ForceSupportUpdate", forceUpdateSupportingBlockPos);
*///?}
                    //? if (>=26.1 && <=26.3) {
                    /*extension.putBoolean("ForceSupportUpdate", onGroundNoBlocks);
*///?}

                    //? if (>=1.20 && <=1.21.11) {
                    /*if (supportingBlockPos.isPresent())
*///?}
                    //? if (>=1.20 && <=1.21.4) {
                    /*extension.put(
                                                    "SupportingBlock",
                                                    NbtHelper.fromBlockPos(supportingBlockPos.get()));
*///?}
                    //? if (>=1.21.5 && <=1.21.11) {
                    /*extension.put(
                                                    "SupportingBlock",
                                                    net.minecraft.util.math.BlockPos.CODEC,
                                                    supportingBlockPos.get());
*///?}
                    //? if (>=26.1 && <=26.3) {
                    /*if (mainSupportingBlockPos.isPresent())
                                            extension.store(
                                                    "SupportingBlock",
                                                    net.minecraft.core.BlockPos.CODEC,
                                                    mainSupportingBlockPos.get());
*///?}

                    // Touched a nether portal this tick: the dimension change happens at the start
                    // of the
                    // next tick. Without this the entity has to touch the portal again, one tick
                    // late.

                    //? if (>=1.18 && <=1.20.6) {
                    if (inNetherPortal || netherPortalTime > 0) {
                        NbtCompound portal = new NbtCompound();
                        //?}

                        //? if (>=1.18 && <=1.20.6) {
                        portal.putBoolean("In", inNetherPortal);
                        //?}

                        //? if (>=1.18 && <=1.20.6) {
                        portal.putInt("Time", netherPortalTime);
                        //?}

                        //? if (>=1.18 && <=1.20.6) {
                        if (lastNetherPortalPosition != null)
                            portal.put("Pos", NbtHelper.fromBlockPos(lastNetherPortalPosition));
                        //?}

                        //? if (>=1.18 && <=1.20.6) {
                        extension.put("Portal", portal);
                    }
                    //?}
                    //? if (>=1.21 && <=1.21.11) {
                    /*if (portalManager != null)
                                            extension.put(
                                                    "Portal",
                                                    com.atsukimc.anticrash.runtime.PortalRuntime.write(portalManager));
*///?}
                    //? if (>=26.1 && <=26.3) {
                    /*if (portalProcess != null)
                                            extension.put(
                                                    "Portal",
                                                    com.atsukimc.anticrash.runtime.PortalRuntime.write(portalProcess));
*///?}

                    // 60 ticks after leaving a vehicle during which the entity cannot board
                    // another.

                    //? if (>=1.18 && <=1.21.11) {
                    if (ridingCooldown > 0) extension.putInt("RidingCooldown", ridingCooldown);
                    //?}
                    //? if (>=26.1 && <=26.3) {
                    /*if (boardingCooldown > 0) extension.putInt("RidingCooldown", boardingCooldown);
*///?}

                    // A loaded entity is "new" for one tick: Entity#isInLava is false and it does
                    // not
                    // splash. An entity that had already ticked must not go through that tick
                    // again.

                    //? if (>=1.18 && <=1.21.11) {
                    if (!firstUpdate)
                    //?}
                    //? if (>=26.1 && <=26.3) {
                    /*if (!firstTick)
*///?}
                    {
                        extension.putBoolean("Ticked", true);

                        //? if (>=1.18 && <=1.21.11) {
                        if (touchingWater)
                            //?}
                            //? if (>=26.1 && <=26.3) {
                            /*if (wasTouchingWater)
*///?}
                            extension.putBoolean("Water", true);
                    }
                    // Damage immunity window; LivingEntity compares new damage with lastDamageTaken
                    // in it.

                    //? if (>=1.18 && <=1.21.11) {
                    if (timeUntilRegen > 0)
                    //?}
                    //? if (>=26.1 && <=26.3) {
                    /*if (invulnerableTime > 0)
*///?}
                    {

                        //? if (>=1.18 && <=1.21.11) {
                        extension.putInt("Regen", timeUntilRegen);
                        //?}
                        //? if (>=26.1 && <=26.3) {
                        /*extension.putInt("Regen", invulnerableTime);
*///?}

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

                    //? if (>=1.18 && <=1.21.11) {
                    if (self instanceof ItemEntity || self instanceof ExperienceOrbEntity)
                        extension.putInt("Age", age);
                    //?}
                    //? if (>=26.1 && <=26.3) {
                    /*if (self instanceof ItemEntity || self instanceof ExperienceOrb)
                                            extension.putInt("Age", tickCount);
*///?}

                    if (self instanceof com.atsukimc.anticrash.runtime.ItemPhaseHolder item)
                        extension.putInt("IdPhase", item.anticrash$idPhase());

                    //? if (>=1.21 && <=1.21.11) {
                    /*if (self instanceof net.minecraft.entity.TntEntity)
*///?}
                    //? if (>=26.1 && <=26.3) {
                    /*if (self instanceof net.minecraft.world.entity.item.PrimedTnt)
*///?}
                    //? if (>=1.21 && <=26.3) {
                    /*extension.putBoolean(
                                                    "TeleportedTnt",
                                                    ((TntRuntimeAccessorMixin) self).anticrash$teleported());
*///?}

                    //? if (>=1.21 && <=1.21.11) {
                    /*if (self instanceof net.minecraft.entity.FallingBlockEntity falling)
                                            extension.putBoolean("ShouldDupe", falling.shouldDupe);
*///?}
                    //? if (>=26.1 && <=26.3) {
                    /*if (self instanceof net.minecraft.world.entity.item.FallingBlockEntity falling)
                                            extension.putBoolean(
                                                    "ShouldDupe", falling.forceTickAfterTeleportToDuplicate);
*///?}

                    //? if (>=1.21.9 && <=1.21.11) {
                    /*if (self instanceof net.minecraft.entity.projectile.ProjectileEntity)
*///?}
                    //? if (>=26.1 && <=26.3) {
                    /*if (self instanceof net.minecraft.world.entity.projectile.Projectile)
*///?}
                    //? if (>=1.21.9 && <=26.3) {
                    /*extension.putBoolean(
                                                    "CheckedForLeftOwner",
                                                    ((ProjectileRuntimeAccessorMixin) self)
                                                            .anticrash$checkedForLeftOwner());
*///?}

                });
    }

    //? if (>=1.18 && <=1.21.5) {
    @Inject(method = "readNbt", at = @At("TAIL"))
    private void anticrash$loadRuntimeState(NbtCompound nbt, CallbackInfo callbackInfo)
                //?}
                //? if (>=1.21.6 && <=1.21.11) {
                /*@Inject(method = "readData", at = @At("TAIL"))
                    private void anticrash$loadRuntimeState(
                            net.minecraft.storage.ReadView nbt, CallbackInfo callbackInfo)
*///?}
                //? if (>=26.1 && <=26.3) {
                /*@Inject(method = "load", at = @At("TAIL"))
                    private void anticrash$loadRuntimeState(
                            net.minecraft.world.level.storage.ValueInput nbt, CallbackInfo callbackInfo)
*///?}
            {

        //? if (>=1.18 && <=1.21.11) {
        NbtCompound extension = RuntimeExtensionNbt.read(nbt);
        //?}
        //? if (>=26.1 && <=26.3) {
        /*CompoundTag extension = RuntimeExtensionNbt.read(nbt);
*///?}

        if (extension == null) return;

        //? if 1.18 {
        /*if (extension.contains("Inanimate")) inanimate = extension.getBoolean("Inanimate");
*///?}
        //? if (>=1.20 && <=26.3) {
        /*Object self = this;
*///?}

        // Old normal-world entity NBT may predate 26.1. Cross-game checkpoint restoration is
        // rejected separately; do not prevent Vanilla's ordinary world upgrade on this basis.

        //? if (>=26.1 && <=26.3) {
        /*if (extension.contains("FluidTrackers"))
                    com.atsukimc.anticrash.runtime.FluidInteractionSnapshot.restore(
                            (Entity) self,
                            com.atsukimc.anticrash.runtime.VersionNbt.list(extension, "FluidTrackers", 10));
*///?}

        //? if (>=1.18 && <=1.19.4) {
        Object self = this;
        //?}
        //? if (>=1.21 && <=1.21.4) {
        /*if (self instanceof net.minecraft.entity.TntEntity
                        && extension.contains("TeleportedTnt", 1))
                    ((TntRuntimeAccessorMixin) self)
                            .anticrash$setTeleported(extension.getBoolean("TeleportedTnt"));
*///?}
        //? if (>=1.21.11 && <=26.3) {
        /*if (self instanceof LivingEntity && extension.contains("LastKineticAttackTime"))
                    ((LivingEntityAccessorMixin) self)
                            .anticrash$setLastKineticAttackTime(
                                    extension.getLong("LastKineticAttackTime").orElseThrow());
*///?}

        //? if (>=1.21.11 && <=26.3) {
        /*if (extension.contains("MotionFrame"))
                    com.atsukimc.anticrash.runtime.EntityMotionFrame.read(
                                    extension.getCompound("MotionFrame").orElseThrow())
                            .apply((Entity) self);
*///?}

        //? if (>=1.21.9 && <=1.21.11) {
        /*if (self instanceof net.minecraft.entity.projectile.ProjectileEntity)
*///?}
        //? if (>=26.1 && <=26.3) {
        /*if (self instanceof net.minecraft.world.entity.projectile.Projectile)
*///?}
        //? if (>=1.21.9 && <=26.3) {
        /*((ProjectileRuntimeAccessorMixin) self)
                            .anticrash$setCheckedForLeftOwner(
                                    extension.getBoolean("CheckedForLeftOwner").orElseThrow());
*///?}

        //? if (>=1.21.5 && <=1.21.11) {
        /*if (self instanceof net.minecraft.entity.TntEntity
                        && com.atsukimc.anticrash.runtime.VersionNbt.contains(
                                extension, "TeleportedTnt", 1))
*///?}
        //? if (>=26.1 && <=26.3) {
        /*if (self instanceof net.minecraft.world.entity.item.PrimedTnt
                        && com.atsukimc.anticrash.runtime.VersionNbt.contains(
                                extension, "TeleportedTnt", 1))
*///?}
        //? if (>=1.21.5 && <=26.3) {
        /*((TntRuntimeAccessorMixin) self)
                            .anticrash$setTeleported(extension.getBoolean("TeleportedTnt").orElseThrow());
*///?}

        //? if (>=1.21 && <=1.21.4) {
        /*if (self instanceof net.minecraft.entity.FallingBlockEntity falling
                        && extension.contains("ShouldDupe", 1))
                    falling.shouldDupe = extension.getBoolean("ShouldDupe");
*///?}
        //? if (>=1.21.5 && <=1.21.11) {
        /*if (self instanceof net.minecraft.entity.FallingBlockEntity falling
                        && com.atsukimc.anticrash.runtime.VersionNbt.contains(extension, "ShouldDupe", 1))
                    falling.shouldDupe = extension.getBoolean("ShouldDupe").orElseThrow();
*///?}
        //? if (>=26.1 && <=26.3) {
        /*if (self instanceof net.minecraft.world.entity.item.FallingBlockEntity falling
                        && com.atsukimc.anticrash.runtime.VersionNbt.contains(extension, "ShouldDupe", 1))
                    falling.forceTickAfterTeleportToDuplicate =
                            extension.getBoolean("ShouldDupe").orElseThrow();
*///?}

        //? if (>=1.20 && <=1.21.4) {
        /*if (extension.contains("ForceSupportUpdate", 1))
*///?}
        //? if (>=1.21.5 && <=26.3) {
        /*if (com.atsukimc.anticrash.runtime.VersionNbt.contains(
                        extension, "ForceSupportUpdate", 1))
*///?}
        //? if (>=1.20 && <=26.3) {
        /*{
*///?}
        //? if (>=1.20 && <=1.21.4) {
        /*forceUpdateSupportingBlockPos = extension.getBoolean("ForceSupportUpdate");
*///?}
        //? if (>=1.21.5 && <=1.21.11) {
        /*forceUpdateSupportingBlockPos =
                            extension.getBoolean("ForceSupportUpdate").orElseThrow();
*///?}
        //? if (>=26.1 && <=26.3) {
        /*onGroundNoBlocks = extension.getBoolean("ForceSupportUpdate").orElseThrow();
*///?}

        //? if (>=1.20 && <=1.21.4) {
        /*groundCollision = extension.getBoolean("GroundCollision");
*///?}
        //? if (>=1.21.5 && <=1.21.11) {
        /*groundCollision = extension.getBoolean("GroundCollision").orElseThrow();
*///?}
        //? if (>=26.1 && <=26.3) {
        /*verticalCollisionBelow = extension.getBoolean("GroundCollision").orElseThrow();
*///?}

        //? if (>=1.20 && <=1.20.4) {
        /*supportingBlockPos =
                            extension.contains("SupportingBlock", 10)
                                    ? java.util.Optional.of(
                                            NbtHelper.toBlockPos(extension.getCompound("SupportingBlock")))
                                    : java.util.Optional.empty();
*///?}
        //? if (>=1.20.5 && <=1.21.4) {
        /*supportingBlockPos = NbtHelper.toBlockPos(extension, "SupportingBlock");
*///?}
        //? if (>=1.21.5 && <=1.21.11) {
        /*supportingBlockPos =
                            extension.get("SupportingBlock", net.minecraft.util.math.BlockPos.CODEC);
*///?}
        //? if (>=26.1 && <=26.3) {
        /*mainSupportingBlockPos =
                            extension.read("SupportingBlock", net.minecraft.core.BlockPos.CODEC);
*///?}
        //? if (>=1.20 && <=26.3) {

        /*}
*///?}

        //? if (>=1.18 && <=1.21.4) {
        if (extension.contains("Portal", 10))
        //?}
        //? if (>=1.21.5 && <=26.3) {
        /*if (com.atsukimc.anticrash.runtime.VersionNbt.contains(extension, "Portal", 10))
*///?}
        {

            //? if (>=1.18 && <=1.20.6) {
            NbtCompound portal = extension.getCompound("Portal");
            //?}

            //? if (>=1.18 && <=1.20.6) {
            inNetherPortal = portal.getBoolean("In");
            //?}

            //? if (>=1.18 && <=1.20.6) {
            netherPortalTime = Math.max(0, portal.getInt("Time"));
            //?}

            //? if (>=1.18 && <=1.20.4) {
            if (portal.contains("Pos", 10))
                lastNetherPortalPosition = NbtHelper.toBlockPos(portal.getCompound("Pos"));
            //?}
            //? if (>=1.20.5 && <=1.20.6) {
            /*lastNetherPortalPosition =
                                NbtHelper.toBlockPos(portal, "Pos").orElse(lastNetherPortalPosition);
*///?}
            //? if (>=1.21 && <=1.21.4) {
            /*portalManager =
                                com.atsukimc.anticrash.runtime.PortalRuntime.read(
                                        extension.getCompound("Portal"));
*///?}
            //? if (>=1.21.5 && <=1.21.11) {
            /*portalManager =
                                com.atsukimc.anticrash.runtime.PortalRuntime.read(
                                        extension.getCompoundOrEmpty("Portal"));
*///?}
            //? if (>=26.1 && <=26.3) {
            /*portalProcess =
                                com.atsukimc.anticrash.runtime.PortalRuntime.read(
                                        extension.getCompoundOrEmpty("Portal"));
*///?}

        }

        //? if (>=1.18 && <=1.21.4) {
        if (extension.contains("RidingCooldown", 99))
            ridingCooldown = Math.max(0, extension.getInt("RidingCooldown"));
        //?}
        //? if (>=1.21.5 && <=26.3) {
        /*if (com.atsukimc.anticrash.runtime.VersionNbt.contains(extension, "RidingCooldown", 99))
*///?}
        //? if (>=1.21.5 && <=1.21.11) {
        /*ridingCooldown = Math.max(0, extension.getInt("RidingCooldown").orElseThrow());
*///?}
        //? if (>=26.1 && <=26.3) {
        /*boardingCooldown = Math.max(0, extension.getInt("RidingCooldown").orElseThrow());
*///?}

        //? if (>=1.18 && <=1.21.4) {
        if (extension.getBoolean("Ticked"))
        //?}
        //? if (>=1.21.5 && <=26.3) {
        /*if (com.atsukimc.anticrash.runtime.VersionNbt.sparseFlag(extension, "Ticked"))
*///?}
        {

            //? if (>=1.18 && <=1.21.11) {
            firstUpdate = false;
            //?}
            //? if (>=26.1 && <=26.3) {
            /*firstTick = false;
*///?}

            //? if (>=1.18 && <=1.21.4) {
            touchingWater = extension.getBoolean("Water");
            //?}
            //? if (>=1.21.5 && <=1.21.11) {
            /*touchingWater =
                                com.atsukimc.anticrash.runtime.VersionNbt.sparseFlag(extension, "Water");
*///?}
            //? if (>=26.1 && <=26.3) {
            /*wasTouchingWater =
                                com.atsukimc.anticrash.runtime.VersionNbt.sparseFlag(extension, "Water");
*///?}

        }

        //? if (>=1.18 && <=1.21.4) {
        if (extension.contains("Regen", 99))
        //?}
        //? if (>=1.21.5 && <=26.3) {
        /*if (com.atsukimc.anticrash.runtime.VersionNbt.contains(extension, "Regen", 99))
*///?}
        {

            //? if (>=1.18 && <=1.21.4) {
            timeUntilRegen = Math.max(0, extension.getInt("Regen"));
            //?}
            //? if (>=1.21.5 && <=1.21.11) {
            /*timeUntilRegen = Math.max(0, extension.getInt("Regen").orElseThrow());
*///?}
            //? if (>=26.1 && <=26.3) {
            /*invulnerableTime = Math.max(0, extension.getInt("Regen").orElseThrow());
*///?}

            //? if (>=1.18 && <=1.21.4) {
            if (self instanceof LivingEntity living && extension.contains("LastDamage", 99))
            //?}
            //? if (>=1.21.5 && <=26.3) {
            /*if (self instanceof LivingEntity living
                                && com.atsukimc.anticrash.runtime.VersionNbt.contains(
                                        extension, "LastDamage", 99))
*///?}
            {

                //? if (>=1.18 && <=1.21.4) {
                ((LivingEntityAccessorMixin) living)
                        .anticrash$setLastDamageTaken(extension.getFloat("LastDamage"));
                //?}
                //? if (>=1.21.5 && <=26.3) {
                /*((LivingEntityAccessorMixin) living)
                                        .anticrash$setLastDamageTaken(
                                                extension.getFloat("LastDamage").orElseThrow());
*///?}

            }
        }

        //? if (>=1.18 && <=1.21.4) {
        if ((self instanceof ItemEntity || self instanceof ExperienceOrbEntity)
                && extension.contains("Age", 99))
        //?}
        //? if (>=1.21.5 && <=1.21.11) {
        /*if ((self instanceof ItemEntity || self instanceof ExperienceOrbEntity)
                        && com.atsukimc.anticrash.runtime.VersionNbt.contains(extension, "Age", 99))
*///?}
        //? if (>=26.1 && <=26.3) {
        /*if ((self instanceof ItemEntity || self instanceof ExperienceOrb)
                        && com.atsukimc.anticrash.runtime.VersionNbt.contains(extension, "Age", 99))
*///?}
        {

            //? if (>=1.18 && <=1.21.4) {
            age = Math.max(0, extension.getInt("Age"));
            //?}
            //? if (>=1.21.5 && <=1.21.11) {
            /*age = Math.max(0, extension.getInt("Age").orElseThrow());
*///?}
            //? if (>=26.1 && <=26.3) {
            /*tickCount = Math.max(0, extension.getInt("Age").orElseThrow());
*///?}

        }

        //? if (>=1.18 && <=1.21.4) {
        if (self instanceof com.atsukimc.anticrash.runtime.ItemPhaseHolder item
                && extension.contains("IdPhase", 99))
        //?}
        //? if (>=1.21.5 && <=26.3) {
        /*if (self instanceof com.atsukimc.anticrash.runtime.ItemPhaseHolder item
                        && com.atsukimc.anticrash.runtime.VersionNbt.contains(extension, "IdPhase", 99))
*///?}
        {

            //? if (>=1.18 && <=1.21.4) {
            item.anticrash$setIdPhase(extension.getInt("IdPhase"));
            //?}
            //? if (>=1.21.5 && <=26.3) {
            /*item.anticrash$setIdPhase(extension.getInt("IdPhase").orElseThrow());
*///?}

        }
    }
}
