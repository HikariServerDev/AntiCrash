package com.atsukimc.anticrash.player;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class InventorySnapshot {
    public final List<String> main = new ArrayList<>();
    public final List<String> armor = new ArrayList<>();
    public final List<String> offhand = new ArrayList<>();
    public int selectedSlot;

    public static InventorySnapshot capture(
            Player player,
            boolean mainEnabled,
            boolean armorEnabled,
            boolean offhandEnabled,
            boolean selectedEnabled)
            throws IOException {
        if (!(mainEnabled || armorEnabled || offhandEnabled || selectedEnabled)) return null;
        InventorySnapshot snapshot = new InventorySnapshot();

        Inventory inventory = player.getInventory();

        if (mainEnabled)

            //? if (>=1.18 && <=1.21.4) {
            /*for (ItemStack stack : inventory.items)

                *///?}
            //? if (>=1.18 && <=1.20.4) {
            /*snapshot.main.add(NbtCodec.encode(stack.save(new net.minecraft.nbt.CompoundTag())));
        *///?}
            //? if (>=1.20.5 && <=1.21.4) {
            /*snapshot.main.add(
                                NbtCodec.encode(stack.saveOptional(player.registryAccess())));
*///?}
            //? if (>=1.21.5 && <=26.3) {
            for (ItemStack stack : inventory.getNonEquipmentItems())
                snapshot.main.add(
                        NbtCodec.encode(
                                com.atsukimc.anticrash.runtime.VersionNbt.item(
                                        stack, player.registryAccess())));
        //?}

        if (armorEnabled)

            //? if (>=1.18 && <=1.21.4) {
            /*for (ItemStack stack : inventory.armor)

                *///?}
            //? if (>=1.18 && <=1.20.4) {
            /*snapshot.armor.add(
                        NbtCodec.encode(stack.save(new net.minecraft.nbt.CompoundTag())));
        *///?}
            //? if (>=1.20.5 && <=1.21.4) {
            /*snapshot.armor.add(
                                NbtCodec.encode(stack.saveOptional(player.registryAccess())));
*///?}
            //? if (>=1.21.5 && <=26.3) {
            for (ItemStack stack : equipmentStacks(inventory, 36, 4))
                snapshot.armor.add(
                        NbtCodec.encode(
                                com.atsukimc.anticrash.runtime.VersionNbt.item(
                                        stack, player.registryAccess())));
        //?}

        if (offhandEnabled)

            //? if (>=1.18 && <=1.21.4) {
            /*for (ItemStack stack : inventory.offhand)

                *///?}
            //? if (>=1.18 && <=1.20.4) {
            /*snapshot.offhand.add(
                        NbtCodec.encode(stack.save(new net.minecraft.nbt.CompoundTag())));
        *///?}
            //? if (>=1.20.5 && <=1.21.4) {
            /*snapshot.offhand.add(
                                NbtCodec.encode(stack.saveOptional(player.registryAccess())));
*///?}
            //? if (>=1.21.5 && <=26.3) {
            for (ItemStack stack : equipmentStacks(inventory, 40, 1))
                snapshot.offhand.add(
                        NbtCodec.encode(
                                com.atsukimc.anticrash.runtime.VersionNbt.item(
                                        stack, player.registryAccess())));
        //?}

        if (selectedEnabled)
            //? if (>=1.18 && <=1.21.4) {
            /*snapshot.selectedSlot = inventory.selected;
        *///?}
            //? if (>=1.21.5 && <=26.3) {
            snapshot.selectedSlot = inventory.getSelectedSlot();
        //?}

        return snapshot;
    }

    public boolean restore(
            Player player,
            boolean mainEnabled,
            boolean armorEnabled,
            boolean offhandEnabled,
            boolean selectedEnabled)
            throws IOException {

        Inventory inventory = player.getInventory();

        //? if (>=1.18 && <=1.20.4) {
        /*List<ItemStack> decodedMain =
                mainEnabled ? decodeList(main, inventory.items.size(), "main") : null;
        *///?}
        //? if (>=1.20.5 && <=1.21.4) {
        /*List<ItemStack> decodedMain =
                        mainEnabled
                                ? decodeList(
                                        main, inventory.items.size(), "main", player.registryAccess())
                                : null;
*///?}
        //? if (>=1.21.5 && <=26.3) {
        List<ItemStack> decodedMain =
                mainEnabled
                        ? decodeList(
                                main,
                                inventory.getNonEquipmentItems().size(),
                                "main",
                                player.registryAccess())
                        : null;
        //?}

        //? if (>=1.18 && <=1.20.4) {
        /*List<ItemStack> decodedArmor =
                armorEnabled ? decodeList(armor, inventory.armor.size(), "armor") : null;
        *///?}
        //? if (>=1.20.5 && <=1.21.4) {
        /*List<ItemStack> decodedArmor =
                        armorEnabled
                                ? decodeList(
                                        armor, inventory.armor.size(), "armor", player.registryAccess())
                                : null;
*///?}
        //? if (>=1.21.5 && <=26.3) {
        List<ItemStack> decodedArmor =
                armorEnabled ? decodeList(armor, 4, "armor", player.registryAccess()) : null;
        //?}

        //? if (>=1.18 && <=1.20.4) {
        /*List<ItemStack> decodedOffhand =
                offhandEnabled ? decodeList(offhand, inventory.offhand.size(), "offhand") : null;
        *///?}
        //? if (>=1.20.5 && <=1.21.4) {
        /*List<ItemStack> decodedOffhand =
                        offhandEnabled
                                ? decodeList(
                                        offhand,
                                        inventory.offhand.size(),
                                        "offhand",
                                        player.registryAccess())
                                : null;
*///?}
        //? if (>=1.21.5 && <=26.3) {
        List<ItemStack> decodedOffhand =
                offhandEnabled ? decodeList(offhand, 1, "offhand", player.registryAccess()) : null;
        //?}

        if (selectedEnabled && !snapshotSlotValid(selectedSlot))
            throw new IOException("Invalid selected hotbar slot: " + selectedSlot);

        // Decode and validate every enabled category before mutating a single slot.  A corrupt
        // optional item can therefore never leave a half-restored inventory.
        if (decodedMain != null) applyList(inventory, decodedMain, 0);

        //? if (>=1.18 && <=1.21.4) {
        /*if (decodedArmor != null) applyList(inventory, decodedArmor, inventory.items.size());
        *///?}
        //? if (>=1.21.5 && <=26.3) {
        if (decodedArmor != null)
            applyList(inventory, decodedArmor, inventory.getNonEquipmentItems().size());
        //?}

        if (decodedOffhand != null)

            //? if (>=1.18 && <=1.21.4) {
            /*applyList(inventory, decodedOffhand, inventory.items.size() + inventory.armor.size());
        *///?}
            //? if (>=1.21.5 && <=26.3) {
            applyList(inventory, decodedOffhand, inventory.getNonEquipmentItems().size() + 4);
        //?}

        if (selectedEnabled)
            //? if (>=1.18 && <=1.21.4) {
            /*inventory.selected = selectedSlot;
        *///?}
            //? if (>=1.21.5 && <=26.3) {
            inventory.setSelectedSlot(selectedSlot);
        //?}

        boolean changed = mainEnabled || armorEnabled || offhandEnabled || selectedEnabled;
        if (changed) inventory.setChanged();

        return changed;
    }

    /** Decodes every enabled slot without mutating the live inventory. */

    public void validate(
            Player player,
            boolean mainEnabled,
            boolean armorEnabled,
            boolean offhandEnabled,
            boolean selectedEnabled)
            throws IOException {

        Inventory inventory = player.getInventory();

        //? if (>=1.18 && <=1.20.4) {
        /*if (mainEnabled) decodeList(main, inventory.items.size(), "main");
        *///?}
        //? if (>=1.20.5 && <=26.3) {
        if (mainEnabled)
            //?}
            //? if (>=1.20.5 && <=1.21.4) {
            /*decodeList(main, inventory.items.size(), "main", player.registryAccess());
*///?}
            //? if (>=1.21.5 && <=26.3) {
            decodeList(
                    main, inventory.getNonEquipmentItems().size(), "main", player.registryAccess());
        //?}

        //? if (>=1.18 && <=1.20.4) || (>=1.21.5 && <=26.3) {
        if (armorEnabled)
            //?}
            //? if (>=1.18 && <=1.20.4) {
            /*decodeList(armor, inventory.armor.size(), "armor");
        *///?}
            //? if (>=1.20.5 && <=1.21.4) {
            /*if (armorEnabled)
                    decodeList(armor, inventory.armor.size(), "armor", player.registryAccess());
*///?}
            //? if (>=1.21.5 && <=26.3) {
            decodeList(armor, 4, "armor", player.registryAccess());
        //?}

        //? if (>=1.18 && <=1.20.4) || (>=1.21.5 && <=26.3) {
        if (offhandEnabled)
            //?}
            //? if (>=1.18 && <=1.20.4) {
            /*decodeList(offhand, inventory.offhand.size(), "offhand");
        *///?}
            //? if (>=1.20.5 && <=1.21.4) {
            /*if (offhandEnabled)
                    decodeList(offhand, inventory.offhand.size(), "offhand", player.registryAccess());
*///?}
            //? if (>=1.21.5 && <=26.3) {
            decodeList(offhand, 1, "offhand", player.registryAccess());
        //?}

        if (selectedEnabled && !snapshotSlotValid(selectedSlot)) {
            throw new IOException("Invalid selected hotbar slot: " + selectedSlot);
        }
    }

    /** Validates the fixed Vanilla 1.18.2 Inventory shape without a live player. */
    public void validatePortable(
            boolean mainEnabled,
            boolean armorEnabled,
            boolean offhandEnabled,
            boolean selectedEnabled)
            throws IOException {
        if (mainEnabled)
            //? if (>=1.18 && <=1.20.4) {
            /*decodeList(main, 36, "main");
        *///?}
            //? if (>=1.20.5 && <=26.3) {
            validateEncodedList(main, 36, "main");
        //?}

        if (armorEnabled)
            //? if (>=1.18 && <=1.20.4) {
            /*decodeList(armor, 4, "armor");
        *///?}
            //? if (>=1.20.5 && <=26.3) {
            validateEncodedList(armor, 4, "armor");
        //?}

        if (offhandEnabled)
            //? if (>=1.18 && <=1.20.4) {
            /*decodeList(offhand, 1, "offhand");
        *///?}
            //? if (>=1.20.5 && <=26.3) {
            validateEncodedList(offhand, 1, "offhand");
        //?}

        if (selectedEnabled && !snapshotSlotValid(selectedSlot)) {
            throw new IOException("Invalid selected hotbar slot: " + selectedSlot);
        }
    }

    //? if (>=1.18 && <=1.20.4) {
    /*private static List<ItemStack> decodeList(
            List<String> encoded, int expectedSize, String category) throws IOException
                *///?}
    //? if (>=1.20.5 && <=26.3) {
    private static List<net.minecraft.nbt.CompoundTag> validateEncodedList(
            List<String> encoded, int expectedSize, String category) throws IOException
                //?}
            {
        if (encoded == null || encoded.size() != expectedSize) {
            throw new IOException(
                    "Inventory "
                            + category
                            + " entry count is "
                            + (encoded == null ? "null" : encoded.size())
                            + ", expected "
                            + expectedSize);
        }

        //? if (>=1.18 && <=1.20.4) {
        /*List<ItemStack> decoded = new ArrayList<>(expectedSize);
        *///?}
        //? if (>=1.20.5 && <=26.3) {
        List<net.minecraft.nbt.CompoundTag> decoded = new ArrayList<>(expectedSize);
        //?}

        for (String item : encoded) {
            try {

                net.minecraft.nbt.Tag element = NbtCodec.decode(item);

                if (!(element instanceof net.minecraft.nbt.CompoundTag compound)) {
                    throw new IOException(
                            "Inventory " + category + " contains a non-compound item");
                }
                // Pre-world inspection cannot resolve dynamic item components. Validate the bounded
                // envelope now; registry-aware decoding must finish before changing any player
                // slot.

                //? if (>=1.20.5 && <=1.21.4) {
                /*if (!compound.isEmpty()
                                        && (!compound.contains("id", 8)
                                                || net.minecraft.resources.ResourceLocation.tryParse(compound.getString("id"))
                                                        == null
                                                || !compound.contains("count", 99)
                                                || compound.getInt("count") < 1))
*///?}
                //? if (>=1.21.5 && <=1.21.11) {
                /*if (!compound.isEmpty()
                                        && (!com.atsukimc.anticrash.runtime.VersionNbt.contains(compound, "id", 8)
                                                //? if (>=1.21.5 && <=1.21.10) {
                                                /^|| net.minecraft.resources.ResourceLocation.tryParse(
                                                ^///?}
                                                //? if 1.21.11 {
                                                /^|| net.minecraft.resources.Identifier.tryParse(
                                                ^///?}
                                                                compound.getString("id").orElseThrow())
                                                        == null
                                                || !com.atsukimc.anticrash.runtime.VersionNbt.contains(
                                                        compound, "count", 99)
                                                || compound.getInt("count").orElseThrow() < 1))
*///?}
                //? if (>=26.1 && <=26.3) {
                if (!compound.isEmpty()
                        && (!com.atsukimc.anticrash.runtime.VersionNbt.contains(compound, "id", 8)
                                || net.minecraft.resources.Identifier.tryParse(
                                                compound.getString("id").orElseThrow())
                                        == null
                                || !com.atsukimc.anticrash.runtime.VersionNbt.contains(
                                        compound, "count", 99)
                                || compound.getInt("count").orElseThrow() < 1))
                //?}
                //? if (>=1.20.5 && <=26.3) {
                {
                    throw new IOException("Invalid component item envelope in " + category);
                }
                //?}

                //? if (>=1.18 && <=1.20.4) {
                /*decoded.add(ItemStack.of(compound));
                *///?}
                //? if (>=1.20.5 && <=26.3) {
                decoded.add(compound);
                //?}

            } catch (IOException exception) {
                throw exception;
            } catch (Exception exception) {
                throw new IOException(
                        "Inventory " + category + " contains malformed item NBT", exception);
            }
        }
        return decoded;
        //? if (>=1.20.5 && <=26.3) {

    }

    private static List<ItemStack> decodeList(
            List<String> encoded,
            int expectedSize,
            String category,
            net.minecraft.core.HolderLookup.Provider registries)
            throws IOException {
        List<ItemStack> result = new ArrayList<>(expectedSize);

        for (var compound : validateEncodedList(encoded, expectedSize, category)) {
            if (compound.isEmpty()) result.add(ItemStack.EMPTY);
            else
                //?}
                //? if (>=1.20.5 && <=1.21.5) {
                /*result.add(
                                ItemStack.parse(registries, compound)
                                        .orElseThrow(() ->
                                                new IOException(
                                                        "Unresolvable item/components in " + category)));
*///?}
                //? if (>=1.21.6 && <=26.3) {
                result.add(
                        ItemStack.CODEC
                                .parse(
                                        registries.createSerializationContext(
                                                net.minecraft.nbt.NbtOps.INSTANCE),
                                        compound)
                                .result()
                                .orElseThrow(() ->
                                        new IOException(
                                                "Unresolvable item/components in " + category)));
            //?}
            //? if (>=1.20.5 && <=26.3) {

        }

        return result;
        //?}

    }

    private static void applyList(Inventory inventory, List<ItemStack> decoded, int offset) {
        for (int index = 0; index < decoded.size(); index++)
            inventory.setItem(offset + index, decoded.get(index));
    }

    //? if (>=1.21.5 && <=26.3) {
    private static List<ItemStack> equipmentStacks(Inventory inventory, int offset, int count) {
        List<ItemStack> stacks = new ArrayList<>(count);

        for (int index = 0; index < count; index++) stacks.add(inventory.getItem(offset + index));

        return stacks;
    }

    //?}

    private static boolean snapshotSlotValid(int slot) {
        return slot >= 0 && slot < 9;
    }
}
