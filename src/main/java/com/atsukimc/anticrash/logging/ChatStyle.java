package com.atsukimc.anticrash.logging;

//? if (>=1.18 && <=1.18.2) {
/*import net.minecraft.network.chat.TextComponent;
*///?}

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
        if (message == null)
            //? if (>=1.18 && <=1.18.2) {
            /*return new TextComponent("");
        *///?} else {
            return Component.literal("");
        //?}

        if (!coloured) {

            //? if (>=1.18 && <=1.18.2) {
            /*MutableComponent plain = new TextComponent(message);
            *///?} else {
            MutableComponent plain = Component.literal(message);
            //?}

            return base == null ? plain : plain.withStyle(base);
        }

        //? if (>=1.18 && <=1.18.2) {
        /*MutableComponent result = new TextComponent("");
        *///?} else {
        MutableComponent result = Component.literal("");
        //?}

        if (base != null) result.withStyle(base);

        String[] lines = message.split("\n", -1);
        for (int index = 0; index < lines.length; index++) {
            if (index > 0) result.append("\n");
            for (MessageStyle.Span span : MessageStyle.spans(lines[index])) {

                //? if (>=1.18 && <=1.18.2) {
                /*MutableComponent part = new TextComponent(span.text());
                *///?} else {
                MutableComponent part = Component.literal(span.text());
                //?}

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
