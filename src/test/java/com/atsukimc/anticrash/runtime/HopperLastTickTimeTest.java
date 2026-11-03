package com.atsukimc.anticrash.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Why HopperBlockEntity.lastTickTime needs no restore, as an executable argument.
 *
 * <p>The field is written in one place (serverTick: {@code lastTickTime = world.getTime()}, every
 * tick, before the hopper moves anything) and read in one place: when a hopper-to-hopper transfer
 * fills an empty hopper, the receiver's cooldown is 7 if {@code to.lastTickTime >=
 * from.lastTickTime} and 8 otherwise. Transfers between hoppers only happen while block entities
 * tick, and the side that acts has always just stamped itself with the current tick. So the
 * comparison only asks "has the other hopper already ticked in this tick?". This model replays the
 * first ticks after a checkpoint for every tick order of a chain, once with the live values (every
 * hopper stamped with the checkpoint tick) and once with the values after a load (0), and with a
 * hopper in a chunk that does not tick.
 */
class HopperLastTickTimeTest {
    private record Transfer(int tick, int from, int to, int cooldown) {}

    /**
     * from pushes into to, and to pulls from the hopper above it, exactly as transfer() decides.
     */
    private static List<Transfer> run(
            long[] lastTickTime, boolean[] ticking, int[] order, long firstTick) {
        long[] stamps = lastTickTime.clone();
        List<Transfer> transfers = new ArrayList<>();
        for (long tick = firstTick; tick < firstTick + 3; tick++) {
            for (int index : order) {
                if (!ticking[index]) continue;
                stamps[index] = tick;
                // push: this hopper (from) into the next one (to)
                if (index + 1 < stamps.length) {
                    transfers.add(
                            new Transfer(
                                    (int) (tick - firstTick),
                                    index,
                                    index + 1,
                                    stamps[index + 1] >= stamps[index] ? 7 : 8));
                }
                // pull: this hopper (to) takes from the previous one (from)
                if (index > 0) {
                    transfers.add(
                            new Transfer(
                                    (int) (tick - firstTick),
                                    index - 1,
                                    index,
                                    stamps[index] >= stamps[index - 1] ? 7 : 8));
                }
            }
        }
        return transfers;
    }

    @Test
    void cooldownDecisionsAreIdenticalAfterALoadForEveryTickOrder() {
        long checkpointTick = 503_803_354L;
        int[][] orders = {{0, 1, 2}, {0, 2, 1}, {1, 0, 2}, {1, 2, 0}, {2, 0, 1}, {2, 1, 0}};
        boolean[][] tickingSets = {
            {true, true, true}, {true, false, true}, {false, true, true}, {true, true, false}
        };
        for (boolean[] ticking : tickingSets) {
            for (int[] order : orders) {
                long[] live = new long[3];
                long[] loaded = new long[3];
                for (int i = 0; i < 3; i++) {
                    // A ticking hopper was stamped in the checkpoint tick; one in a lazy chunk
                    // carries some older stamp. After a load both are 0.
                    live[i] = ticking[i] ? checkpointTick : checkpointTick - 1000;
                }
                assertEquals(
                        run(live, ticking, order, checkpointTick + 1),
                        run(loaded, ticking, order, checkpointTick + 1));
            }
        }
    }
}
