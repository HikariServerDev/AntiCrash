package com.atsukimc.anticrash.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Base64;

class NbtCodecTest {
    @Test
    void emptyRootRoundTripsAndTrailingBytesAreRejected() throws Exception {
        NbtElement empty = NbtCodec.decode("AA==");
        assertEquals(0, empty.getType());
        assertEquals("AA==", NbtCodec.encode(empty));
        assertThrows(IOException.class, () -> NbtCodec.decode("AAA="));
    }

    @Test
    void roundTripsAListRootUsedByEnderChestSnapshots() throws Exception {
        NbtList list = new NbtList();
        list.add(NbtString.of("slot"));

        NbtList decoded = assertInstanceOf(NbtList.class, NbtCodec.decode(NbtCodec.encode(list)));

        assertEquals("slot", decoded.getString(0).orElseThrow());
    }

    @Test
    void hostileArrayLengthIsRejectedBeforeAHeapSizedAllocation() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeByte(NbtElement.BYTE_ARRAY_TYPE);
            output.writeUTF("");
            output.writeInt(Integer.MAX_VALUE);
        }
        String encoded = Base64.getEncoder().encodeToString(bytes.toByteArray());

        assertThrows(IOException.class, () -> NbtCodec.decode(encoded));
    }
}
