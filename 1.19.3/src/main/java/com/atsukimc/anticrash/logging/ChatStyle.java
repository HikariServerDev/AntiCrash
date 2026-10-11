package com.atsukimc.anticrash.logging;

import net.minecraft.text.Text;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** Renders an AntiCrash message as coloured Minecraft chat text; see {@link MessageStyle}. */
public final class ChatStyle {
    private ChatStyle() {
    }

    /**
     * @param base colour of the unstyled text, or null to inherit (white for feedback, red when
     *             Vanilla wraps the text as a command error)
     */
    public static Text text(String message, Formatting base, boolean coloured) {
        if (message == null) return Text.literal("");
        if (!coloured) {
            MutableText plain = Text.literal(message);
            return base == null ? plain : plain.formatted(base);
        }
        MutableText result = Text.literal("");
        if (base != null) result.formatted(base);
        String[] lines = message.split("\n", -1);
        for (int index = 0; index < lines.length; index++) {
            if (index > 0) result.append("\n");
            for (MessageStyle.Span span : MessageStyle.spans(lines[index])) {
                MutableText part = Text.literal(span.text());
                switch (span.kind()) {
                    case HEADER -> part.formatted(Formatting.BOLD);
                    case PREFIX, PLAIN -> { }
                }
                result.append(part);
            }
        }
        return result;
    }
}
