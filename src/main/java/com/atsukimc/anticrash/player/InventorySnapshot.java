package com.atsukimc.anticrash.player;

//? if (>=1.18 && <=1.21.11) {
import net.minecraft.entity.player.PlayerEntity;
//?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.world.entity.player.Inventory;
*///?}

//? if (>=1.18 && <=1.21.11) {
import net.minecraft.entity.player.PlayerInventory;
//?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.world.entity.player.Player;
*///?}

//? if (>=1.18 && <=1.21.11) {
import net.minecraft.item.ItemStack;
//?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.world.item.ItemStack;
*///?}

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class InventorySnapshot {
    public final List<String> main = new ArrayList<>();
    public final List<String> armor = new ArrayList<>();
    public final List<String> offhand = new ArrayList<>();
    public int selectedSlot;

    //? if (>=1.18 && <=1.21.11) {
    public static InventorySnapshot capture(
            PlayerEntity player,
            boolean mainEnabled,
            boolean armorEnabled,
            boolean offhandEnabled,
            boolean selectedEnabled)
            throws IOException
                //?}
                //? if (>=26.1 && <=26.3) {
                /*public static InventorySnapshot capture(
                            Player player,
                            boolean mainEnabled,
                            boolean armorEnabled,
                            boolean offhandEnabled,
                            boolean selectedEnabled)
                            throws IOException
*///?}
            {
        if (!(mainEnabled || armorEnabled || offhandEnabled || selectedEnabled)) return null;
        InventorySnapshot snapshot = new InventorySnapshot();

        //? if (>=1.18 && <=1.21.11) {
        PlayerInventory inventory = player.getInventory();
        //?}
        //? if (>=26.1 && <=26.3) {
        /*Inventory inventory = player.getInventory();
*///?}

        if (mainEnabled)

            //? if (>=1.18 && <=1.21.4) {
            for (ItemStack stack : inventory.main)

                //?}
                //? if (>=1.18 && <=1.20.4) {
                snapshot.main.add(
                        NbtCodec.encode(stack.writeNbt(new net.minecraft.nbt.NbtCompound())));
        //?}
        //? if (>=1.20.5 && <=1.21.1) {
        /*snapshot.main.add(
                                NbtCodec.encode(stack.encodeAllowEmpty(player.getRegistryManager())));
*///?}
        //? if (>=1.21.2 && <=1.21.4) {
        /*snapshot.main.add(
                                NbtCodec.encode(stack.toNbtAllowEmpty(player.getRegistryManager())));
*///?}
        //? if (>=1.21.5 && <=1.21.11) {
        /*for (ItemStack stack : inventory.getMainStacks())
                        snapshot.main.add(
                                NbtCodec.encode(
                                        com.atsukimc.anticrash.runtime.VersionNbt.item(
                                                stack, player.getRegistryManager())));
*///?}
        //? if (>=26.1 && <=26.3) {
        /*for (ItemStack stack : inventory.getNonEquipmentItems())
                        snapshot.main.add(
                                NbtCodec.encode(
                                        com.atsukimc.anticrash.runtime.VersionNbt.item(
                                                stack, player.registryAccess())));
*///?}

        if (armorEnabled)

            //? if (>=1.18 && <=1.21.4) {
            for (ItemStack stack : inventory.armor)

                //?}
                //? if (>=1.18 && <=1.20.4) {
                snapshot.armor.add(
                        NbtCodec.encode(stack.writeNbt(new net.minecraft.nbt.NbtCompound())));
        //?}
        //? if (>=1.20.5 && <=1.21.1) {
        /*snapshot.armor.add(
                                NbtCodec.encode(stack.encodeAllowEmpty(player.getRegistryManager())));
*///?}
        //? if (>=1.21.2 && <=1.21.4) {
        /*snapshot.armor.add(
                                NbtCodec.encode(stack.toNbtAllowEmpty(player.getRegistryManager())));
*///?}
        //? if (>=1.21.5 && <=26.3) {
        /*for (ItemStack stack : equipmentStacks(inventory, 36, 4))
*///?}
        //? if (>=1.21.5 && <=1.21.11) {
        /*snapshot.armor.add(
                                NbtCodec.encode(
                                        com.atsukimc.anticrash.runtime.VersionNbt.item(
                                                stack, player.getRegistryManager())));
*///?}
        //? if (>=26.1 && <=26.3) {
        /*snapshot.armor.add(
                                NbtCodec.encode(
                                        com.atsukimc.anticrash.runtime.VersionNbt.item(
                                                stack, player.registryAccess())));
*///?}

        if (offhandEnabled)

            //? if (>=1.18 && <=1.21.4) {
            for (ItemStack stack : inventory.offHand)

                //?}
                //? if (>=1.18 && <=1.20.4) {
                snapshot.offhand.add(
                        NbtCodec.encode(stack.writeNbt(new net.minecraft.nbt.NbtCompound())));
        //?}
        //? if (>=1.20.5 && <=1.21.1) {
        /*snapshot.offhand.add(
                                NbtCodec.encode(stack.encodeAllowEmpty(player.getRegistryManager())));
*///?}
        //? if (>=1.21.2 && <=1.21.4) {
        /*snapshot.offhand.add(
                                NbtCodec.encode(stack.toNbtAllowEmpty(player.getRegistryManager())));
*///?}
        //? if (>=1.21.5 && <=26.3) {
        /*for (ItemStack stack : equipmentStacks(inventory, 40, 1))
*///?}
        //? if (>=1.21.5 && <=1.21.11) {
        /*snapshot.offhand.add(
                                NbtCodec.encode(
                                        com.atsukimc.anticrash.runtime.VersionNbt.item(
                                                stack, player.getRegistryManager())));
*///?}
        //? if (>=26.1 && <=26.3) {
        /*snapshot.offhand.add(
                                NbtCodec.encode(
                                        com.atsukimc.anticrash.runtime.VersionNbt.item(
                                                stack, player.registryAccess())));
*///?}

        if (selectedEnabled)
            //? if (>=1.18 && <=1.21.4) {
            snapshot.selectedSlot = inventory.selectedSlot;
        //?}
        //? if (>=1.21.5 && <=26.3) {
        /*snapshot.selectedSlot = inventory.getSelectedSlot();
*///?}

        return snapshot;
    }

    //? if (>=1.18 && <=1.21.11) {
    public boolean restore(
            PlayerEntity player,
            boolean mainEnabled,
            boolean armorEnabled,
            boolean offhandEnabled,
            boolean selectedEnabled)
            throws IOException
                //?}
                //? if (>=26.1 && <=26.3) {
                /*public boolean restore(
                            Player player,
                            boolean mainEnabled,
                            boolean armorEnabled,
                            boolean offhandEnabled,
                            boolean selectedEnabled)
                            throws IOException
*///?}
            {

        //? if (>=1.18 && <=1.21.11) {
        PlayerInventory inventory = player.getInventory();
        //?}
        //? if (>=26.1 && <=26.3) {
        /*Inventory inventory = player.getInventory();
*///?}

        //? if (>=1.18 && <=1.20.4) {
        List<ItemStack> decodedMain =
                mainEnabled ? decodeList(main, inventory.main.size(), "main") : null;
        //?}
        //? if (>=1.20.5 && <=1.21.4) {
        /*List<ItemStack> decodedMain =
                        mainEnabled
                                ? decodeList(
                                        main, inventory.main.size(), "main", player.getRegistryManager())
                                : null;
*///?}
        //? if (>=1.21.5 && <=1.21.11) {
        /*List<ItemStack> decodedMain =
                        mainEnabled
                                ? decodeList(
                                        main,
                                        inventory.getMainStacks().size(),
                                        "main",
                                        player.getRegistryManager())
                                : null;
*///?}
        //? if (>=26.1 && <=26.3) {
        /*List<ItemStack> decodedMain =
                        mainEnabled
                                ? decodeList(
                                        main,
                                        inventory.getNonEquipmentItems().size(),
                                        "main",
                                        player.registryAccess())
                                : null;
*///?}

        //? if (>=1.18 && <=1.20.4) {
        List<ItemStack> decodedArmor =
                armorEnabled ? decodeList(armor, inventory.armor.size(), "armor") : null;
        //?}
        //? if (>=1.20.5 && <=1.21.4) {
        /*List<ItemStack> decodedArmor =
                        armorEnabled
                                ? decodeList(
                                        armor, inventory.armor.size(), "armor", player.getRegistryManager())
                                : null;
*///?}
        //? if (>=1.21.5 && <=1.21.11) {
        /*List<ItemStack> decodedArmor =
                        armorEnabled ? decodeList(armor, 4, "armor", player.getRegistryManager()) : null;
*///?}
        //? if (>=26.1 && <=26.3) {
        /*List<ItemStack> decodedArmor =
                        armorEnabled ? decodeList(armor, 4, "armor", player.registryAccess()) : null;
*///?}

        //? if (>=1.18 && <=1.20.4) {
        List<ItemStack> decodedOffhand =
                offhandEnabled ? decodeList(offhand, inventory.offHand.size(), "offhand") : null;
        //?}
        //? if (>=1.20.5 && <=1.21.4) {
        /*List<ItemStack> decodedOffhand =
                        offhandEnabled
                                ? decodeList(
                                        offhand,
                                        inventory.offHand.size(),
                                        "offhand",
                                        player.getRegistryManager())
                                : null;
*///?}
        //? if (>=1.21.5 && <=1.21.11) {
        /*List<ItemStack> decodedOffhand =
                        offhandEnabled
                                ? decodeList(offhand, 1, "offhand", player.getRegistryManager())
                                : null;
*///?}
        //? if (>=26.1 && <=26.3) {
        /*List<ItemStack> decodedOffhand =
                        offhandEnabled ? decodeList(offhand, 1, "offhand", player.registryAccess()) : null;
*///?}

        if (selectedEnabled && !snapshotSlotValid(selectedSlot))
            throw new IOException("Invalid selected hotbar slot: " + selectedSlot);

        // Decode and validate every enabled category before mutating a single slot.  A corrupt
        // optional item can therefore never leave a half-restored inventory.
        if (decodedMain != null) applyList(inventory, decodedMain, 0);

        //? if (>=1.18 && <=1.21.4) {
        if (decodedArmor != null) applyList(inventory, decodedArmor, inventory.main.size());
        //?}
        //? if (>=1.21.5 && <=26.3) {
        /*if (decodedArmor != null)
*///?}
        //? if (>=1.21.5 && <=1.21.11) {
        /*applyList(inventory, decodedArmor, inventory.getMainStacks().size());
*///?}
        //? if (>=26.1 && <=26.3) {
        /*applyList(inventory, decodedArmor, inventory.getNonEquipmentItems().size());
*///?}

        if (decodedOffhand != null)

            //? if (>=1.18 && <=1.21.4) {
            applyList(inventory, decodedOffhand, inventory.main.size() + inventory.armor.size());
        //?}
        //? if (>=1.21.5 && <=1.21.11) {
        /*applyList(inventory, decodedOffhand, inventory.getMainStacks().size() + 4);
*///?}
        //? if (>=26.1 && <=26.3) {
        /*applyList(inventory, decodedOffhand, inventory.getNonEquipmentItems().size() + 4);
*///?}

        if (selectedEnabled)
            //? if (>=1.18 && <=1.21.4) {
            inventory.selectedSlot = selectedSlot;
        //?}
        //? if (>=1.21.5 && <=26.3) {
        /*inventory.setSelectedSlot(selectedSlot);
*///?}

        boolean changed = mainEnabled || armorEnabled || offhandEnabled || selectedEnabled;
        if (changed)
            //? if (>=1.18 && <=1.21.11) {
            inventory.markDirty();
        //?}
        //? if (>=26.1 && <=26.3) {
        /*inventory.setChanged();
*///?}

        return changed;
    }

    /** Decodes every enabled slot without mutating the live inventory. */

    //? if (>=1.18 && <=1.21.11) {
    public void validate(
            PlayerEntity player,
            boolean mainEnabled,
            boolean armorEnabled,
            boolean offhandEnabled,
            boolean selectedEnabled)
            throws IOException
                //?}
                //? if (>=26.1 && <=26.3) {
                /*public void validate(
                            Player player,
                            boolean mainEnabled,
                            boolean armorEnabled,
                            boolean offhandEnabled,
                            boolean selectedEnabled)
                            throws IOException
*///?}
            {

        //? if (>=1.18 && <=1.21.11) {
        PlayerInventory inventory = player.getInventory();
        //?}
        //? if (>=26.1 && <=26.3) {
        /*Inventory inventory = player.getInventory();
*///?}

        //? if (>=1.18 && <=1.20.4) {
        if (mainEnabled) decodeList(main, inventory.main.size(), "main");
        //?}
        //? if (>=1.20.5 && <=26.3) {
        /*if (mainEnabled)
*///?}
        //? if (>=1.20.5 && <=1.21.4) {
        /*decodeList(main, inventory.main.size(), "main", player.getRegistryManager());
*///?}
        //? if (>=1.21.5 && <=1.21.11) {
        /*decodeList(main, inventory.getMainStacks().size(), "main", player.getRegistryManager());
*///?}
        //? if (>=26.1 && <=26.3) {
        /*decodeList(
                            main, inventory.getNonEquipmentItems().size(), "main", player.registryAccess());
*///?}

        //? if (>=1.18 && <=1.20.4) || (>=1.21.5 && <=26.3) {
        if (armorEnabled)
            //?}
            //? if (>=1.18 && <=1.20.4) {
            decodeList(armor, inventory.armor.size(), "armor");
        //?}
        //? if (>=1.20.5 && <=1.21.4) {
        /*if (armorEnabled)
                    decodeList(armor, inventory.armor.size(), "armor", player.getRegistryManager());
*///?}
        //? if (>=1.21.5 && <=1.21.11) {
        /*decodeList(armor, 4, "armor", player.getRegistryManager());
*///?}
        //? if (>=26.1 && <=26.3) {
        /*decodeList(armor, 4, "armor", player.registryAccess());
*///?}

        //? if (>=1.18 && <=1.20.4) || (>=1.21.5 && <=26.3) {
        if (offhandEnabled)
            //?}
            //? if (>=1.18 && <=1.20.4) {
            decodeList(offhand, inventory.offHand.size(), "offhand");
        //?}
        //? if (>=1.20.5 && <=1.21.4) {
        /*if (offhandEnabled)
                    decodeList(offhand, inventory.offHand.size(), "offhand", player.getRegistryManager());
*///?}
        //? if (>=1.21.5 && <=1.21.11) {
        /*decodeList(offhand, 1, "offhand", player.getRegistryManager());
*///?}
        //? if (>=26.1 && <=26.3) {
        /*decodeList(offhand, 1, "offhand", player.registryAccess());
*///?}

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
        if (mainEnabled)
            //? if (>=1.18 && <=1.20.4) {
            decodeList(main, 36, "main");
        //?}
        //? if (>=1.20.5 && <=26.3) {
        /*validateEncodedList(main, 36, "main");
*///?}

        if (armorEnabled)
            //? if (>=1.18 && <=1.20.4) {
            decodeList(armor, 4, "armor");
        //?}
        //? if (>=1.20.5 && <=26.3) {
        /*validateEncodedList(armor, 4, "armor");
*///?}

        if (offhandEnabled)
            //? if (>=1.18 && <=1.20.4) {
            decodeList(offhand, 1, "offhand");
        //?}
        //? if (>=1.20.5 && <=26.3) {
        /*validateEncodedList(offhand, 1, "offhand");
*///?}

        if (selectedEnabled && !snapshotSlotValid(selectedSlot)) {
            throw new IOException("Invalid selected hotbar slot: " + selectedSlot);
        }
    }

    //? if (>=1.18 && <=1.20.4) {
    private static List<ItemStack> decodeList(
            List<String> encoded, int expectedSize, String category) throws IOException
                //?}
                //? if (>=1.20.5 && <=1.21.11) {
                /*private static List<net.minecraft.nbt.NbtCompound> validateEncodedList(
                            List<String> encoded, int expectedSize, String category) throws IOException
*///?}
                //? if (>=26.1 && <=26.3) {
                /*private static List<net.minecraft.nbt.CompoundTag> validateEncodedList(
                            List<String> encoded, int expectedSize, String category) throws IOException
*///?}
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
        List<ItemStack> decoded = new ArrayList<>(expectedSize);
        //?}
        //? if (>=1.20.5 && <=1.21.11) {
        /*List<net.minecraft.nbt.NbtCompound> decoded = new ArrayList<>(expectedSize);
*///?}
        //? if (>=26.1 && <=26.3) {
        /*List<net.minecraft.nbt.CompoundTag> decoded = new ArrayList<>(expectedSize);
*///?}

        for (String item : encoded) {
            try {

                //? if (>=1.18 && <=1.21.11) {
                net.minecraft.nbt.NbtElement element = NbtCodec.decode(item);
                //?}
                //? if (>=26.1 && <=26.3) {
                /*net.minecraft.nbt.Tag element = NbtCodec.decode(item);
*///?}

                //? if (>=1.18 && <=1.21.11) {
                if (!(element instanceof net.minecraft.nbt.NbtCompound compound))
                //?}
                //? if (>=26.1 && <=26.3) {
                /*if (!(element instanceof net.minecraft.nbt.CompoundTag compound))
*///?}
                {
                    throw new IOException(
                            "Inventory " + category + " contains a non-compound item");
                }
                // Pre-world inspection cannot resolve dynamic item components. Validate the bounded
                // envelope now; registry-aware decoding must finish before changing any player
                // slot.

                //? if (>=1.20.5 && <=1.21.4) {
                /*if (!compound.isEmpty()
                                        && (!compound.contains("id", 8)
                                                || net.minecraft.util.Identifier.tryParse(compound.getString("id"))
                                                        == null
                                                || !compound.contains("count", 99)
                                                || compound.getInt("count") < 1))
*///?}
                //? if (>=1.21.5 && <=1.21.11) {
                /*if (!compound.isEmpty()
                                        && (!com.atsukimc.anticrash.runtime.VersionNbt.contains(compound, "id", 8)
                                                || net.minecraft.util.Identifier.tryParse(
                                                                compound.getString("id").orElseThrow())
                                                        == null
                                                || !com.atsukimc.anticrash.runtime.VersionNbt.contains(
                                                        compound, "count", 99)
                                                || compound.getInt("count").orElseThrow() < 1))
*///?}
                //? if (>=26.1 && <=26.3) {
                /*if (!compound.isEmpty()
                                        && (!com.atsukimc.anticrash.runtime.VersionNbt.contains(compound, "id", 8)
                                                || net.minecraft.resources.Identifier.tryParse(
                                                                compound.getString("id").orElseThrow())
                                                        == null
                                                || !com.atsukimc.anticrash.runtime.VersionNbt.contains(
                                                        compound, "count", 99)
                                                || compound.getInt("count").orElseThrow() < 1))
*///?}
                //? if (>=1.20.5 && <=26.3) {
                /*{
                                    throw new IOException("Invalid component item envelope in " + category);
                                }
*///?}

                //? if (>=1.18 && <=1.20.4) {
                decoded.add(ItemStack.fromNbt(compound));
                //?}
                //? if (>=1.20.5 && <=26.3) {
                /*decoded.add(compound);
*///?}

            } catch (IOException exception) {
                throw exception;
            } catch (Exception exception) {
                throw new IOException(
                        "Inventory " + category + " contains malformed item NBT", exception);
            }
        }
        return decoded;
        //? if (>=1.20.5 && <=26.3) {

        /*}
*///?}

        //? if (>=1.20.5 && <=1.21.11) {
        /*private static List<ItemStack> decodeList(
                    List<String> encoded,
                    int expectedSize,
                    String category,
                    net.minecraft.registry.RegistryWrapper.WrapperLookup registries)
                    throws IOException
*///?}
        //? if (>=26.1 && <=26.3) {
        /*private static List<ItemStack> decodeList(
                    List<String> encoded,
                    int expectedSize,
                    String category,
                    net.minecraft.core.HolderLookup.Provider registries)
                    throws IOException
*///?}
        //? if (>=1.20.5 && <=26.3) {
        /*{
                List<ItemStack> result = new ArrayList<>(expectedSize);
*///?}

        //? if (>=1.20.5 && <=26.3) {
        /*for (var compound : validateEncodedList(encoded, expectedSize, category)) {
                    if (compound.isEmpty()) result.add(ItemStack.EMPTY);
                    else
*///?}
        //? if (>=1.20.5 && <=1.21.5) {
        /*result.add(
                                ItemStack.fromNbt(registries, compound)
                                        .orElseThrow(() ->
                                                new IOException(
                                                        "Unresolvable item/components in " + category)));
*///?}
        //? if (>=1.21.6 && <=1.21.11) {
        /*result.add(
                                ItemStack.CODEC
                                        .parse(
                                                registries.getOps(net.minecraft.nbt.NbtOps.INSTANCE),
                                                compound)
                                        .result()
                                        .orElseThrow(() ->
                                                new IOException(
                                                        "Unresolvable item/components in " + category)));
*///?}
        //? if (>=26.1 && <=26.3) {
        /*result.add(
                                ItemStack.CODEC
                                        .parse(
                                                registries.createSerializationContext(
                                                        net.minecraft.nbt.NbtOps.INSTANCE),
                                                compound)
                                        .result()
                                        .orElseThrow(() ->
                                                new IOException(
                                                        "Unresolvable item/components in " + category)));
*///?}
        //? if (>=1.20.5 && <=26.3) {

        /*}
*///?}

        //? if (>=1.20.5 && <=26.3) {
        /*return result;
*///?}

    }

    //? if (>=1.18 && <=1.21.11) {
    private static void applyList(PlayerInventory inventory, List<ItemStack> decoded, int offset)
                //?}
                //? if (>=26.1 && <=26.3) {
                /*private static void applyList(Inventory inventory, List<ItemStack> decoded, int offset)
*///?}
            {
        for (int index = 0; index < decoded.size(); index++)

            //? if (>=1.18 && <=1.21.11) {
            inventory.setStack(offset + index, decoded.get(index));
        //?}
        //? if (>=26.1 && <=26.3) {
        /*inventory.setItem(offset + index, decoded.get(index));
*///?}

    }

    //? if (>=1.21.5 && <=1.21.11) {
    /*private static List<ItemStack> equipmentStacks(
                PlayerInventory inventory, int offset, int count)
*///?}
    //? if (>=26.1 && <=26.3) {
    /*private static List<ItemStack> equipmentStacks(Inventory inventory, int offset, int count)
*///?}
    //? if (>=1.21.5 && <=26.3) {
    /*{
            List<ItemStack> stacks = new ArrayList<>(count);
*///?}

    //? if (>=1.21.5 && <=26.3) {
    /*for (int index = 0; index < count; index++)
*///?}
    //? if (>=1.21.5 && <=1.21.11) {
    /*stacks.add(inventory.getStack(offset + index));
*///?}
    //? if (>=26.1 && <=26.3) {
    /*stacks.add(inventory.getItem(offset + index));
*///?}

    //? if (>=1.21.5 && <=26.3) {
    /*return stacks;
        }
*///?}

    private static boolean snapshotSlotValid(int slot) {
        return slot >= 0 && slot < 9;
    }
}
