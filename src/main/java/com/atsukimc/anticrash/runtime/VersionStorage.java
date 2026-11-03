//? if (>=1.21.6 && <=26.3) {
package com.atsukimc.anticrash.runtime;

//? if (>=1.21.6 && <=1.21.11) {
/*import net.minecraft.world.entity.Entity;
*///?}

//? if (>=1.21.6 && <=1.21.11) {
/*import net.minecraft.world.entity.player.Abilities;
*///?}

//? if (>=1.21.6 && <=1.21.11) {
/*import net.minecraft.world.Container;
*///?}

//? if (>=1.21.6 && <=1.21.11) {
/*import net.minecraft.world.ItemStackWithSlot;
*///?}

//? if (>=1.21.6 && <=1.21.11) {
/*import net.minecraft.world.item.ItemStack;
*///?}
//? if (>=26.1 && <=26.3) {
import net.minecraft.core.HolderLookup;
//?}

import net.minecraft.nbt.*;
//? if (>=1.21.6 && <=1.21.11) {
/*import net.minecraft.core.HolderLookup;
*///?}
//? if (>=26.1 && <=26.3) {
import net.minecraft.util.ProblemReporter;
//?}

//? if (>=1.21.6 && <=1.21.11) {
/*import net.minecraft.world.level.storage.TagValueOutput;
*///?}
//? if (>=26.1 && <=26.3) {
import net.minecraft.world.Container;
//?}

//? if (>=1.21.6 && <=1.21.11) {
/*import net.minecraft.util.ProblemReporter;
*///?}
//? if (>=26.1 && <=26.3) {
import net.minecraft.world.ItemStackWithSlot;
//?}

//? if (>=26.1 && <=26.3) {
import net.minecraft.world.entity.Entity;
//?}

//? if (>=26.1 && <=26.3) {
import net.minecraft.world.entity.player.Abilities;
//?}

//? if (>=26.1 && <=26.3) {
import net.minecraft.world.item.ItemStack;
//?}

//? if (>=26.1 && <=26.3) {
import net.minecraft.world.level.storage.TagValueOutput;
//?}

import java.util.*;

/** 1.21.6 view adapters. Codec failures must abort capture/validation, never silently drop data. */
public final class VersionStorage {
    private VersionStorage() {}

    public record ThrowingReporter(String path
            //? if (>=1.21.6 && <=1.21.11) {
            /*) implements ProblemReporter {
        public ProblemReporter forChild(ProblemReporter.PathElement context)
*///?} else if (>=26.1 && <=26.3) {
            ) implements ProblemReporter {
        public ProblemReporter forChild(ProblemReporter.PathElement context)
                    //?}
                {

            //? if (>=1.21.6 && <=1.21.11) {
            /*return new ThrowingReporter(path + "/" + context.get());
*///?} else if (>=26.1 && <=26.3) {
            return new ThrowingReporter(path + "/" + context.get());
            //?}

        }

        //? if (>=1.21.6 && <=1.21.11) {
        /*public void report(ProblemReporter.Problem error)
*///?} else if (>=26.1 && <=26.3) {
        public void report(ProblemReporter.Problem error)
                    //?}
                {

            //? if (>=1.21.6 && <=1.21.11) {
            /*throw new IllegalStateException(
                    "AntiCrash serialization failed at " + path + ": " + error.description());
*///?} else if (>=26.1 && <=26.3) {
            throw new IllegalStateException(
                    "AntiCrash serialization failed at " + path + ": " + error.description());
            //?}

        }
    }

    //? if (>=1.21.6 && <=1.21.11) {
    /*public static TagValueOutput writer(HolderLookup.Provider registries)
*///?} else if (>=26.1 && <=26.3) {
    public static TagValueOutput writer(HolderLookup.Provider registries)
                //?}
            {

        //? if (>=1.21.6 && <=1.21.11) {
        /*return TagValueOutput.createWithContext(new ThrowingReporter("snapshot"), registries);
*///?} else if (>=26.1 && <=26.3) {
        return TagValueOutput.createWithContext(new ThrowingReporter("snapshot"), registries);
        //?}

    }

    //? if (>=1.21.6 && <=1.21.11) {
    /*public static CompoundTag entity(Entity entity)
*///?} else if (>=26.1 && <=26.3) {
    public static CompoundTag entity(Entity entity)
                //?}
            {

        //? if (>=1.21.6 && <=1.21.11) {
        /*var view = writer(entity.registryAccess());
*///?} else if (>=26.1 && <=26.3) {
        var view = writer(entity.registryAccess());
        //?}

        //? if (>=1.21.6 && <=1.21.11) {
        /*return entity.save(view) ? view.buildResult() : null;
*///?} else if (>=26.1 && <=26.3) {
        return entity.save(view) ? view.buildResult() : null;
        //?}

    }

    //? if (>=1.21.6 && <=1.21.11) {
    /*public static CompoundTag abilities(Abilities abilities)
*///?} else if (>=26.1 && <=26.3) {
    public static CompoundTag abilities(Abilities abilities)
                //?}
            {

        //? if (>=1.21.6 && <=1.21.11) {
        /*var nbt = new CompoundTag();
*///?} else if (>=26.1 && <=26.3) {
        var nbt = new CompoundTag();
        //?}

        //? if (>=1.21.6 && <=1.21.11) {
        /*nbt.store("abilities", Abilities.Packed.CODEC, abilities.pack());
*///?} else if (>=26.1 && <=26.3) {
        nbt.store("abilities", Abilities.Packed.CODEC, abilities.pack());
        //?}

        return nbt;
    }

