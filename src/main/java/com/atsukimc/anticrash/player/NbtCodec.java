package com.atsukimc.anticrash.player;

//? if (>=1.18 && <=1.21.11) {
import net.minecraft.nbt.CompoundTag;
//?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.nbt.CompoundTag;
*///?}

//? if (>=1.18 && <=1.21.11) {
import net.minecraft.nbt.Tag;
//?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.nbt.EndTag;
*///?}

//? if (>=1.18.1 && <=1.21.11) {
import net.minecraft.nbt.EndTag;
//?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.nbt.NbtAccounter;
*///?}

//? if 1.18 || (>=1.20.4 && <=26.3) {
/*import net.minecraft.nbt.NbtIo;
*///?}

//? if (>=1.18.1 && <=1.20.3) {
import net.minecraft.nbt.NbtIo;
//?}
//? if 1.18 {
/*import net.minecraft.nbt.EndTag;
*///?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.nbt.Tag;
*///?}

//? if (>=1.18 && <=1.20.3) {
import net.minecraft.nbt.NbtAccounter;
//?}
//? if (>=1.20.4 && <=1.21.11) {
/*import net.minecraft.nbt.NbtAccounter;
*///?}

//? if (>=1.18 && <=1.21.11) {
import net.minecraft.nbt.TagTypes;
//?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.nbt.TagTypes;
*///?}

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Base64;

public final class NbtCodec {
    private static final int MAX_NBT_BYTES = 16 * 1024 * 1024;
    private static final int MAX_ENCODED_CHARS = ((MAX_NBT_BYTES + 2) / 3) * 4;

    private NbtCodec() {}

    //? if (>=1.18 && <=1.21.11) {
    public static String encode(Tag element) throws IOException
                //?}
                //? if (>=26.1 && <=26.3) {
                /*public static String encode(Tag element) throws IOException
*///?}
            {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {

            //? if (>=1.18 && <=1.21.11) {
            //? if (>=1.18 && <=1.20.2) {
            NbtIo.writeUnnamedTag(element, output);
            //?}
            //? if (>=1.20.3 && <=1.21.11) {
            /*NbtIo.writeUnnamedTagWithFallback(element, output);
            *///?}
            //?}
            //? if (>=26.1 && <=26.3) {
            /*NbtIo.writeUnnamedTagWithFallback(element, output);
*///?}

        }
        if (bytes.size() > MAX_NBT_BYTES) throw new IOException("NBT snapshot exceeds 16 MiB");
        return Base64.getEncoder().encodeToString(bytes.toByteArray());
    }

    //? if (>=1.18 && <=1.21.11) {
    public static Tag decode(String encoded) throws IOException
                //?}
                //? if (>=26.1 && <=26.3) {
                /*public static Tag decode(String encoded) throws IOException
*///?}
            {
        if (encoded == null || encoded.isBlank()) return null;
        if (encoded.length() > MAX_ENCODED_CHARS)
            throw new IOException("Encoded NBT exceeds 16 MiB");
        final byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(encoded);
        } catch (IllegalArgumentException exception) {
            throw new IOException("Encoded NBT is not valid Base64", exception);
        }
        if (bytes.length > MAX_NBT_BYTES) throw new IOException("Decoded NBT exceeds 16 MiB");
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes))) {
            int type = input.readUnsignedByte();

            //? if (>=1.18.1 && <=1.21.11) {
            if (type == Tag.TAG_END)
            //?}
            //? if 1.18 {
            /*if (type == Tag.TAG_END)
*///?}
            //? if (>=26.1 && <=26.3) {
            /*if (type == Tag.TAG_END)
*///?}
            {
                if (input.available() != 0)
                    throw new IOException("Encoded NBT contains trailing bytes");

                //? if (>=1.18.1 && <=1.21.11) {
                return EndTag.INSTANCE;
                //?}
                //? if 1.18 {
                /*return EndTag.INSTANCE;
*///?}
                //? if (>=26.1 && <=26.3) {
                /*return EndTag.INSTANCE;
*///?}

            }
            input.readUTF(); // root name; encode() writes the empty name used by Vanilla NbtIo

            //? if (>=1.18 && <=1.21.11) {
            final Tag element;
            //?}
            //? if (>=26.1 && <=26.3) {
            /*final Tag element;
*///?}

            try {

                //? if (>=1.18 && <=1.20.1) {
                element = TagTypes.getType(type).load(input, 0, new NbtAccounter(MAX_NBT_BYTES));
                //?}
                //? if (>=1.20.2 && <=1.20.3) {
                /*element = TagTypes.getType(type).load(input, NbtAccounter.create(MAX_NBT_BYTES));
*///?}
                //? if (>=1.20.4 && <=1.21.11) {
                /*element = TagTypes.getType(type).load(input, NbtAccounter.create(MAX_NBT_BYTES));
*///?}
                //? if (>=26.1 && <=26.3) {
                /*element = TagTypes.getType(type).load(input, NbtAccounter.create(MAX_NBT_BYTES));
*///?}

            } catch (RuntimeException exception) {
                throw new IOException("NBT exceeds its allocation or structural limit", exception);
            }
            if (input.available() != 0)
                throw new IOException("Encoded NBT contains trailing bytes");
            return element;
        }
    }

    //? if (>=1.18 && <=1.21.11) {
    public static CompoundTag decodeCompound(String encoded) throws IOException
                //?}
                //? if (>=26.1 && <=26.3) {
                /*public static CompoundTag decodeCompound(String encoded) throws IOException
*///?}
            {

        //? if (>=1.18 && <=1.21.11) {
        Tag element = decode(encoded);
        //?}
        //? if (>=26.1 && <=26.3) {
        /*Tag element = decode(encoded);
*///?}

        //? if (>=1.18 && <=1.21.11) {
        if (!(element instanceof CompoundTag compound))

            //?}
            //? if (>=26.1 && <=26.3) {
            /*if (!(element instanceof CompoundTag compound))
*///?}
            throw new IOException("Encoded NBT is not a compound");
        return compound;
    }
}
