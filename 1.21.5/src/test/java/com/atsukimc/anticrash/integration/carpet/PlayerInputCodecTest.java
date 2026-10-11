package com.atsukimc.anticrash.integration.carpet;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class PlayerInputCodecTest {
    @Test void allCombinationsIncludingOppositeKeysRoundTrip() {
        for (int flags=0; flags<128; flags++) assertEquals(flags, PlayerInputCodec.capture(PlayerInputCodec.restore(flags)));
    }
    @Test void absentOrInvalidStateFailsClosed() {
        assertThrows(IllegalArgumentException.class, () -> PlayerInputCodec.restore(null));
        assertThrows(IllegalArgumentException.class, () -> PlayerInputCodec.restore(128));
        assertThrows(IllegalArgumentException.class, () -> PlayerInputCodec.restore(-1));
    }
}
