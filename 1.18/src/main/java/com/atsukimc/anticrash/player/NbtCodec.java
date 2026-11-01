package com.atsukimc.anticrash.player;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtNull;
import net.minecraft.nbt.NbtTagSizeTracker;
import net.minecraft.nbt.NbtTypes;

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

    public static String encode(NbtElement element) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            NbtIo.write(element, output);
        }
        if (bytes.size() > MAX_NBT_BYTES) throw new IOException("NBT snapshot exceeds 16 MiB");
        return Base64.getEncoder().encodeToString(bytes.toByteArray());
    }

    public static NbtElement decode(String encoded) throws IOException {
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
            if (type == NbtElement.NULL_TYPE) {
                if (input.available() != 0)
                    throw new IOException("Encoded NBT contains trailing bytes");
                return NbtNull.INSTANCE;
            }
            input.readUTF(); // root name; encode() writes the empty name used by Vanilla NbtIo
            final NbtElement element;
            try {
                element = NbtTypes.byId(type).read(input, 0, new NbtTagSizeTracker(MAX_NBT_BYTES));
            } catch (RuntimeException exception) {
                throw new IOException("NBT exceeds its allocation or structural limit", exception);
            }
            if (input.available() != 0)
                throw new IOException("Encoded NBT contains trailing bytes");
            return element;
        }
    }

    public static NbtCompound decodeCompound(String encoded) throws IOException {
        NbtElement element = decode(encoded);
        if (!(element instanceof NbtCompound compound))
            throw new IOException("Encoded NBT is not a compound");
        return compound;
    }
}
