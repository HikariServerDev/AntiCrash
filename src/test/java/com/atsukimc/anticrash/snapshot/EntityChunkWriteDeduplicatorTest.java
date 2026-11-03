package com.atsukimc.anticrash.snapshot;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

//? if (>=1.18 && <=1.21.11) {
import net.minecraft.nbt.NbtCompound;
//?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.nbt.CompoundTag;
*///?}

import org.junit.jupiter.api.Test;

class EntityChunkWriteDeduplicatorTest {

    //? if (>=1.18 && <=1.21.11) {
    private static NbtCompound chunk(int health)
                //?}
                //? if (>=26.1 && <=26.3) {
                /*private static CompoundTag chunk(int health)
*///?}
            {

        //? if (>=1.18 && <=1.21.11) {
        NbtCompound nbt = new NbtCompound();
        //?}
        //? if (>=26.1 && <=26.3) {
        /*CompoundTag nbt = new CompoundTag();
*///?}

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
