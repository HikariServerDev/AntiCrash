//? if (>=1.21 && <=26.3) {
package com.atsukimc.anticrash.runtime;

import com.atsukimc.anticrash.mixin.PortalManagerAccessorMixin;

//? if (>=1.21 && <=1.21.11) {
/*import net.minecraft.world.level.block.Block;
*///?}
//? if (>=26.1 && <=26.3) {
import net.minecraft.core.registries.BuiltInRegistries;
//?}

//? if (>=1.21 && <=1.21.11) {
/*import net.minecraft.world.level.block.Portal;
*///?}
//? if (>=26.1 && <=26.3) {
import net.minecraft.nbt.CompoundTag;
//?}

//? if (>=1.21 && <=1.21.11) {
/*import net.minecraft.nbt.CompoundTag;
*///?}
//? if (>=26.1 && <=26.3) {
import net.minecraft.resources.Identifier;
//?}

//? if (>=1.21 && <=1.21.4) {
/*import net.minecraft.nbt.NbtUtils;
*///?}
//? if (>=26.1 && <=26.3) {
import net.minecraft.world.entity.PortalProcessor;
//?}

//? if (>=1.21 && <=1.21.11) {
/*import net.minecraft.core.registries.BuiltInRegistries;
*///?}
//? if (>=26.1 && <=26.3) {
import net.minecraft.world.level.block.Block;
//?}

//? if (>=1.21 && <=1.21.10) {
/*import net.minecraft.resources.ResourceLocation;
*///?}
//? if 1.21.11 {
/*import net.minecraft.resources.Identifier;
*///?}

//? if (>=1.21 && <=1.21.11) {
/*import net.minecraft.world.entity.PortalProcessor;
*///?}
//? if (>=26.1 && <=26.3) {
import net.minecraft.world.level.block.Portal;

//?}

public final class PortalRuntime {
    private PortalRuntime() {}

    //? if (>=1.21 && <=1.21.11) {
    /*public static String type(PortalProcessor manager)
*///?} else if (>=26.1 && <=26.3) {
    public static String type(PortalProcessor manager)
                //?}
            {
        Portal portal = ((PortalManagerAccessorMixin) manager).anticrash$getPortal();

        //? if (>=1.21 && <=1.21.11) {
        /*return portal instanceof Block block ? BuiltInRegistries.BLOCK.getKey(block).toString() : null;
*///?} else if (>=26.1 && <=26.3) {
        return portal instanceof Block block
                ? BuiltInRegistries.BLOCK.getKey(block).toString()
                : null;
        //?}

    }

    //? if (>=1.21 && <=1.21.11) {
    /*public static CompoundTag write(PortalProcessor manager)
*///?} else if (>=26.1 && <=26.3) {
    public static CompoundTag write(PortalProcessor manager)
                //?}
            {
        String type = type(manager);
        if (type == null)
            throw new IllegalStateException("UNSUPPORTED non-block Portal implementation");

        //? if (>=1.21 && <=1.21.11) {
        /*CompoundTag nbt = new CompoundTag();
*///?} else if (>=26.1 && <=26.3) {
        CompoundTag nbt = new CompoundTag();
        //?}

        nbt.putString("Type", type);

        //? if (>=1.21 && <=1.21.4) {
        /*nbt.put("Pos", NbtUtils.writeBlockPos(manager.getEntryPosition()));
*///?} else if (>=1.21.5 && <=1.21.11) {
        /*nbt.store("Pos", net.minecraft.core.BlockPos.CODEC, manager.getEntryPosition());
*///?} else if (>=26.1 && <=26.3) {
        nbt.store("Pos", net.minecraft.core.BlockPos.CODEC, manager.getEntryPosition());
        //?}

        //? if (>=1.21 && <=1.21.11) {
        /*nbt.putInt("Time", manager.getPortalTime());
*///?} else if (>=26.1 && <=26.3) {
        nbt.putInt("Time", manager.getPortalTime());
        //?}

        //? if (>=1.21 && <=1.21.11) {
        /*nbt.putBoolean("In", manager.isInsidePortalThisTick());
*///?} else if (>=26.1 && <=26.3) {
        nbt.putBoolean("In", manager.isInsidePortalThisTick());
        //?}

        return nbt;
    }

