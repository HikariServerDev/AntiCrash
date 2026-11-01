package com.atsukimc.anticrash.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Base64;

class NbtCodecTest {
    @Test
    void emptyRootRoundTripsAndTrailingBytesAreRejected() throws Exception {
        Tag empty = NbtCodec.decode("AA==");
        assertEquals(0, empty.getId());
        assertEquals("AA==", NbtCodec.encode(empty));
        assertThrows(IOException.class, () -> NbtCodec.decode("AAA="));
    }

    @Test
    void roundTripsAListRootUsedByEnderChestSnapshots() throws Exception {
        ListTag list = new ListTag();
        list.add(StringTag.valueOf("slot"));

        ListTag decoded = assertInstanceOf(ListTag.class, NbtCodec.decode(NbtCodec.encode(list)));

        assertEquals("slot", decoded.getString(0).orElseThrow());
    }

    @Test
    void hostileArrayLengthIsRejectedBeforeAHeapSizedAllocation() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeByte(Tag.TAG_BYTE_ARRAY);
            output.writeUTF("");
            output.writeInt(Integer.MAX_VALUE);
        }
        String encoded = Base64.getEncoder().encodeToString(bytes.toByteArray());

        assertThrows(IOException.class, () -> NbtCodec.decode(encoded));
    }
}
