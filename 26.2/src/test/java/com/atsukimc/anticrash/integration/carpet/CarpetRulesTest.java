package com.atsukimc.anticrash.integration.carpet;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CarpetRulesTest {
    private static final Map<String, String> DEFAULTS = Map.of(
            "creativeNoClip", "false", "fillUpdates", "true", "commandPlayer", "ops", "tntDoNotUpdate", "false");

    /**
     * Regression test: creativeNoClip had been switched on with /carpet (not as a default), so it
     * was off again after a recovery.
     */
    @Test
    void aRuleChangedAtRunTimeIsSetAgain() {
        Map<String, String> afterRestart = Map.of(
                "creativeNoClip", "false", "fillUpdates", "true", "commandPlayer", "true", "tntDoNotUpdate", "false");
        Map<String, String> checkpoint = Map.of("creativeNoClip", "true", "commandPlayer", "true");
        assertEquals(Map.of("creativeNoClip", "true"), CarpetRules.changes(afterRestart, DEFAULTS, checkpoint));
    }

    @Test
    void aRuleThatWasAtItsDefaultAtTheCheckpointGoesBackToIt() {
        // carpet.conf was edited to make tntDoNotUpdate a default after the checkpoint was taken.
        Map<String, String> afterRestart = Map.of(
                "creativeNoClip", "false", "fillUpdates", "true", "commandPlayer", "ops", "tntDoNotUpdate", "true");
        assertEquals(Map.of("tntDoNotUpdate", "false"), CarpetRules.changes(afterRestart, DEFAULTS, Map.of()));
    }

    @Test
    void nothingIsTouchedWhenTheRulesAlreadyMatch() {
        Map<String, String> afterRestart = Map.of(
                "creativeNoClip", "true", "fillUpdates", "false", "commandPlayer", "ops", "tntDoNotUpdate", "false");
        assertTrue(CarpetRules.changes(afterRestart, DEFAULTS, Map.of("creativeNoClip", "true", "fillUpdates", "false")).isEmpty());
    }

    @Test
    void aRuleOfAnExtensionThatIsGoneIsIgnored() {
        Map<String, String> afterRestart = Map.of("creativeNoClip", "false");
        assertEquals(Map.of("creativeNoClip", "true"), CarpetRules.changes(afterRestart, Map.of("creativeNoClip", "false"),
                Map.of("creativeNoClip", "true", "someRemovedRule", "7")));
        assertEquals(3, CarpetRules.count(Map.of("carpet", Map.of("a", "1", "b", "2"), "extra", Map.of("c", "3"))));
        assertEquals(0, CarpetRules.count(null));
    }
}
