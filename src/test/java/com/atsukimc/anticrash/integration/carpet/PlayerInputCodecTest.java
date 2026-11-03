//? if (>=1.21.2 && <=26.3) {
package com.atsukimc.anticrash.integration.carpet;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class PlayerInputCodecTest {

    //? if (>=1.21.6 && <=26.3) {
    @Test
    void freezeFiltersPressReleaseAndSprintWithoutReadingNetworkThreadState() {
        for (int before = 0; before < 128; before++)
            for (int after = 0; after < 128; after++) {
                var current = PlayerInputCodec.restore(before);
                //?}

                //? if (>=1.21.6 && <=26.3) {
                var next = PlayerInputCodec.restore(after);
                //?}

                //? if (>=1.21.6 && <=26.3) {
                assertEquals(
                        before == after,
                        PlayerInputCodec.allowedDuringFreeze(current, next, false, false));
                //?}

                //? if (>=1.21.6 && <=26.3) {
                assertEquals(
                        (before & 95) == (after & 95),
                        PlayerInputCodec.allowedDuringFreeze(current, next, false, true));
                //?}

                //? if (>=1.21.6 && <=26.3) {
                assertEquals(
                        (before & 32) == (after & 32),
                        PlayerInputCodec.allowedDuringFreeze(current, next, true, false));
                //?}

                //? if (>=1.21.6 && <=26.3) {
                assertTrue(PlayerInputCodec.allowedDuringFreeze(current, next, true, true));
            }
    }

    //?}

    @Test
    void allCombinationsIncludingOppositeKeysRoundTrip() {
        for (int flags = 0; flags < 128; flags++)
            assertEquals(flags, PlayerInputCodec.capture(PlayerInputCodec.restore(flags)));
    }

    @Test
    void absentOrInvalidStateFailsClosed() {
        assertThrows(IllegalArgumentException.class, () -> PlayerInputCodec.restore(null));
        assertThrows(IllegalArgumentException.class, () -> PlayerInputCodec.restore(128));
        assertThrows(IllegalArgumentException.class, () -> PlayerInputCodec.restore(-1));
    }
}
//?}
