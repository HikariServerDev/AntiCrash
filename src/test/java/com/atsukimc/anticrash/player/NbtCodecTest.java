package com.atsukimc.anticrash.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

//? if (>=1.18 && <=1.21.11) {
/*import net.minecraft.nbt.Tag;
*///?}
//? if (>=26.1 && <=26.3) {
import net.minecraft.nbt.ListTag;
//?}

//? if (>=1.18 && <=1.21.11) {
/*import net.minecraft.nbt.ListTag;
*///?}
//? if (>=26.1 && <=26.3) {
import net.minecraft.nbt.StringTag;
//?}

//? if (>=1.18 && <=1.21.11) {
/*import net.minecraft.nbt.StringTag;
*///?}
//? if (>=26.1 && <=26.3) {
import net.minecraft.nbt.Tag;
//?}

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Base64;

class NbtCodecTest {
    @Test
    void emptyRootRoundTripsAndTrailingBytesAreRejected() throws Exception {

        //? if (>=1.18 && <=1.21.11) {
        /*Tag empty = NbtCodec.decode("AA==");
        *///?} else if (>=26.1 && <=26.3) {
        Tag empty = NbtCodec.decode("AA==");
        //?}

        //? if (>=1.18 && <=1.21.11) {
        /*assertEquals(0, empty.getId());
        *///?} else if (>=26.1 && <=26.3) {
        assertEquals(0, empty.getId());
        //?}

        assertEquals("AA==", NbtCodec.encode(empty));
        assertThrows(IOException.class, () -> NbtCodec.decode("AAA="));
    }

    @Test
    void roundTripsAListRootUsedByEnderChestSnapshots() throws Exception {

        //? if (>=1.18 && <=1.21.11) {
        /*ListTag list = new ListTag();
        *///?} else if (>=26.1 && <=26.3) {
        ListTag list = new ListTag();
        //?}

        //? if (>=1.18 && <=1.21.11) {
        /*list.add(StringTag.valueOf("slot"));
        *///?} else if (>=26.1 && <=26.3) {
        list.add(StringTag.valueOf("slot"));
        //?}

        //? if (>=1.18 && <=1.21.11) {
        /*ListTag decoded = assertInstanceOf(ListTag.class, NbtCodec.decode(NbtCodec.encode(list)));
        *///?} else if (>=26.1 && <=26.3) {
        ListTag decoded = assertInstanceOf(ListTag.class, NbtCodec.decode(NbtCodec.encode(list)));
        //?}

        //? if (>=1.18 && <=1.21.4) {
        /*assertEquals("slot", decoded.getString(0));
        *///?} else if (>=1.21.5 && <=26.3) {
        assertEquals("slot", decoded.getString(0).orElseThrow());
        //?}

    }

    @Test
    void hostileArrayLengthIsRejectedBeforeAHeapSizedAllocation() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {

            //? if (>=1.18 && <=1.21.11) {
            /*output.writeByte(Tag.TAG_BYTE_ARRAY);
            *///?} else if (>=26.1 && <=26.3) {
            output.writeByte(Tag.TAG_BYTE_ARRAY);
            //?}

            output.writeUTF("");
            output.writeInt(Integer.MAX_VALUE);
        }
        String encoded = Base64.getEncoder().encodeToString(bytes.toByteArray());

        assertThrows(IOException.class, () -> NbtCodec.decode(encoded));
    }
}
