package org.javai.punit.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.junit.jupiter.api.Test;

class PUnitReporterTest {

    @Test
    void headerDividerIncludesTitleOnLeftAndPUnitOnRight() {
        PUnitReporter reporter = new PUnitReporter(60);
        String divider = reporter.headerDivider("TEST TITLE");

        // Format: ═ {TITLE} ═══...═══ PUnit ═
        assertThat(divider)
                .startsWith("═ TEST TITLE ")
                .endsWith(" PUnit ═")
                .hasSize(60);
    }

    @Test
    void headerDividerHandlesLongTitle() {
        PUnitReporter reporter = new PUnitReporter(30);
        String divider = reporter.headerDivider("VERY LONG TITLE HERE");

        // Title is too long for suffix, so suffix is sacrificed
        assertThat(divider)
                .startsWith("═ VERY LONG TITLE HERE")
                .endsWith("═");
    }

    @Test
    void headerDividerTruncatesExtremelyLongTitle() {
        PUnitReporter reporter = new PUnitReporter(40);
        String divider = reporter.headerDivider(
                "This is an extremely long title that definitely will not fit in the header");

        // Title should be truncated with ellipsis, PUnit suffix sacrificed
        assertThat(divider)
                .startsWith("═ This is an extremely long title")
                .contains("...")
                .endsWith(" ═")
                .hasSize(40);
    }

    @Test
    void footerDividerIsPlainLine() {
        PUnitReporter reporter = new PUnitReporter(40);
        String footer = reporter.footerDivider();

        assertThat(footer)
                .isEqualTo("═".repeat(40))
                .hasSize(40);
    }

    @Test
    void reportInfoEmitsFormattedOutput() {
        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        Configuration configuration = context.getConfiguration();
        
        // Get or create the PUnitReporter logger config
        String loggerName = PUnitReporter.class.getName();
        LoggerConfig loggerConfig = configuration.getLoggerConfig(loggerName);
        if (!loggerConfig.getName().equals(loggerName)) {
            loggerConfig = new LoggerConfig(loggerName, Level.INFO, false);
            configuration.addLogger(loggerName, loggerConfig);
        }

        TestAppender appender = new TestAppender("testAppender");
        appender.start();
        loggerConfig.addAppender(appender, Level.INFO, null);
        context.updateLoggers();

        try {
            PUnitReporter reporter = new PUnitReporter(40);
            reporter.reportInfo("TEST TITLE", "hello");

            assertThat(appender.events())
                    .hasSize(1);
            String message = appender.events().get(0).getMessage().getFormattedMessage();
            // Format: header + blank line + indented body + newline
            String expected = reporter.headerDivider("TEST TITLE") + "\n\n" + "  hello" + "\n";
            assertThat(message)
                    .isEqualTo(expected);
        } finally {
            loggerConfig.removeAppender(appender.getName());
            appender.stop();
            context.updateLoggers();
        }
    }

    private static final class TestAppender extends AbstractAppender {

        private final List<org.apache.logging.log4j.core.LogEvent> events = new ArrayList<>();

        private TestAppender(String name) {
            super(name, null, PatternLayout.createDefaultLayout(), false, null);
        }

        @Override
        public void append(org.apache.logging.log4j.core.LogEvent event) {
            events.add(event.toImmutable());
        }

        private List<org.apache.logging.log4j.core.LogEvent> events() {
            return events;
        }
    }
}
