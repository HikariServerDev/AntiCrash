package com.atsukimc.anticrash.runtime;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.Container;
import net.minecraft.world.ItemStackWithSlot;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.TagValueOutput;

import java.util.*;

/** 1.21.6 view adapters. Codec failures must abort capture/validation, never silently drop data. */
public final class VersionStorage {
    private VersionStorage() {}

    public record ThrowingReporter(String path) implements ProblemReporter {
        public ProblemReporter forChild(ProblemReporter.PathElement context) {
            return new ThrowingReporter(path + "/" + context.get());
        }

        public void report(ProblemReporter.Problem error) {
            throw new IllegalStateException(
                    "AntiCrash serialization failed at " + path + ": " + error.description());
        }
    }

    public static TagValueOutput writer(HolderLookup.Provider registries) {
        return TagValueOutput.createWithContext(new ThrowingReporter("snapshot"), registries);
    }

    public static CompoundTag entity(Entity entity) {
        var view = writer(entity.registryAccess());
        return entity.save(view) ? view.buildResult() : null;
    }

    public static CompoundTag abilities(Abilities abilities) {
        var nbt = new CompoundTag();
        nbt.store("abilities", Abilities.Packed.CODEC, abilities.pack());
        return nbt;
    }

    public static Abilities.Packed abilities(CompoundTag nbt) {
        return nbt.read("abilities", Abilities.Packed.CODEC)
                .orElseThrow(() -> new IllegalArgumentException("Invalid player abilities"));
    }

    public static ListTag inventory(Container inventory, HolderLookup.Provider registries) {
        var list = new ListTag();
        var ops = registries.createSerializationContext(NbtOps.INSTANCE);
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            var stack = inventory.getItem(slot);
            if (!stack.isEmpty())
                list.add(
                        ItemStackWithSlot.CODEC
                                .encodeStart(ops, new ItemStackWithSlot(slot, stack))
                                .getOrThrow());
        }
        return list;
    }

    public static List<ItemStack> inventory(
            ListTag encoded, int size, HolderLookup.Provider registries) {
        if (encoded.size() > size) throw new IllegalArgumentException("Too many inventory entries");
        List<ItemStack> stacks = new ArrayList<>(Collections.nCopies(size, ItemStack.EMPTY));
        Set<Integer> slots = new HashSet<>();
        var ops = registries.createSerializationContext(NbtOps.INSTANCE);
        for (var element : encoded) {
            var entry = ItemStackWithSlot.CODEC.parse(ops, element).getOrThrow();
            if (!entry.isValidInContainer(size) || !slots.add(entry.slot()))
                throw new IllegalArgumentException("Invalid/duplicate inventory slot");
            stacks.set(entry.slot(), entry.stack());
        }
        return stacks;
    }

    public static void applyInventory(Container inventory, List<ItemStack> stacks) {
        if (inventory.getContainerSize() != stacks.size())
            throw new IllegalArgumentException("Inventory shape changed");
        for (int i = 0; i < stacks.size(); i++) inventory.setItem(i, stacks.get(i));
        inventory.setChanged();
    }
}
