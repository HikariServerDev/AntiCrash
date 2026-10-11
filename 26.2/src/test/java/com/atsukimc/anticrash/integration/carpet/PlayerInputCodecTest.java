package com.atsukimc.anticrash.integration.carpet;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class PlayerInputCodecTest {
    @Test void freezeFiltersPressReleaseAndSprintWithoutReadingNetworkThreadState() {
        for(int before=0;before<128;before++) for(int after=0;after<128;after++) {
            var current=PlayerInputCodec.restore(before); var next=PlayerInputCodec.restore(after);
            assertEquals(before==after,PlayerInputCodec.allowedDuringFreeze(current,next,false,false));
            assertEquals((before&95)==(after&95),PlayerInputCodec.allowedDuringFreeze(current,next,false,true));
            assertEquals((before&32)==(after&32),PlayerInputCodec.allowedDuringFreeze(current,next,true,false));
            assertTrue(PlayerInputCodec.allowedDuringFreeze(current,next,true,true));
        }
    }
    @Test void allCombinationsIncludingOppositeKeysRoundTrip() {
        for (int flags=0; flags<128; flags++) assertEquals(flags, PlayerInputCodec.capture(PlayerInputCodec.restore(flags)));
    }
    @Test void absentOrInvalidStateFailsClosed() {
        assertThrows(IllegalArgumentException.class, () -> PlayerInputCodec.restore(null));
        assertThrows(IllegalArgumentException.class, () -> PlayerInputCodec.restore(128));
        assertThrows(IllegalArgumentException.class, () -> PlayerInputCodec.restore(-1));
    }
}