    //? if (>=1.21.6 && <=1.21.11) {
    /*public static Abilities.Packed abilities(CompoundTag nbt)
*///?} else if (>=26.1 && <=26.3) {
    public static Abilities.Packed abilities(CompoundTag nbt)
                //?}
            {

        //? if (>=1.21.6 && <=1.21.11) {
        /*return nbt.read("abilities", Abilities.Packed.CODEC)
                .orElseThrow(() -> new IllegalArgumentException("Invalid player abilities"));
*///?} else if (>=26.1 && <=26.3) {
        return nbt.read("abilities", Abilities.Packed.CODEC)
                .orElseThrow(() -> new IllegalArgumentException("Invalid player abilities"));
        //?}

    }

    //? if (>=1.21.6 && <=1.21.11) {
    /*public static ListTag inventory(Container inventory, HolderLookup.Provider registries)
*///?} else if (>=26.1 && <=26.3) {
    public static ListTag inventory(Container inventory, HolderLookup.Provider registries)
                //?}
            {

        //? if (>=1.21.6 && <=1.21.11) {
        /*var list = new ListTag();
*///?} else if (>=26.1 && <=26.3) {
        var list = new ListTag();
        //?}

        //? if (>=1.21.6 && <=1.21.11) {
        /*var ops = registries.createSerializationContext(NbtOps.INSTANCE);
*///?} else if (>=26.1 && <=26.3) {
        var ops = registries.createSerializationContext(NbtOps.INSTANCE);
        //?}

        //? if (>=1.21.6 && <=1.21.11) {
        /*for (int slot = 0; slot < inventory.getContainerSize();
*///?} else if (>=26.1 && <=26.3) {
        for (int slot = 0;
                slot < inventory.getContainerSize();
                //?}
                slot++) {

            //? if (>=1.21.6 && <=1.21.11) {
            /*var stack = inventory.getItem(slot);
*///?} else if (>=26.1 && <=26.3) {
            var stack = inventory.getItem(slot);
            //?}

            if (!stack.isEmpty())

                //? if (>=1.21.6 && <=1.21.11) {
                /*list.add(
                        ItemStackWithSlot.CODEC
                                .encodeStart(ops, new ItemStackWithSlot(slot, stack))
                                .getOrThrow());
*///?} else if (>=26.1 && <=26.3) {
                list.add(
                        ItemStackWithSlot.CODEC
                                .encodeStart(ops, new ItemStackWithSlot(slot, stack))
                                .getOrThrow());
            //?}

        }
        return list;
    }

    //? if (>=1.21.6 && <=1.21.11) {
    /*public static List<ItemStack> inventory(
            ListTag encoded, int size, HolderLookup.Provider registries)
*///?} else if (>=26.1 && <=26.3) {
    public static List<ItemStack> inventory(
            ListTag encoded, int size, HolderLookup.Provider registries)
                //?}
            {
        if (encoded.size() > size) throw new IllegalArgumentException("Too many inventory entries");
        List<ItemStack> stacks = new ArrayList<>(Collections.nCopies(size, ItemStack.EMPTY));
        Set<Integer> slots = new HashSet<>();

        //? if (>=1.21.6 && <=1.21.11) {
        /*var ops = registries.createSerializationContext(NbtOps.INSTANCE);
*///?} else if (>=26.1 && <=26.3) {
        var ops = registries.createSerializationContext(NbtOps.INSTANCE);
        //?}

        for (var element : encoded) {

            //? if (>=1.21.6 && <=1.21.11) {
            /*var entry = ItemStackWithSlot.CODEC.parse(ops, element).getOrThrow();
*///?} else if (>=26.1 && <=26.3) {
            var entry = ItemStackWithSlot.CODEC.parse(ops, element).getOrThrow();
            //?}

            //? if (>=1.21.6 && <=1.21.11) {
            /*if (!entry.isValidInContainer(size) || !slots.add(entry.slot()))

*///?} else if (>=26.1 && <=26.3) {
            if (!entry.isValidInContainer(size) || !slots.add(entry.slot()))

                //?}
                throw new IllegalArgumentException("Invalid/duplicate inventory slot");
            stacks.set(entry.slot(), entry.stack());
        }
        return stacks;
    }

    //? if (>=1.21.6 && <=1.21.11) {
    /*public static void applyInventory(Container inventory, List<ItemStack> stacks)
*///?} else if (>=26.1 && <=26.3) {
    public static void applyInventory(Container inventory, List<ItemStack> stacks)
                //?}
            {

        //? if (>=1.21.6 && <=1.21.11) {
        /*if (inventory.getContainerSize() != stacks.size())

*///?} else if (>=26.1 && <=26.3) {
        if (inventory.getContainerSize() != stacks.size())

            //?}
            throw new IllegalArgumentException("Inventory shape changed");
        for (int i = 0; i < stacks.size(); i++)
            //? if (>=1.21.6 && <=1.21.11) {
            /*inventory.setItem(i, stacks.get(i));
*///?} else if (>=26.1 && <=26.3) {
            inventory.setItem(i, stacks.get(i));
        //?}

        //? if (>=1.21.6 && <=1.21.11) {
        /*inventory.setChanged();
*///?} else if (>=26.1 && <=26.3) {
        inventory.setChanged();
        //?}

    }
}
//?}
