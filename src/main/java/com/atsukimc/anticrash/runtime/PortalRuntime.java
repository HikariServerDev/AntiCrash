//? if (>=1.21 && <=26.3) {
/*package com.atsukimc.anticrash.runtime;

import com.atsukimc.anticrash.mixin.PortalManagerAccessorMixin;

//? if (>=1.21 && <=1.21.11) {
/^import net.minecraft.block.Block;
^///?}
//? if (>=26.1 && <=26.3) {
/^import net.minecraft.core.registries.BuiltInRegistries;
^///?}

//? if (>=1.21 && <=1.21.11) {
/^import net.minecraft.block.Portal;
^///?}
//? if (>=26.1 && <=26.3) {
/^import net.minecraft.nbt.CompoundTag;
^///?}

//? if (>=1.21 && <=1.21.11) {
/^import net.minecraft.nbt.NbtCompound;
^///?}
//? if (>=26.1 && <=26.3) {
/^import net.minecraft.resources.Identifier;
^///?}

//? if (>=1.21 && <=1.21.4) {
/^import net.minecraft.nbt.NbtHelper;
^///?}
//? if (>=26.1 && <=26.3) {
/^import net.minecraft.world.entity.PortalProcessor;
^///?}

//? if (>=1.21 && <=1.21.11) {
/^import net.minecraft.registry.Registries;
^///?}
//? if (>=26.1 && <=26.3) {
/^import net.minecraft.world.level.block.Block;
^///?}

//? if (>=1.21 && <=1.21.11) {
/^import net.minecraft.util.Identifier;
^///?}

//? if (>=1.21 && <=1.21.11) {
/^import net.minecraft.world.dimension.PortalManager;
^///?}
//? if (>=26.1 && <=26.3) {
/^import net.minecraft.world.level.block.Portal;
^///?}


public final class PortalRuntime {
    private PortalRuntime() {}


//? if (>=1.21 && <=1.21.11) {
/^public static String type(PortalManager manager)
^///?}
//? if (>=26.1 && <=26.3) {
/^public static String type(PortalProcessor manager)
^///?}
{
        Portal portal = ((PortalManagerAccessorMixin) manager).anticrash$getPortal();

//? if (>=1.21 && <=1.21.11) {
/^return portal instanceof Block block ? Registries.BLOCK.getId(block).toString() : null;
^///?}
//? if (>=26.1 && <=26.3) {
/^return portal instanceof Block block
                ? BuiltInRegistries.BLOCK.getKey(block).toString()
                : null;
^///?}

    }


//? if (>=1.21 && <=1.21.11) {
/^public static NbtCompound write(PortalManager manager)
^///?}
//? if (>=26.1 && <=26.3) {
/^public static CompoundTag write(PortalProcessor manager)
^///?}
{
        String type = type(manager);
        if (type == null)
            throw new IllegalStateException("UNSUPPORTED non-block Portal implementation");

//? if (>=1.21 && <=1.21.11) {
/^NbtCompound nbt = new NbtCompound();
^///?}
//? if (>=26.1 && <=26.3) {
/^CompoundTag nbt = new CompoundTag();
^///?}

        nbt.putString("Type", type);

//? if (>=1.21 && <=1.21.4) {
/^nbt.put("Pos", NbtHelper.fromBlockPos(manager.getPortalPos()));
^///?}
//? if (>=1.21.5 && <=1.21.11) {
/^nbt.put("Pos", net.minecraft.util.math.BlockPos.CODEC, manager.getPortalPos());
^///?}
//? if (>=26.1 && <=26.3) {
/^nbt.store("Pos", net.minecraft.core.BlockPos.CODEC, manager.getEntryPosition());
^///?}


//? if (>=1.21 && <=1.21.11) {
/^nbt.putInt("Time", manager.getTicksInPortal());
^///?}
//? if (>=26.1 && <=26.3) {
/^nbt.putInt("Time", manager.getPortalTime());
^///?}


//? if (>=1.21 && <=1.21.11) {
/^nbt.putBoolean("In", manager.isInPortal());
^///?}
//? if (>=26.1 && <=26.3) {
/^nbt.putBoolean("In", manager.isInsidePortalThisTick());
^///?}

        return nbt;
    }


//? if (>=1.21 && <=1.21.11) {
/^public static PortalManager read(NbtCompound nbt)
^///?}
//? if (>=26.1 && <=26.3) {
/^public static PortalProcessor read(CompoundTag nbt)
^///?}
{

//? if (>=1.21 && <=1.21.4) {
/^Identifier id = Identifier.tryParse(nbt.getString("Type"));
^///?}
//? if (>=1.21.5 && <=26.3) {
/^Identifier id = Identifier.tryParse(nbt.getString("Type").orElseThrow());
^///?}


//? if (>=1.21 && <=1.21.11) {
/^if (id == null
                || !Registries.BLOCK.containsId(id)
                || !(Registries.BLOCK.get(id) instanceof Portal portal))

^///?}
//? if (>=26.1 && <=26.3) {
/^if (id == null
                || !BuiltInRegistries.BLOCK.containsKey(id)
                || !(BuiltInRegistries.BLOCK.getValue(id) instanceof Portal portal))

^///?}
throw new IllegalArgumentException("Invalid saved Portal type");

//? if (>=1.21 && <=1.21.4) {
/^var pos =
                NbtHelper.toBlockPos(nbt, "Pos")
                        .orElseThrow(() ->
                                new IllegalArgumentException("Missing saved Portal position"));
^///?}
//? if (>=1.21.5 && <=1.21.11) {
/^var pos =
                nbt.get("Pos", net.minecraft.util.math.BlockPos.CODEC)
                        .orElseThrow(() ->
                                new IllegalArgumentException("Missing saved Portal position"));
^///?}
//? if (>=26.1 && <=26.3) {
/^var pos =
                nbt.read("Pos", net.minecraft.core.BlockPos.CODEC)
                        .orElseThrow(() ->
                                new IllegalArgumentException("Missing saved Portal position"));
^///?}


//? if (>=1.21 && <=1.21.4) {
/^int ticks = nbt.getInt("Time");
^///?}
//? if (>=1.21.5 && <=26.3) {
/^int ticks = nbt.getInt("Time").orElseThrow();
^///?}

        if (ticks < 0) throw new IllegalArgumentException("Negative saved Portal time");

//? if (>=1.21 && <=1.21.11) {
/^PortalManager manager = new PortalManager(portal, pos);
^///?}
//? if (>=26.1 && <=26.3) {
/^PortalProcessor manager = new PortalProcessor(portal, pos);
^///?}


//? if (>=1.21 && <=1.21.4) {
/^manager.setInPortal(nbt.getBoolean("In"));
^///?}
//? if (>=1.21.5 && <=1.21.11) {
/^manager.setInPortal(nbt.getBoolean("In").orElseThrow());
^///?}
//? if (>=26.1 && <=26.3) {
/^manager.setAsInsidePortalThisTick(nbt.getBoolean("In").orElseThrow());
^///?}

        ((PortalManagerAccessorMixin) manager).anticrash$setTicksInPortal(ticks);
        return manager;
    }


//? if (>=1.21 && <=1.21.11) {
/^public static String canonical(PortalManager manager)
^///?}
//? if (>=26.1 && <=26.3) {
/^public static String canonical(PortalProcessor manager)
^///?}
{

//? if (>=1.21 && <=1.21.11) {
/^return type(manager)
                + "|pos="
                + manager.getPortalPos().asLong()
                + "|ticks="
                + manager.getTicksInPortal()
                + "|in="
                + manager.isInPortal();
^///?}
//? if (>=26.1 && <=26.3) {
/^return type(manager)
                + "|pos="
                + manager.getEntryPosition().asLong()
                + "|ticks="
                + manager.getPortalTime()
                + "|in="
                + manager.isInsidePortalThisTick();
^///?}

    }
}
*///?}
