package com.atsukimc.anticrash.logging;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageStyleTest {
    private static String strip(String ansi) {
        return ansi.replaceAll("\u001B\\[[0-9;]*m", "");
    }

    @Test
    void colouringNeverChangesTheText() {
        for (String line : List.of(
                "[AntiCrash] checkpointを要求しました。",
                "Checkpoint cp-012545 を PORTABLE_GENERATION で確定しました（snapshot 122 ms）",
                "----- AntiCrash コマンド -----", "[AntiCrash]", "", "   ")) {
            assertEquals(line, MessageStyle.spans(line).stream().map(MessageStyle.Span::text)
                    .collect(Collectors.joining()));
            assertEquals(line, strip(MessageStyle.ansiReply(line, false)));
            assertEquals(line, strip(MessageStyle.ansiReply(line, true)));
            assertEquals("[NORMAL] " + line, strip(MessageStyle.ansiLine("NORMAL", line)));
        }
    }

    @Test
    void onlyLinesThatNeedAttentionAreColoured() {
        String message = "Checkpoint cp-000001 を確定しました（変更 9、失敗 0）";
        assertEquals("[NORMAL] " + message, MessageStyle.ansiLine("NORMAL", message));
        assertEquals("[AntiCrash] ok", MessageStyle.ansiReply("[AntiCrash] ok", false));
        assertTrue(MessageStyle.ansiLine("IMPORTANT", message).startsWith("\u001B[93m[IMPORTANT] "));
        assertTrue(MessageStyle.ansiLine("FATAL", message).startsWith("\u001B[1;91m[FATAL] "));
        assertTrue(MessageStyle.ansiLine("VERBOSE", message).startsWith("\u001B[90m[VERBOSE] "));
        assertEquals("\u001B[91m" + message + "\u001B[0m", MessageStyle.ansiReply(message, true));
        assertEquals(List.of(MessageStyle.Kind.PREFIX, MessageStyle.Kind.PLAIN),
                MessageStyle.spans("[AntiCrash] 設定を再読み込みしました。").stream()
                        .map(MessageStyle.Span::kind).toList());
        assertEquals(MessageStyle.Kind.HEADER,
                MessageStyle.spans("----- AntiCrash コマンド -----").get(0).kind());
    }
}
