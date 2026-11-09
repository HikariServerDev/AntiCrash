package com.atsukimc.anticrash.logging;

import com.atsukimc.anticrash.runtime.VersionMessages;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Renders an AntiCrash message as coloured Minecraft chat text; see {@link MessageStyle}. */
public final class ChatStyle {
    private ChatStyle() {}

    /**
     * @param base colour of the unstyled text, or null to inherit (white for feedback, red when
     *     Vanilla wraps the text as a command error)
     */
    public static Component text(String message, ChatFormatting base, boolean coloured) {
        if (message == null) return VersionMessages.literal("");

        if (!coloured) {
            MutableComponent plain = VersionMessages.literal(message);
            return base == null ? plain : plain.withStyle(base);
        }

        MutableComponent result = VersionMessages.literal("");

        if (base != null) result.withStyle(base);

        String[] lines = message.split("\n", -1);
        for (int index = 0; index < lines.length; index++) {
            if (index > 0) result.append("\n");
            for (MessageStyle.Span span : MessageStyle.spans(lines[index])) {
                MutableComponent part = VersionMessages.literal(span.text());

                switch (span.kind()) {
                    case HEADER -> part.withStyle(ChatFormatting.BOLD);
                    case PREFIX, PLAIN -> {}
                }
                result.append(part);
            }
        }
        return result;
    }
}
