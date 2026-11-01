package com.atsukimc.anticrash.player;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class InventorySnapshot {
    public final List<String> main = new ArrayList<>();
    public final List<String> armor = new ArrayList<>();
    public final List<String> offhand = new ArrayList<>();
    public int selectedSlot;

    public static InventorySnapshot capture(
            PlayerEntity player,
            boolean mainEnabled,
            boolean armorEnabled,
            boolean offhandEnabled,
            boolean selectedEnabled)
            throws IOException {
        if (!(mainEnabled || armorEnabled || offhandEnabled || selectedEnabled)) return null;
        InventorySnapshot snapshot = new InventorySnapshot();
        PlayerInventory inventory = player.getInventory();
        if (mainEnabled)
            for (ItemStack stack : inventory.main)
                snapshot.main.add(
                        NbtCodec.encode(stack.writeNbt(new net.minecraft.nbt.NbtCompound())));
        if (armorEnabled)
            for (ItemStack stack : inventory.armor)
                snapshot.armor.add(
                        NbtCodec.encode(stack.writeNbt(new net.minecraft.nbt.NbtCompound())));
        if (offhandEnabled)
            for (ItemStack stack : inventory.offHand)
                snapshot.offhand.add(
                        NbtCodec.encode(stack.writeNbt(new net.minecraft.nbt.NbtCompound())));
        if (selectedEnabled) snapshot.selectedSlot = inventory.selectedSlot;
        return snapshot;
    }

    public boolean restore(
            PlayerEntity player,
            boolean mainEnabled,
            boolean armorEnabled,
            boolean offhandEnabled,
            boolean selectedEnabled)
            throws IOException {
        PlayerInventory inventory = player.getInventory();
        List<ItemStack> decodedMain =
                mainEnabled ? decodeList(main, inventory.main.size(), "main") : null;
        List<ItemStack> decodedArmor =
                armorEnabled ? decodeList(armor, inventory.armor.size(), "armor") : null;
        List<ItemStack> decodedOffhand =
                offhandEnabled ? decodeList(offhand, inventory.offHand.size(), "offhand") : null;
        if (selectedEnabled && !snapshotSlotValid(selectedSlot))
            throw new IOException("Invalid selected hotbar slot: " + selectedSlot);

        // Decode and validate every enabled category before mutating a single slot.  A corrupt
        // optional item can therefore never leave a half-restored inventory.
        if (decodedMain != null) applyList(inventory, decodedMain, 0);
        if (decodedArmor != null) applyList(inventory, decodedArmor, inventory.main.size());
        if (decodedOffhand != null)
            applyList(inventory, decodedOffhand, inventory.main.size() + inventory.armor.size());
        if (selectedEnabled) inventory.selectedSlot = selectedSlot;
        boolean changed = mainEnabled || armorEnabled || offhandEnabled || selectedEnabled;
        if (changed) inventory.markDirty();
        return changed;
    }

    /** Decodes every enabled slot without mutating the live inventory. */
    public void validate(
            PlayerEntity player,
            boolean mainEnabled,
            boolean armorEnabled,
            boolean offhandEnabled,
            boolean selectedEnabled)
            throws IOException {
        PlayerInventory inventory = player.getInventory();
        if (mainEnabled) decodeList(main, inventory.main.size(), "main");
        if (armorEnabled) decodeList(armor, inventory.armor.size(), "armor");
        if (offhandEnabled) decodeList(offhand, inventory.offHand.size(), "offhand");
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
        if (mainEnabled) decodeList(main, 36, "main");
        if (armorEnabled) decodeList(armor, 4, "armor");
        if (offhandEnabled) decodeList(offhand, 1, "offhand");
        if (selectedEnabled && !snapshotSlotValid(selectedSlot)) {
            throw new IOException("Invalid selected hotbar slot: " + selectedSlot);
        }
    }

    private static List<ItemStack> decodeList(
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
        List<ItemStack> decoded = new ArrayList<>(expectedSize);
        for (String item : encoded) {
            try {
                net.minecraft.nbt.NbtElement element = NbtCodec.decode(item);
                if (!(element instanceof net.minecraft.nbt.NbtCompound compound)) {
                    throw new IOException(
                            "Inventory " + category + " contains a non-compound item");
                }
                decoded.add(ItemStack.fromNbt(compound));
            } catch (IOException exception) {
                throw exception;
            } catch (Exception exception) {
                throw new IOException(
                        "Inventory " + category + " contains malformed item NBT", exception);
            }
        }
        return decoded;
    }

    private static void applyList(PlayerInventory inventory, List<ItemStack> decoded, int offset) {
        for (int index = 0; index < decoded.size(); index++)
            inventory.setStack(offset + index, decoded.get(index));
    }

    private static boolean snapshotSlotValid(int slot) {
        return slot >= 0 && slot < 9;
    }
}
