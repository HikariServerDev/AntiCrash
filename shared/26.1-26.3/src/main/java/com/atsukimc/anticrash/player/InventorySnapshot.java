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
            for (ItemStack stack : inventory.getNonEquipmentItems())
                snapshot.main.add(
                        NbtCodec.encode(
                                com.atsukimc.anticrash.runtime.VersionNbt.item(
                                        stack, player.registryAccess())));
        if (armorEnabled)
            for (ItemStack stack : equipmentStacks(inventory, 36, 4))
                snapshot.armor.add(
                        NbtCodec.encode(
                                com.atsukimc.anticrash.runtime.VersionNbt.item(
                                        stack, player.registryAccess())));
        if (offhandEnabled)
            for (ItemStack stack : equipmentStacks(inventory, 40, 1))
                snapshot.offhand.add(
                        NbtCodec.encode(
                                com.atsukimc.anticrash.runtime.VersionNbt.item(
                                        stack, player.registryAccess())));
        if (selectedEnabled) snapshot.selectedSlot = inventory.getSelectedSlot();
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
        if (selectedEnabled && !snapshotSlotValid(selectedSlot))
            throw new IOException("Invalid selected hotbar slot: " + selectedSlot);

        // Decode and validate every enabled category before mutating a single slot.  A corrupt
        // optional item can therefore never leave a half-restored inventory.
        if (decodedMain != null) applyList(inventory, decodedMain, 0);
        if (decodedArmor != null)
            applyList(inventory, decodedArmor, inventory.getNonEquipmentItems().size());
        if (decodedOffhand != null)
            applyList(inventory, decodedOffhand, inventory.getNonEquipmentItems().size() + 4);
        if (selectedEnabled) inventory.setSelectedSlot(selectedSlot);
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
        if (mainEnabled)
            decodeList(
                    main, inventory.getNonEquipmentItems().size(), "main", player.registryAccess());
        if (armorEnabled) decodeList(armor, 4, "armor", player.registryAccess());
        if (offhandEnabled) decodeList(offhand, 1, "offhand", player.registryAccess());
        if (selectedEnabled && !snapshotSlotValid(selectedSlot)) {
            throw new IOException("Invalid selected hotbar slot: " + selectedSlot);
        }
    }

    /** Validates the fixed Vanilla 1.18.2 PlayerInventory shape without a live player. */
    public void validatePortable(
            boolean mainEnabled,
            boolean armorEnabled,
            boolean offhandEnabled,
            boolean selectedEnabled)
            throws IOException {
        if (mainEnabled) validateEncodedList(main, 36, "main");
        if (armorEnabled) validateEncodedList(armor, 4, "armor");
        if (offhandEnabled) validateEncodedList(offhand, 1, "offhand");
        if (selectedEnabled && !snapshotSlotValid(selectedSlot)) {
            throw new IOException("Invalid selected hotbar slot: " + selectedSlot);
        }
    }

    private static List<net.minecraft.nbt.CompoundTag> validateEncodedList(
            List<String> encoded, int expectedSize, String category) throws IOException {
        if (encoded == null || encoded.size() != expectedSize) {
            throw new IOException(
                    "Inventory "
                            + category
                            + " entry count is "
                            + (encoded == null ? "null" : encoded.size())
                            + ", expected "
                            + expectedSize);
        }
        List<net.minecraft.nbt.CompoundTag> decoded = new ArrayList<>(expectedSize);
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
                if (!compound.isEmpty()
                        && (!com.atsukimc.anticrash.runtime.VersionNbt.contains(compound, "id", 8)
                                || net.minecraft.resources.Identifier.tryParse(
                                                compound.getString("id").orElseThrow())
                                        == null
                                || !com.atsukimc.anticrash.runtime.VersionNbt.contains(
                                        compound, "count", 99)
                                || compound.getInt("count").orElseThrow() < 1)) {
                    throw new IOException("Invalid component item envelope in " + category);
                }
                decoded.add(compound);
            } catch (IOException exception) {
                throw exception;
            } catch (Exception exception) {
                throw new IOException(
                        "Inventory " + category + " contains malformed item NBT", exception);
            }
        }
        return decoded;
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
        }
        return result;
    }

    private static void applyList(Inventory inventory, List<ItemStack> decoded, int offset) {
        for (int index = 0; index < decoded.size(); index++)
            inventory.setItem(offset + index, decoded.get(index));
    }

    private static List<ItemStack> equipmentStacks(Inventory inventory, int offset, int count) {
        List<ItemStack> stacks = new ArrayList<>(count);
        for (int index = 0; index < count; index++) stacks.add(inventory.getItem(offset + index));
        return stacks;
    }

    private static boolean snapshotSlotValid(int slot) {
        return slot >= 0 && slot < 9;
    }
}
