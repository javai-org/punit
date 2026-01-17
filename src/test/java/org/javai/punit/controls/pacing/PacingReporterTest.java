package org.javai.punit.controls.pacing;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.javai.punit.reporting.PUnitReporter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link PacingReporter}.
 */
class PacingReporterTest {

    private static final String PUNIT_REPORTER_LOGGER = "org.javai.punit.reporting.PUnitReporter";
    
    private TestAppender appender;
    private PacingReporter reporter;
    private LoggerConfig targetLoggerConfig;

    @BeforeEach
    void setUp() {
        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        Configuration configuration = context.getConfiguration();
        
        // Get or create the PUnitReporter logger config
        targetLoggerConfig = configuration.getLoggerConfig(PUNIT_REPORTER_LOGGER);
        if (!targetLoggerConfig.getName().equals(PUNIT_REPORTER_LOGGER)) {
            // Logger config doesn't exist, create it
            targetLoggerConfig = new LoggerConfig(PUNIT_REPORTER_LOGGER, Level.INFO, false);
            configuration.addLogger(PUNIT_REPORTER_LOGGER, targetLoggerConfig);
        }

        appender = new TestAppender("pacingTestAppender");
        appender.start();
        targetLoggerConfig.addAppender(appender, Level.INFO, null);
        context.updateLoggers();

        reporter = new PacingReporter(new PUnitReporter());
    }

    @AfterEach
    void tearDown() {
        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        targetLoggerConfig.removeAppender(appender.getName());
        appender.stop();
        context.updateLoggers();
    }

    private String getLoggedContent() {
        if (appender.events().isEmpty()) {
            return "";
        }
        return appender.events().stream()
                .map(e -> e.getMessage().getFormattedMessage())
                .reduce("", (a, b) -> a + b);
    }

    private List<org.apache.logging.log4j.core.LogEvent> getInfoEvents() {
        return appender.events().stream()
                .filter(e -> e.getLevel() == Level.INFO)
                .toList();
    }

    private List<org.apache.logging.log4j.core.LogEvent> getWarnEvents() {
        return appender.events().stream()
                .filter(e -> e.getLevel() == Level.WARN)
                .toList();
    }

    @Nested
    @DisplayName("Pre-Flight Report")
    class PreFlightReportTests {

        @Test
        @DisplayName("Does not print report when no pacing configured")
        void noPacing_noReport() {
            PacingConfiguration pacing = PacingConfiguration.noPacing();
            Instant startTime = Instant.now();

            reporter.printPreFlightReport("testMethod", 100, pacing, startTime);

            assertThat(appender.events()).isEmpty();
        }

        @Test
        @DisplayName("Prints report when pacing is configured")
        void withPacing_printsReport() {
            PacingConfiguration pacing = new PacingConfiguration(
                    0, 60, 0, 0, 0, 1000, 1, 200000, 1.0);
            Instant startTime = Instant.now();

            reporter.printPreFlightReport("testMethod", 200, pacing, startTime);

            assertThat(getInfoEvents()).hasSize(1);
            String output = getLoggedContent();
            assertThat(output).contains("═ EXECUTION PLAN");
            assertThat(output).contains("PUnit ═");
            assertThat(output).contains("testMethod");
            assertThat(output).contains("Samples requested:");
            assertThat(output).contains("200");
        }

        @Test
        @DisplayName("Includes RPM constraint in report")
        void includesRpmConstraint() {
            PacingConfiguration pacing = new PacingConfiguration(
                    0, 60, 0, 0, 0, 1000, 1, 200000, 1.0);
            Instant startTime = Instant.now();

            reporter.printPreFlightReport("testMethod", 200, pacing, startTime);

            String output = getLoggedContent();
            assertThat(output).contains("Max requests/min:");
            assertThat(output).contains("60");
            assertThat(output).contains("RPM");
        }

        @Test
        @DisplayName("Includes RPS constraint in report")
        void includesRpsConstraint() {
            PacingConfiguration pacing = new PacingConfiguration(
                    2.0, 0, 0, 0, 0, 500, 1, 50000, 2.0);
            Instant startTime = Instant.now();

            reporter.printPreFlightReport("testMethod", 100, pacing, startTime);

            String output = getLoggedContent();
            assertThat(output).contains("Max requests/sec:");
            assertThat(output).contains("RPS");
        }

        @Test
        @DisplayName("Includes concurrency in report when > 1")
        void includesConcurrency() {
            PacingConfiguration pacing = new PacingConfiguration(
                    0, 60, 0, 3, 0, 1000, 3, 200000, 1.0);
            Instant startTime = Instant.now();

            reporter.printPreFlightReport("testMethod", 200, pacing, startTime);

            String output = getLoggedContent();
            assertThat(output).contains("Max concurrent:");
            assertThat(output).contains("3");
        }

        @Test
        @DisplayName("Includes estimated duration")
        void includesEstimatedDuration() {
            PacingConfiguration pacing = new PacingConfiguration(
                    0, 60, 0, 0, 0, 1000, 1, 200000, 1.0);
            Instant startTime = Instant.now();

            reporter.printPreFlightReport("testMethod", 200, pacing, startTime);

            String output = getLoggedContent();
            assertThat(output).contains("Estimated duration:");
            assertThat(output).contains("3m 20s");
        }

