package com.atsukimc.anticrash.runtime;

import com.atsukimc.anticrash.mixin.PortalManagerAccessorMixin;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.PortalProcessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Portal;

public final class PortalRuntime {
    private PortalRuntime() {}
    public static String type(PortalProcessor manager) {
        Portal portal = ((PortalManagerAccessorMixin) manager).anticrash$getPortal();
        return portal instanceof Block block ? BuiltInRegistries.BLOCK.getKey(block).toString() : null;
    }
    public static CompoundTag write(PortalProcessor manager) {
        String type = type(manager);
        if (type == null) throw new IllegalStateException("UNSUPPORTED non-block Portal implementation");
        CompoundTag nbt = new CompoundTag();
        nbt.putString("Type", type); nbt.store("Pos", net.minecraft.core.BlockPos.CODEC, manager.getEntryPosition());
        nbt.putInt("Time", manager.getPortalTime()); nbt.putBoolean("In", manager.isInsidePortalThisTick());
        return nbt;
    }
    public static PortalProcessor read(CompoundTag nbt) {
        Identifier id = Identifier.tryParse(nbt.getString("Type").orElseThrow());
        if (id == null || !BuiltInRegistries.BLOCK.containsKey(id) || !(BuiltInRegistries.BLOCK.getValue(id) instanceof Portal portal))
            throw new IllegalArgumentException("Invalid saved Portal type");
        var pos = nbt.read("Pos", net.minecraft.core.BlockPos.CODEC).orElseThrow(
                () -> new IllegalArgumentException("Missing saved Portal position"));
        int ticks = nbt.getInt("Time").orElseThrow();
        if (ticks < 0) throw new IllegalArgumentException("Negative saved Portal time");
        PortalProcessor manager = new PortalProcessor(portal, pos);
        manager.setAsInsidePortalThisTick(nbt.getBoolean("In").orElseThrow());
        ((PortalManagerAccessorMixin) manager).anticrash$setTicksInPortal(ticks);
        return manager;
    }
    public static String canonical(PortalProcessor manager) {
        return type(manager) + "|pos=" + manager.getEntryPosition().asLong() + "|ticks="
                + manager.getPortalTime() + "|in=" + manager.isInsidePortalThisTick();
    }
}