    //? if (>=1.21 && <=1.21.11) {
    /*public static PortalProcessor read(CompoundTag nbt)
*///?} else if (>=26.1 && <=26.3) {
    public static PortalProcessor read(CompoundTag nbt)
                //?}
            {

        //? if (>=1.21 && <=1.21.4) {
        /*ResourceLocation id = ResourceLocation.tryParse(nbt.getString("Type"));
*///?} else if (>=1.21.5 && <=1.21.10) {
        /*ResourceLocation id = ResourceLocation.tryParse(nbt.getString("Type").orElseThrow());
*///?} else if (>=1.21.11 && <=26.3) {
        Identifier id = Identifier.tryParse(nbt.getString("Type").orElseThrow());
        //?}

        //? if (>=1.21 && <=1.21.11) {
        /*if (id == null
                || !BuiltInRegistries.BLOCK.containsKey(id)
*///?}
        //? if (>=1.21 && <=1.21.1) {
        /*|| !(BuiltInRegistries.BLOCK.get(id) instanceof Portal portal))
*///?} else if (>=1.21.2 && <=1.21.11) {
        /*|| !(BuiltInRegistries.BLOCK.getValue(id) instanceof Portal portal))
*///?} else if (>=26.1 && <=26.3) {
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
*///?} else if (>=1.21.5 && <=1.21.11) {
        /*var pos =
                nbt.read("Pos", net.minecraft.core.BlockPos.CODEC)
                        .orElseThrow(() ->
                                new IllegalArgumentException("Missing saved Portal position"));
*///?} else if (>=26.1 && <=26.3) {
        var pos =
                nbt.read("Pos", net.minecraft.core.BlockPos.CODEC)
                        .orElseThrow(() ->
                                new IllegalArgumentException("Missing saved Portal position"));
        //?}

        //? if (>=1.21 && <=1.21.4) {
        /*int ticks = nbt.getInt("Time");
*///?} else if (>=1.21.5 && <=26.3) {
        int ticks = nbt.getInt("Time").orElseThrow();
        //?}

        if (ticks < 0) throw new IllegalArgumentException("Negative saved Portal time");

        //? if (>=1.21 && <=1.21.11) {
        /*PortalProcessor manager = new PortalProcessor(portal, pos);
*///?} else if (>=26.1 && <=26.3) {
        PortalProcessor manager = new PortalProcessor(portal, pos);
        //?}

        //? if (>=1.21 && <=1.21.4) {
        /*manager.setAsInsidePortalThisTick(nbt.getBoolean("In"));
*///?} else if (>=1.21.5 && <=1.21.11) {
        /*manager.setAsInsidePortalThisTick(nbt.getBoolean("In").orElseThrow());
*///?} else if (>=26.1 && <=26.3) {
        manager.setAsInsidePortalThisTick(nbt.getBoolean("In").orElseThrow());
        //?}

        ((PortalManagerAccessorMixin) manager).anticrash$setTicksInPortal(ticks);
        return manager;
    }

    //? if (>=1.21 && <=1.21.11) {
    /*public static String canonical(PortalProcessor manager)
*///?} else if (>=26.1 && <=26.3) {
    public static String canonical(PortalProcessor manager)
                //?}
            {

        //? if (>=1.21 && <=1.21.11) {
        /*return type(manager)
                + "|pos="
                + manager.getEntryPosition().asLong()
                + "|ticks="
                + manager.getPortalTime()
                + "|in="
                + manager.isInsidePortalThisTick();
*///?} else if (>=26.1 && <=26.3) {
        return type(manager)
                + "|pos="
                + manager.getEntryPosition().asLong()
                + "|ticks="
                + manager.getPortalTime()
                + "|in="
                + manager.isInsidePortalThisTick();
        //?}

    }
}
//?}
