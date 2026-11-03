package com.atsukimc.anticrash.logging;

//? if (>=1.18 && <=1.18.2) {
/*import net.minecraft.network.chat.TextComponent;
*///?}
//? if (>=26.1 && <=26.3) {
import net.minecraft.ChatFormatting;
//?}

//? if (>=1.18 && <=1.21.11) {
/*import net.minecraft.network.chat.MutableComponent;
*///?}
//? if (>=26.1 && <=26.3) {
import net.minecraft.network.chat.Component;
//?}

//? if (>=1.18 && <=1.21.11) {
/*import net.minecraft.network.chat.Component;
*///?}

//? if (>=1.18 && <=1.21.11) {
/*import net.minecraft.ChatFormatting;

*///?}
//? if (>=26.1 && <=26.3) {
import net.minecraft.network.chat.MutableComponent;

//?}

/** Renders an AntiCrash message as coloured Minecraft chat text; see {@link MessageStyle}. */
public final class ChatStyle {
    private ChatStyle() {}

    /**
     * @param base colour of the unstyled text, or null to inherit (white for feedback, red when
     *     Vanilla wraps the text as a command error)
     */

    //? if (>=1.18 && <=1.21.11) {
    /*public static Component text(String message, ChatFormatting base, boolean coloured)
                *///?} else if (>=26.1 && <=26.3) {
    public static Component text(String message, ChatFormatting base, boolean coloured)
                //?}
            {
        if (message == null)
            //? if (>=1.18 && <=1.18.2) {
            /*return new TextComponent("");
        *///?} else if (>=1.19 && <=1.21.11) {
            /*return Component.literal("");
*///?} else if (>=26.1 && <=26.3) {
            return Component.literal("");
        //?}

        if (!coloured) {

            //? if (>=1.18 && <=1.18.2) {
            /*MutableComponent plain = new TextComponent(message);
            *///?} else if (>=1.19 && <=1.21.11) {
            /*MutableComponent plain = Component.literal(message);
*///?} else if (>=26.1 && <=26.3) {
            MutableComponent plain = Component.literal(message);
            //?}

            //? if (>=1.18 && <=1.21.11) {
            /*return base == null ? plain : plain.withStyle(base);
            *///?} else if (>=26.1 && <=26.3) {
            return base == null ? plain : plain.withStyle(base);
            //?}

        }

        //? if (>=1.18 && <=1.18.2) {
        /*MutableComponent result = new TextComponent("");
        *///?} else if (>=1.19 && <=1.21.11) {
        /*MutableComponent result = Component.literal("");
*///?} else if (>=26.1 && <=26.3) {
        MutableComponent result = Component.literal("");
        //?}

        if (base != null)
            //? if (>=1.18 && <=1.21.11) {
            /*result.withStyle(base);
        *///?} else if (>=26.1 && <=26.3) {
            result.withStyle(base);
        //?}

        String[] lines = message.split("\n", -1);
        for (int index = 0; index < lines.length; index++) {
            if (index > 0) result.append("\n");
            for (MessageStyle.Span span : MessageStyle.spans(lines[index])) {

                //? if (>=1.18 && <=1.18.2) {
                /*MutableComponent part = new TextComponent(span.text());
                *///?} else if (>=1.19 && <=1.21.11) {
                /*MutableComponent part = Component.literal(span.text());
*///?} else if (>=26.1 && <=26.3) {
                MutableComponent part = Component.literal(span.text());
                //?}

                switch (span.kind()) {
                    case HEADER ->
                            //? if (>=1.18 && <=1.21.11) {
                            /*part.withStyle(ChatFormatting.BOLD);
                    *///?} else if (>=26.1 && <=26.3) {
                            part.withStyle(ChatFormatting.BOLD);
                    //?}

                    case PREFIX, PLAIN -> {}
                }
                result.append(part);
            }
        }
        return result;
    }
}
