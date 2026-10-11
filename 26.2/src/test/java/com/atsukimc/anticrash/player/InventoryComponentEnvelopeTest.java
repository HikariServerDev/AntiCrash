package com.atsukimc.anticrash.player;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import net.minecraft.nbt.CompoundTag;

import static org.junit.jupiter.api.Assertions.*;


class InventoryComponentEnvelopeTest {
    private InventorySnapshot empty() throws IOException {
        var snapshot = new InventorySnapshot();
        for (int i = 0; i < 36; i++) snapshot.main.add(NbtCodec.encode(new CompoundTag()));
        return snapshot;
    }

    @Test void preWorldValidationKeepsComponentPayloadWithoutNeedingLiveRegistries() throws Exception {
        var snapshot = empty();
        CompoundTag item = new CompoundTag();
        item.putString("id", "minecraft:stone"); item.putInt("count", 1);
        CompoundTag components = new CompoundTag();
        CompoundTag custom = new CompoundTag(); custom.putString("machine", "test");
        components.put("minecraft:custom_data", custom); item.put("components", components);
        snapshot.main.set(0, NbtCodec.encode(item));
        String original = snapshot.main.get(0);
        snapshot.validatePortable(true, false, false, false);
        assertEquals(item, NbtCodec.decode(original));
        assertEquals(original, snapshot.main.get(0));
    }

    @Test void malformedOrLegacyEnvelopeCannotPassAsAComponentInventory() throws Exception {
        var snapshot = empty();
        CompoundTag item = new CompoundTag(); item.putString("id", "minecraft:stone");
        item.putByte("Count", (byte) 1); // legacy format is not this Minecraft version
        snapshot.main.set(0, NbtCodec.encode(item));
        assertThrows(IOException.class, () -> snapshot.validatePortable(true, false, false, false));
        item.putInt("count", 0); snapshot.main.set(0, NbtCodec.encode(item));
        assertThrows(IOException.class, () -> snapshot.validatePortable(true, false, false, false));
    }
}
