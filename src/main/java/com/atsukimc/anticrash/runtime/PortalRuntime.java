//? if (>=1.21 && <=26.3) {
package com.atsukimc.anticrash.runtime;

import com.atsukimc.anticrash.mixin.PortalManagerAccessorMixin;

import net.minecraft.world.level.block.Block;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Portal;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.PortalProcessor;

//? if (>=1.21 && <=1.21.4) {
/*import net.minecraft.nbt.NbtUtils;
*///?}
//? if (>=1.21 && <=1.21.10) {
/*import net.minecraft.resources.ResourceLocation;

*///?} else {
import net.minecraft.resources.Identifier;

//?}

public final class PortalRuntime {
    private PortalRuntime() {}

    public static String type(PortalProcessor manager) {
        Portal portal = ((PortalManagerAccessorMixin) manager).anticrash$getPortal();

        return portal instanceof Block block
                ? BuiltInRegistries.BLOCK.getKey(block).toString()
                : null;
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
        /*ResourceLocation id = ResourceLocation.tryParse(nbt.getString("Type"));
        *///?}
        //? if (>=1.21.5 && <=1.21.10) {
        /*ResourceLocation id = ResourceLocation.tryParse(nbt.getString("Type").orElseThrow());
        *///?}
        //? if (>=1.21.11 && <=26.3) {
        Identifier id = Identifier.tryParse(nbt.getString("Type").orElseThrow());
        //?}

        //? if (>=1.21 && <=1.21.11) {
        /*if (id == null
                || !BuiltInRegistries.BLOCK.containsKey(id)
        *///?}
                //? if (>=1.21 && <=1.21.1) {
                /*|| !(BuiltInRegistries.BLOCK.get(id) instanceof Portal portal))
                *///?}
                //? if (>=1.21.2 && <=1.21.11) {
                /*|| !(BuiltInRegistries.BLOCK.getValue(id) instanceof Portal portal))
                *///?}
        //? if (>=26.1 && <=26.3) {
        if (id == null
                || !BuiltInRegistries.BLOCK.containsKey(id)
                || !(BuiltInRegistries.BLOCK.getValue(id) instanceof Portal portal))

        //?}
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