        @Test
        @DisplayName("Includes estimated completion time")
        void includesEstimatedCompletion() {
            PacingConfiguration pacing = new PacingConfiguration(
                    0, 60, 0, 0, 0, 1000, 1, 200000, 1.0);
            Instant startTime = Instant.now();

            reporter.printPreFlightReport("testMethod", 200, pacing, startTime);

            String output = getLoggedContent();
            assertThat(output).contains("Estimated completion:");
        }

        @Test
        @DisplayName("Includes start time")
        void includesStartTime() {
            PacingConfiguration pacing = new PacingConfiguration(
                    0, 60, 0, 0, 0, 1000, 1, 200000, 1.0);
            Instant startTime = Instant.now();

            reporter.printPreFlightReport("testMethod", 200, pacing, startTime);

            String output = getLoggedContent();
            assertThat(output).contains("Started:");
        }

        @Test
        @DisplayName("Includes effective throughput")
        void includesEffectiveThroughput() {
            PacingConfiguration pacing = new PacingConfiguration(
                    0, 60, 0, 0, 0, 1000, 1, 200000, 1.0);
            Instant startTime = Instant.now();

            reporter.printPreFlightReport("testMethod", 200, pacing, startTime);

            String output = getLoggedContent();
            assertThat(output).contains("Effective throughput:");
            assertThat(output).contains("samples/min");
        }

        @Test
        @DisplayName("Uses plain text without box drawing characters")
        void usesPlainTextWithoutBoxes() {
            PacingConfiguration pacing = new PacingConfiguration(
                    0, 60, 0, 0, 0, 1000, 1, 200000, 1.0);
            Instant startTime = Instant.now();

            reporter.printPreFlightReport("testMethod", 200, pacing, startTime);

            String output = getLoggedContent();
            assertThat(output).doesNotContain("╔");
            assertThat(output).doesNotContain("╚");
            assertThat(output).doesNotContain("║");
        }

        @Test
        @DisplayName("Uses PUnitReporter divider format with title on left and PUnit on right")
        void usesPUnitReporterDividerFormat() {
            PacingConfiguration pacing = new PacingConfiguration(
                    0, 60, 0, 0, 0, 1000, 1, 200000, 1.0);
            Instant startTime = Instant.now();

            reporter.printPreFlightReport("testMethod", 200, pacing, startTime);

            String output = getLoggedContent();
            assertThat(output).contains("═ EXECUTION PLAN");
            assertThat(output).contains("PUnit ═");
        }
    }

    @Nested
    @DisplayName("Feasibility Warning")
    class FeasibilityWarningTests {

        @Test
        @DisplayName("Does not warn when no pacing")
        void noPacing_noWarning() {
            PacingConfiguration pacing = PacingConfiguration.noPacing();

            reporter.printFeasibilityWarning(pacing, 60000, 100);

            assertThat(appender.events()).isEmpty();
        }

        @Test
        @DisplayName("Does not warn when no time budget")
        void noTimeBudget_noWarning() {
            PacingConfiguration pacing = new PacingConfiguration(
                    0, 60, 0, 0, 0, 1000, 1, 200000, 1.0);

            reporter.printFeasibilityWarning(pacing, 0, 100);

            assertThat(appender.events()).isEmpty();
        }

        @Test
        @DisplayName("Does not warn when duration is within budget")
        void withinBudget_noWarning() {
            PacingConfiguration pacing = new PacingConfiguration(
                    0, 60, 0, 0, 0, 1000, 1, 50000, 1.0);

            reporter.printFeasibilityWarning(pacing, 60000, 50);

            assertThat(appender.events()).isEmpty();
        }

        @Test
        @DisplayName("Warns when duration exceeds budget")
        void exceedsBudget_warns() {
            // 200 seconds estimated, but only 60 second budget
            PacingConfiguration pacing = new PacingConfiguration(
                    0, 60, 0, 0, 0, 1000, 1, 200000, 1.0);

            reporter.printFeasibilityWarning(pacing, 60000, 200);

            assertThat(getWarnEvents()).hasSize(1);
            String output = getLoggedContent();
            assertThat(output).contains("═ PACING CONFLICT");
            assertThat(output).contains("PUnit ═");
        }

        @Test
        @DisplayName("Suggests reducing sample count")
        void suggestsReducingSamples() {
            PacingConfiguration pacing = new PacingConfiguration(
                    0, 60, 0, 0, 0, 1000, 1, 200000, 1.0);

            reporter.printFeasibilityWarning(pacing, 60000, 200);

            String output = getLoggedContent();
            assertThat(output).contains("Reduce sample count");
        }

        @Test
        @DisplayName("Suggests increasing time budget")
        void suggestsIncreasingBudget() {
            PacingConfiguration pacing = new PacingConfiguration(
                    0, 60, 0, 0, 0, 1000, 1, 200000, 1.0);

            reporter.printFeasibilityWarning(pacing, 60000, 200);

            String output = getLoggedContent();
            assertThat(output).contains("Increase time budget");
        }

        @Test
        @DisplayName("Suggests relaxing pacing constraints")
        void suggestsRelaxingConstraints() {
            PacingConfiguration pacing = new PacingConfiguration(
                    0, 60, 0, 0, 0, 1000, 1, 200000, 1.0);

            reporter.printFeasibilityWarning(pacing, 60000, 200);

            String output = getLoggedContent();
            assertThat(output).contains("Relax pacing constraints");
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
