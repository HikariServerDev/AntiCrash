package com.atsukimc.anticrash.runtime;

import java.util.function.Consumer;
import net.minecraft.nbt.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;

//? if (>=1.21.6 && <=26.3) {
import java.util.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.Container;
import net.minecraft.world.ItemStackWithSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.TagValueOutput;

//?}

/**
 * Saving and restoring state across versions. 1.21.6 replaced CompoundTag with view adapters in
 * these paths; there, codec failures must abort capture/validation, never silently drop data.
 */
public final class VersionStorage {
    private VersionStorage() {}

    /** Where a block entity's state is saved; {@link #result} reads it back. */
    //? if (>=1.18 && <=1.21.5) {
    /*public static CompoundTag writer(BlockEntity entity) {
        return new CompoundTag();
    }

    public static CompoundTag result(CompoundTag writer) {
        return writer;
    }

    *///?} else {
    public static TagValueOutput writer(BlockEntity entity) {
        return writer(entity.getLevel().registryAccess());
    }

    public static CompoundTag result(TagValueOutput writer) {
        return writer.buildResult();
    }

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

    //?}

    /** The saved entity, or null when it declines to be saved. */
    public static CompoundTag entity(Entity entity) {
        //? if (>=1.18 && <=1.21.5) {
        /*var nbt = new CompoundTag();

        return entity.save(nbt) ? nbt : null;
        *///?} else {
        var view = writer(entity.registryAccess());

        return entity.save(view) ? view.buildResult() : null;
        //?}
    }

    public static CompoundTag abilities(Abilities abilities) {
        var nbt = new CompoundTag();

        //? if (>=1.18 && <=1.21.5) {
        /*abilities.addSaveData(nbt);
        *///?} else {
        nbt.store("abilities", Abilities.Packed.CODEC, abilities.pack());
        //?}

        return nbt;
    }

    /** Decodes now where the version can, and restores the abilities it is given later. */
    public static Consumer<Abilities> abilities(CompoundTag nbt) {
        //? if (>=1.18 && <=1.21.5) {
        /*return abilities -> abilities.loadSaveData(nbt);
        *///?} else {
        var packed =
                nbt.read("abilities", Abilities.Packed.CODEC)
                        .orElseThrow(
                                () -> new IllegalArgumentException("Invalid player abilities"));

        return abilities -> abilities.apply(packed);
        //?}
    }

    public static ListTag enderChest(Player player) {
        //? if (>=1.18 && <=1.20.4) {
        /*return player.getEnderChestInventory().createTag();
        *///?} else if (>=1.20.5 && <=1.21.5) {
        /*return player.getEnderChestInventory().createTag(player.registryAccess());
        *///?} else {
        return inventory(player.getEnderChestInventory(), player.registryAccess());
        //?}
    }

    /** Decodes now where the version can; running the result restores the Ender Chest. */
    public static Runnable enderChest(Player player, ListTag encoded) {
        //? if (>=1.18 && <=1.20.4) {
        /*return () -> player.getEnderChestInventory().fromTag(encoded);
        *///?} else if (>=1.20.5 && <=1.21.5) {
        /*return () -> player.getEnderChestInventory().fromTag(encoded, player.registryAccess());
        *///?} else {
        var stacks =
                inventory(
                        encoded,
                        player.getEnderChestInventory().getContainerSize(),
                        player.registryAccess());

        return () -> applyInventory(player.getEnderChestInventory(), stacks);
        //?}
    }
}
