package com.atsukimc.anticrash.runtime;

import com.atsukimc.anticrash.mixin.PortalManagerAccessorMixin;
import net.minecraft.block.Block;
import net.minecraft.block.Portal;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.world.dimension.PortalManager;

public final class PortalRuntime {
    private PortalRuntime() {}
    public static String type(PortalManager manager) {
        Portal portal = ((PortalManagerAccessorMixin) manager).anticrash$getPortal();
        return portal instanceof Block block ? Registries.BLOCK.getId(block).toString() : null;
    }
    public static NbtCompound write(PortalManager manager) {
        String type = type(manager);
        if (type == null) throw new IllegalStateException("UNSUPPORTED non-block Portal implementation");
        NbtCompound nbt = new NbtCompound();
        nbt.putString("Type", type); nbt.put("Pos", NbtHelper.fromBlockPos(manager.getPortalPos()));
        nbt.putInt("Time", manager.getTicksInPortal()); nbt.putBoolean("In", manager.isInPortal());
        return nbt;
    }
    public static PortalManager read(NbtCompound nbt) {
        Identifier id = Identifier.tryParse(nbt.getString("Type"));
        if (id == null || !Registries.BLOCK.containsId(id) || !(Registries.BLOCK.get(id) instanceof Portal portal))
            throw new IllegalArgumentException("Invalid saved Portal type");
        var pos = NbtHelper.toBlockPos(nbt, "Pos").orElseThrow(
                () -> new IllegalArgumentException("Missing saved Portal position"));
        int ticks = nbt.getInt("Time");
        if (ticks < 0) throw new IllegalArgumentException("Negative saved Portal time");
        PortalManager manager = new PortalManager(portal, pos);
        manager.setInPortal(nbt.getBoolean("In"));
        ((PortalManagerAccessorMixin) manager).anticrash$setTicksInPortal(ticks);
        return manager;
    }
    public static String canonical(PortalManager manager) {
        return type(manager) + "|pos=" + manager.getPortalPos().asLong() + "|ticks="
                + manager.getTicksInPortal() + "|in=" + manager.isInPortal();
    }
}
