package com.atsukimc.anticrash.player;

import com.atsukimc.anticrash.runtime.VersionNbt;
import com.atsukimc.anticrash.util.VersionIds;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
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

        //? if <=1.20.4 {
        /*if (mainEnabled)
            for (ItemStack stack : inventory.items)
                snapshot.main.add(NbtCodec.encode(stack.save(new CompoundTag())));

        if (armorEnabled)
            for (ItemStack stack : inventory.armor)
                snapshot.armor.add(NbtCodec.encode(stack.save(new CompoundTag())));

        if (offhandEnabled)
            for (ItemStack stack : inventory.offhand)
                snapshot.offhand.add(NbtCodec.encode(stack.save(new CompoundTag())));
        *///?} else if <=1.21.4 {
        /*if (mainEnabled)
            for (ItemStack stack : inventory.items)
                snapshot.main.add(NbtCodec.encode(VersionNbt.item(stack, player.registryAccess())));

        if (armorEnabled)
            for (ItemStack stack : inventory.armor)
                snapshot.armor.add(
                        NbtCodec.encode(VersionNbt.item(stack, player.registryAccess())));

        if (offhandEnabled)
            for (ItemStack stack : inventory.offhand)
                snapshot.offhand.add(
                        NbtCodec.encode(VersionNbt.item(stack, player.registryAccess())));
        *///?} else {
        if (mainEnabled)
            for (ItemStack stack : inventory.getNonEquipmentItems())
                snapshot.main.add(NbtCodec.encode(VersionNbt.item(stack, player.registryAccess())));

        if (armorEnabled)
            for (ItemStack stack : equipmentStacks(inventory, 36, 4))
                snapshot.armor.add(
                        NbtCodec.encode(VersionNbt.item(stack, player.registryAccess())));

        if (offhandEnabled)
            for (ItemStack stack : equipmentStacks(inventory, 40, 1))
                snapshot.offhand.add(
                        NbtCodec.encode(VersionNbt.item(stack, player.registryAccess())));
        //?}

        //? if <=1.21.4 {
        /*if (selectedEnabled) snapshot.selectedSlot = inventory.selected;
        *///?} else {
        if (selectedEnabled) snapshot.selectedSlot = inventory.getSelectedSlot();
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

        //? if <=1.20.4 {
        /*List<ItemStack> decodedMain =
                mainEnabled ? decodeList(main, inventory.items.size(), "main") : null;

        List<ItemStack> decodedArmor =
                armorEnabled ? decodeList(armor, inventory.armor.size(), "armor") : null;

        List<ItemStack> decodedOffhand =
                offhandEnabled ? decodeList(offhand, inventory.offhand.size(), "offhand") : null;
        *///?} else if <=1.21.4 {
        /*List<ItemStack> decodedMain =
                mainEnabled
                        ? decodeList(main, inventory.items.size(), "main", player.registryAccess())
                        : null;

        List<ItemStack> decodedArmor =
                armorEnabled
                        ? decodeList(
                                armor, inventory.armor.size(), "armor", player.registryAccess())
                        : null;

        List<ItemStack> decodedOffhand =
                offhandEnabled
                        ? decodeList(
                                offhand,
                                inventory.offhand.size(),
                                "offhand",
                                player.registryAccess())
                        : null;
        *///?} else {
        List<ItemStack> decodedMain =
                mainEnabled
                        ? decodeList(
                                main,
                                inventory.getNonEquipmentItems().size(),
                                "main",
                                player.registryAccess())
                        : null;

        List<ItemStack> decodedArmor =
                armorEnabled ? decodeList(armor, 4, "armor", player.registryAccess()) : null;

        List<ItemStack> decodedOffhand =
                offhandEnabled ? decodeList(offhand, 1, "offhand", player.registryAccess()) : null;
        //?}

        if (selectedEnabled && !snapshotSlotValid(selectedSlot))
            throw new IOException("Invalid selected hotbar slot: " + selectedSlot);

        // Decode and validate every enabled category before mutating a single slot.  A corrupt
        // optional item can therefore never leave a half-restored inventory.
        if (decodedMain != null) applyList(inventory, decodedMain, 0);

        //? if <=1.21.4 {
        /*if (decodedArmor != null) applyList(inventory, decodedArmor, inventory.items.size());

        if (decodedOffhand != null)
            applyList(inventory, decodedOffhand, inventory.items.size() + inventory.armor.size());

        if (selectedEnabled) inventory.selected = selectedSlot;
        *///?} else {
        if (decodedArmor != null)
            applyList(inventory, decodedArmor, inventory.getNonEquipmentItems().size());

        if (decodedOffhand != null)
            applyList(inventory, decodedOffhand, inventory.getNonEquipmentItems().size() + 4);

        if (selectedEnabled) inventory.setSelectedSlot(selectedSlot);
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

        //? if <=1.20.4 {
        /*if (mainEnabled) decodeList(main, inventory.items.size(), "main");

        if (armorEnabled) decodeList(armor, inventory.armor.size(), "armor");

        if (offhandEnabled) decodeList(offhand, inventory.offhand.size(), "offhand");
        *///?} else if <=1.21.4 {
        /*if (mainEnabled) decodeList(main, inventory.items.size(), "main", player.registryAccess());

        if (armorEnabled)
            decodeList(armor, inventory.armor.size(), "armor", player.registryAccess());

        if (offhandEnabled)
            decodeList(offhand, inventory.offhand.size(), "offhand", player.registryAccess());
        *///?} else {
        if (mainEnabled)
            decodeList(
                    main, inventory.getNonEquipmentItems().size(), "main", player.registryAccess());

        if (armorEnabled) decodeList(armor, 4, "armor", player.registryAccess());

        if (offhandEnabled) decodeList(offhand, 1, "offhand", player.registryAccess());
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
        //? if <=1.20.4 {
        /*if (mainEnabled) decodeList(main, 36, "main");
        if (armorEnabled) decodeList(armor, 4, "armor");
        if (offhandEnabled) decodeList(offhand, 1, "offhand");
        *///?} else {
        if (mainEnabled) validateEncodedList(main, 36, "main");
        if (armorEnabled) validateEncodedList(armor, 4, "armor");
        if (offhandEnabled) validateEncodedList(offhand, 1, "offhand");
        //?}

        if (selectedEnabled && !snapshotSlotValid(selectedSlot)) {
            throw new IOException("Invalid selected hotbar slot: " + selectedSlot);
        }
    }

    //? if <=1.20.4 {
    /*private static List<ItemStack> decodeList(
            List<String> encoded, int expectedSize, String category) throws IOException {
    *///?} else {
    private static List<CompoundTag> validateEncodedList(
            List<String> encoded, int expectedSize, String category) throws IOException {
    //?}
        if (encoded == null || encoded.size() != expectedSize) {
            throw new IOException(
                    "Inventory "
                            + category
                            + " entry count is "
                            + (encoded == null ? "null" : encoded.size())
                            + ", expected "
                            + expectedSize);
        }

        //? if <=1.20.4 {
        /*List<ItemStack> decoded = new ArrayList<>(expectedSize);
        *///?} else {
        List<CompoundTag> decoded = new ArrayList<>(expectedSize);
        //?}

        for (String item : encoded) {
            try {
                Tag element = NbtCodec.decode(item);

                if (!(element instanceof CompoundTag compound)) {
                    throw new IOException(
                            "Inventory " + category + " contains a non-compound item");
                }
                // Pre-world inspection cannot resolve dynamic item components. Validate the bounded
                // envelope now; registry-aware decoding must finish before changing any player
                // slot.

                //? if <=1.20.4 {
                /*decoded.add(ItemStack.of(compound));
                *///?} else {
                if (!compound.isEmpty()
                        && (!VersionNbt.contains(compound, "id", 8)
                                || VersionIds.tryParse(VersionNbt.getString(compound, "id")) == null
                                || !VersionNbt.contains(compound, "count", 99)
                                || VersionNbt.getInt(compound, "count") < 1)) {
                    throw new IOException("Invalid component item envelope in " + category);
                }

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
    }

    //? if >=1.20.5 {
    private static List<ItemStack> decodeList(
            List<String> encoded,
            int expectedSize,
            String category,
            net.minecraft.core.HolderLookup.Provider registries)
            throws IOException {
        List<ItemStack> result = new ArrayList<>(expectedSize);

        for (var compound : validateEncodedList(encoded, expectedSize, category)) {
            //? if <=1.21.5 {
            /*if (compound.isEmpty()) result.add(ItemStack.EMPTY);
            else
                result.add(
                        ItemStack.parse(registries, compound)
                                .orElseThrow(() ->
                                        new IOException(
                                                "Unresolvable item/components in " + category)));
            *///?} else {
            if (compound.isEmpty()) result.add(ItemStack.EMPTY);
            else
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
        }

        return result;
    }

    //?}

    private static void applyList(Inventory inventory, List<ItemStack> decoded, int offset) {
        for (int index = 0; index < decoded.size(); index++)
            inventory.setItem(offset + index, decoded.get(index));
    }

    //? if >=1.21.5 {
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
