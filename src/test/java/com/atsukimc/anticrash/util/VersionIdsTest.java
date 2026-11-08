package com.atsukimc.anticrash.util;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.SharedConstants;
import net.minecraft.core.Registry;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class VersionIdsTest {
    @BeforeAll
    static void initializeRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void identifiersKeepTheirNamespaceAndPath() {
        assertEquals("anticrash:checkpoint", VersionIds.of("anticrash", "checkpoint").toString());
        assertEquals(
                VersionIds.of("anticrash", "checkpoint"), VersionIds.parse("anticrash:checkpoint"));
        assertEquals(VersionIds.parse("minecraft:stone"), VersionIds.tryParse("minecraft:stone"));
        assertNull(VersionIds.tryParse("invalid identifier!"));
        assertThrows(RuntimeException.class, () -> VersionIds.parse("invalid identifier!"));
    }

    @Test
    void dimensionKeysKeepThePersistedText() {
        assertEquals("minecraft:overworld", VersionIds.key(Level.OVERWORLD));
        assertEquals("minecraft:the_nether", VersionIds.key(Level.NETHER));
        assertEquals("minecraft:the_end", VersionIds.key(Level.END));
    }

    @Test
    void eachRegistryRoundTripsTheStoredIdentifier() {
        assertRoundTrip(VersionIds.blocks(), "minecraft:piston");
        assertRoundTrip(VersionIds.fluids(), "minecraft:water");
        assertRoundTrip(VersionIds.items(), "minecraft:diamond");
        assertRoundTrip(VersionIds.entityTypes(), "minecraft:pig");
        assertRoundTrip(VersionIds.blockEntityTypes(), "minecraft:chest");
        assertRoundTrip(VersionIds.gameEvents(), "minecraft:step");
    }

    @Test
    void unknownIdsDoNotResolveToTheDefaultRegistryValue() {
        for (String id : new String[] {"anticrash:missing", "invalid identifier!"}) {
            assertFalse(VersionIds.contains(VersionIds.blocks(), id), id);
            assertNull(VersionIds.get(VersionIds.blocks(), id), id);
            assertFalse(VersionIds.contains(VersionIds.items(), id), id);
            assertNull(VersionIds.get(VersionIds.items(), id), id);
        }
    }

    private static <T> void assertRoundTrip(Registry<T> registry, String id) {
        assertTrue(VersionIds.contains(registry, id), id);
        T value = VersionIds.get(registry, id);
        assertNotNull(value, id);
        assertEquals(id, VersionIds.id(registry, value));
        assertSame(value, VersionIds.value(registry, VersionIds.parse(id)));
    }
}
