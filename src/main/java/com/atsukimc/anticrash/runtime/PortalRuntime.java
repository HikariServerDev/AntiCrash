//? if (>=1.21 && <=26.3) {
package com.atsukimc.anticrash.runtime;

import com.atsukimc.anticrash.mixin.PortalManagerAccessorMixin;
import com.atsukimc.anticrash.util.VersionIds;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Portal;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.PortalProcessor;

//? if (>=1.21 && <=1.21.4) {
/*import net.minecraft.nbt.NbtUtils;

*///?}

public final class PortalRuntime {
    private PortalRuntime() {}

    public static String type(PortalProcessor manager) {
        Portal portal = ((PortalManagerAccessorMixin) manager).anticrash$getPortal();

        return portal instanceof Block block ? VersionIds.id(VersionIds.blocks(), block) : null;
    }

    public static CompoundTag write(PortalProcessor manager) {
        String type = type(manager);
        if (type == null)
            throw new IllegalStateException("UNSUPPORTED non-block Portal implementation");

        CompoundTag nbt = new CompoundTag();

        nbt.putString("Type", type);

        //? if (>=1.21 && <=1.21.4) {
        /*nbt.put("Pos", NbtUtils.writeBlockPos(manager.getEntryPosition()));
        *///?}
        //? if (>=1.21.5 && <=26.3) {
        nbt.store("Pos", net.minecraft.core.BlockPos.CODEC, manager.getEntryPosition());
        //?}

        nbt.putInt("Time", manager.getPortalTime());

        nbt.putBoolean("In", manager.isInsidePortalThisTick());

        return nbt;
    }

    public static PortalProcessor read(CompoundTag nbt) {

        //? if (>=1.21 && <=1.21.4) {
        /*var id = VersionIds.tryParse(nbt.getString("Type"));
        *///?}
        //? if (>=1.21.5 && <=26.3) {
        var id = VersionIds.tryParse(nbt.getString("Type").orElseThrow());
        //?}

        if (id == null
                || !VersionIds.blocks().containsKey(id)
                || !(VersionIds.value(VersionIds.blocks(), id) instanceof Portal portal))
            throw new IllegalArgumentException("Invalid saved Portal type");

        //? if (>=1.21 && <=1.21.4) {
        /*var pos =
                NbtUtils.readBlockPos(nbt, "Pos")
                        .orElseThrow(() ->
                                new IllegalArgumentException("Missing saved Portal position"));
        *///?}
        //? if (>=1.21.5 && <=26.3) {
        var pos =
                nbt.read("Pos", net.minecraft.core.BlockPos.CODEC)
                        .orElseThrow(() ->
                                new IllegalArgumentException("Missing saved Portal position"));
        //?}

        //? if (>=1.21 && <=1.21.4) {
        /*int ticks = nbt.getInt("Time");
        *///?}
        //? if (>=1.21.5 && <=26.3) {
        int ticks = nbt.getInt("Time").orElseThrow();
        //?}

        if (ticks < 0) throw new IllegalArgumentException("Negative saved Portal time");

        PortalProcessor manager = new PortalProcessor(portal, pos);

        //? if (>=1.21 && <=1.21.4) {
        /*manager.setAsInsidePortalThisTick(nbt.getBoolean("In"));
        *///?}
        //? if (>=1.21.5 && <=26.3) {
        manager.setAsInsidePortalThisTick(nbt.getBoolean("In").orElseThrow());
        //?}

        ((PortalManagerAccessorMixin) manager).anticrash$setTicksInPortal(ticks);
        return manager;
    }

    public static String canonical(PortalProcessor manager) {

        return type(manager)
                + "|pos="
                + manager.getEntryPosition().asLong()
                + "|ticks="
                + manager.getPortalTime()
                + "|in="
                + manager.isInsidePortalThisTick();
    }
}
//?}
