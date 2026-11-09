package com.atsukimc.anticrash.runtime;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.world.entity.player.Abilities;
import org.junit.jupiter.api.Test;

class VersionStorageAbilitiesTest {
    @Test
    void abilitiesRoundTripOnEveryVersion() {
        var abilities = new Abilities();
        abilities.flying = true;
        abilities.mayfly = true;
        abilities.setFlyingSpeed(0.3f);
        abilities.setWalkingSpeed(0.17f);

        var restored = new Abilities();
        VersionStorage.abilities(VersionStorage.abilities(abilities)).accept(restored);

        assertTrue(restored.flying);
        assertTrue(restored.mayfly);
        assertEquals(0.3f, restored.getFlyingSpeed());
        assertEquals(0.17f, restored.getWalkingSpeed());
    }
}
