package com.atsukimc.anticrash.logging;

//? if (>=1.18 && <=1.18.2) {
import net.minecraft.text.LiteralText;
//?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.ChatFormatting;
*///?}

//? if (>=1.18 && <=1.21.11) {
import net.minecraft.text.MutableText;
//?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.network.chat.Component;
*///?}

//? if (>=1.18 && <=1.21.11) {
import net.minecraft.text.Text;
//?}

//? if (>=1.18 && <=1.21.11) {
import net.minecraft.util.Formatting;

//?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.network.chat.MutableComponent;
*///?}

/** Renders an AntiCrash message as coloured Minecraft chat text; see {@link MessageStyle}. */
public final class ChatStyle {
    private ChatStyle() {}

    /**
     * @param base colour of the unstyled text, or null to inherit (white for feedback, red when
     *     Vanilla wraps the text as a command error)
     */

    //? if (>=1.18 && <=1.21.11) {
    public static Text text(String message, Formatting base, boolean coloured)
                //?}
                //? if (>=26.1 && <=26.3) {
                /*public static Component text(String message, ChatFormatting base, boolean coloured)
*///?}
            {
        if (message == null)
            //? if (>=1.18 && <=1.18.2) {
            return new LiteralText("");
        //?}
        //? if (>=1.19 && <=1.21.11) {
        /*return Text.literal("");
*///?}
        //? if (>=26.1 && <=26.3) {
        /*return Component.literal("");
*///?}

        if (!coloured) {

            //? if (>=1.18 && <=1.18.2) {
            MutableText plain = new LiteralText(message);
            //?}
            //? if (>=1.19 && <=1.21.11) {
            /*MutableText plain = Text.literal(message);
*///?}
            //? if (>=26.1 && <=26.3) {
            /*MutableComponent plain = Component.literal(message);
*///?}

            //? if (>=1.18 && <=1.21.11) {
            return base == null ? plain : plain.formatted(base);
            //?}
            //? if (>=26.1 && <=26.3) {
            /*return base == null ? plain : plain.withStyle(base);
*///?}

        }

        //? if (>=1.18 && <=1.18.2) {
        MutableText result = new LiteralText("");
        //?}
        //? if (>=1.19 && <=1.21.11) {
        /*MutableText result = Text.literal("");
*///?}
        //? if (>=26.1 && <=26.3) {
        /*MutableComponent result = Component.literal("");
*///?}

        if (base != null)
            //? if (>=1.18 && <=1.21.11) {
            result.formatted(base);
        //?}
        //? if (>=26.1 && <=26.3) {
        /*result.withStyle(base);
*///?}

        String[] lines = message.split("\n", -1);
        for (int index = 0; index < lines.length; index++) {
            if (index > 0) result.append("\n");
            for (MessageStyle.Span span : MessageStyle.spans(lines[index])) {

                //? if (>=1.18 && <=1.18.2) {
                MutableText part = new LiteralText(span.text());
                //?}
                //? if (>=1.19 && <=1.21.11) {
                /*MutableText part = Text.literal(span.text());
*///?}
                //? if (>=26.1 && <=26.3) {
                /*MutableComponent part = Component.literal(span.text());
*///?}

                switch (span.kind()) {
                    case HEADER ->
                            //? if (>=1.18 && <=1.21.11) {
                            part.formatted(Formatting.BOLD);
                    //?}
                    //? if (>=26.1 && <=26.3) {
                    /*part.withStyle(ChatFormatting.BOLD);
*///?}

                    case PREFIX, PLAIN -> {}
                }
                result.append(part);
            }
        }
        return result;
    }
}
