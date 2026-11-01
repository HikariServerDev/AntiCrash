package com.atsukimc.anticrash.snapshot;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import net.minecraft.nbt.CompoundTag;

class EntityChunkWriteDeduplicatorTest {
    private static CompoundTag chunk(int health) {
        CompoundTag nbt = new CompoundTag();
        nbt.putInt("DataVersion", 2975);
        nbt.putInt("Health", health);
        return nbt;
    }

    @Test
    void skipsOnlyAnIdenticalRewriteAndRetriesAfterAFailure() {
        EntityChunkWriteDeduplicator dedup = new EntityChunkWriteDeduplicator();
        byte[] first = dedup.digestIfChanged(7L, chunk(20));
        assertNotNull(first);
        dedup.submitted(7L, first);
        assertNull(dedup.digestIfChanged(7L, chunk(20)));
        assertNotNull(dedup.digestIfChanged(7L, chunk(19)));
        assertNotNull(dedup.digestIfChanged(8L, chunk(20)));
        dedup.forget(7L);
        assertNotNull(dedup.digestIfChanged(7L, chunk(20)));
    }
}
