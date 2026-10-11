package com.atsukimc.anticrash.logging;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.ConsoleAppender;
import org.apache.logging.log4j.core.appender.WriterAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.junit.jupiter.api.Test;

import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsoleRouterTest {
    @Test
    void fileAppendersReceiveThePlainLineOnceAndNeverTheColouredOne() {
        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        Configuration configuration = context.getConfiguration();
        PatternLayout layout = PatternLayout.newBuilder().withPattern("%msg%n").build();
        StringWriter file = new StringWriter();
        WriterAppender fileAppender = WriterAppender.newBuilder().setName("TestFile").setLayout(layout)
                .setTarget(file).build();
        fileAppender.start();
        ConsoleAppender console = ConsoleAppender.newBuilder().setName("TestConsole").setLayout(layout).build();
        console.start();
        configuration.getRootLogger().addAppender(fileAppender, null, null);
        configuration.getRootLogger().addAppender(console, null, null);
        context.updateLoggers();
        try {
            ConsoleRouter router = new ConsoleRouter();
            assertTrue(router.log(Level.INFO, "[NORMAL] plain line", "\u001B[32m[NORMAL]\u001B[0m coloured line"));
            assertEquals("[NORMAL] plain line" + System.lineSeparator(), file.toString());

            // An appender another mod adds later must start receiving plain lines too.
            StringWriter late = new StringWriter();
            WriterAppender lateAppender = WriterAppender.newBuilder().setName("TestLate").setLayout(layout)
                    .setTarget(late).build();
            lateAppender.start();
            configuration.getRootLogger().addAppender(lateAppender, null, null);
            context.updateLoggers();
            assertTrue(router.log(Level.WARN, "second", "\u001B[33msecond\u001B[0m"));
            assertEquals("second" + System.lineSeparator(), late.toString());
            assertFalse(file.toString().contains("\u001B"));
            configuration.getRootLogger().removeAppender("TestLate");
        } finally {
            configuration.getRootLogger().removeAppender("TestFile");
            configuration.getRootLogger().removeAppender("TestConsole");
            context.updateLoggers();
        }
    }
}
