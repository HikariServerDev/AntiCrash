package com.atsukimc.anticrash.runtime;

import net.minecraft.nbt.*;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.storage.NbtWriteView;
import net.minecraft.util.ErrorReporter;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerAbilities;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.StackWithSlot;
import net.minecraft.item.ItemStack;
import java.util.*;

/** 1.21.6 view adapters. Codec failures must abort capture/validation, never silently drop data. */
public final class VersionStorage {
    private VersionStorage() { }
    public record ThrowingReporter(String path) implements ErrorReporter {
        public ErrorReporter makeChild(ErrorReporter.Context context) {
            return new ThrowingReporter(path + "/" + context.getName());
        }
        public void report(ErrorReporter.Error error) {
            throw new IllegalStateException("AntiCrash serialization failed at " + path + ": " + error.getMessage());
        }
    }
    public static NbtWriteView writer(RegistryWrapper.WrapperLookup registries) {
        return NbtWriteView.create(new ThrowingReporter("snapshot"), registries);
    }
    public static NbtCompound entity(Entity entity) {
        var view = writer(entity.getRegistryManager());
        return entity.saveData(view) ? view.getNbt() : null;
    }
    public static NbtCompound abilities(PlayerAbilities abilities) {
        var nbt = new NbtCompound(); nbt.put("abilities", PlayerAbilities.Packed.CODEC, abilities.pack()); return nbt;
    }
    public static PlayerAbilities.Packed abilities(NbtCompound nbt) {
        return nbt.get("abilities", PlayerAbilities.Packed.CODEC).orElseThrow(
                () -> new IllegalArgumentException("Invalid player abilities"));
    }
    public static NbtList inventory(Inventory inventory, RegistryWrapper.WrapperLookup registries) {
        var list = new NbtList(); var ops = registries.getOps(NbtOps.INSTANCE);
        for (int slot = 0; slot < inventory.size(); slot++) {
            var stack = inventory.getStack(slot);
            if (!stack.isEmpty()) list.add(StackWithSlot.CODEC.encodeStart(ops, new StackWithSlot(slot, stack)).getOrThrow());
        }
        return list;
    }
    public static List<ItemStack> inventory(NbtList encoded, int size, RegistryWrapper.WrapperLookup registries) {
        if (encoded.size() > size) throw new IllegalArgumentException("Too many inventory entries");
        List<ItemStack> stacks = new ArrayList<>(Collections.nCopies(size, ItemStack.EMPTY));
        Set<Integer> slots = new HashSet<>(); var ops = registries.getOps(NbtOps.INSTANCE);
        for (var element : encoded) {
            var entry = StackWithSlot.CODEC.parse(ops, element).getOrThrow();
            if (!entry.isValidSlot(size) || !slots.add(entry.slot())) throw new IllegalArgumentException("Invalid/duplicate inventory slot");
            stacks.set(entry.slot(), entry.stack());
        }
        return stacks;
    }
    public static void applyInventory(Inventory inventory, List<ItemStack> stacks) {
        if (inventory.size() != stacks.size()) throw new IllegalArgumentException("Inventory shape changed");
        for (int i = 0; i < stacks.size(); i++) inventory.setStack(i, stacks.get(i));
        inventory.markDirty();
    }
}
