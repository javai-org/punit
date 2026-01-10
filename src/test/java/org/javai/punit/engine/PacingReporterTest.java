package org.javai.punit.engine;

import static org.assertj.core.api.Assertions.assertThat;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link PacingReporter}.
 */
class PacingReporterTest {

    private ByteArrayOutputStream outputStream;
    private PacingReporter reporter;

    @BeforeEach
    void setUp() {
        outputStream = new ByteArrayOutputStream();
        reporter = new PacingReporter(new PrintStream(outputStream));
    }

    private String getOutput() {
        return outputStream.toString();
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

            assertThat(getOutput()).isEmpty();
        }

        @Test
        @DisplayName("Prints report when pacing is configured")
        void withPacing_printsReport() {
            PacingConfiguration pacing = new PacingConfiguration(
                    0, 60, 0, 0, 0, 1000, 1, 200000, 1.0);
            Instant startTime = Instant.now();

            reporter.printPreFlightReport("testMethod", 200, pacing, startTime);

            String output = getOutput();
            assertThat(output).contains("PUnit Test:");
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

            String output = getOutput();
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

            String output = getOutput();
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

            String output = getOutput();
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

            String output = getOutput();
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

            String output = getOutput();
            assertThat(output).contains("Estimated completion:");
        }

        @Test
        @DisplayName("Includes start time")
        void includesStartTime() {
            PacingConfiguration pacing = new PacingConfiguration(
                    0, 60, 0, 0, 0, 1000, 1, 200000, 1.0);
            Instant startTime = Instant.now();

            reporter.printPreFlightReport("testMethod", 200, pacing, startTime);

            String output = getOutput();
            assertThat(output).contains("Started:");
        }

        @Test
        @DisplayName("Includes effective throughput")
        void includesEffectiveThroughput() {
            PacingConfiguration pacing = new PacingConfiguration(
                    0, 60, 0, 0, 0, 1000, 1, 200000, 1.0);
            Instant startTime = Instant.now();

            reporter.printPreFlightReport("testMethod", 200, pacing, startTime);

            String output = getOutput();
            assertThat(output).contains("Effective throughput:");
            assertThat(output).contains("samples/min");
        }

        @Test
        @DisplayName("Uses box drawing characters")
        void usesBoxDrawingCharacters() {
            PacingConfiguration pacing = new PacingConfiguration(
                    0, 60, 0, 0, 0, 1000, 1, 200000, 1.0);
            Instant startTime = Instant.now();

            reporter.printPreFlightReport("testMethod", 200, pacing, startTime);

            String output = getOutput();
            assertThat(output).contains("╔");
            assertThat(output).contains("╚");
            assertThat(output).contains("║");
        }

        @Test
        @DisplayName("Truncates long test names")
        void truncatesLongTestNames() {
            PacingConfiguration pacing = new PacingConfiguration(
                    0, 60, 0, 0, 0, 1000, 1, 200000, 1.0);
            Instant startTime = Instant.now();
            String longName = "thisIsAVeryLongTestMethodNameThatExceedsTheBoxWidthAndShouldBeTruncated";

            reporter.printPreFlightReport(longName, 200, pacing, startTime);

            String output = getOutput();
            assertThat(output).contains("...");
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

            assertThat(getOutput()).isEmpty();
        }

        @Test
        @DisplayName("Does not warn when no time budget")
        void noTimeBudget_noWarning() {
            PacingConfiguration pacing = new PacingConfiguration(
                    0, 60, 0, 0, 0, 1000, 1, 200000, 1.0);

            reporter.printFeasibilityWarning(pacing, 0, 100);

            assertThat(getOutput()).isEmpty();
        }

        @Test
        @DisplayName("Does not warn when duration is within budget")
        void withinBudget_noWarning() {
            PacingConfiguration pacing = new PacingConfiguration(
                    0, 60, 0, 0, 0, 1000, 1, 50000, 1.0);

            reporter.printFeasibilityWarning(pacing, 60000, 50);

            assertThat(getOutput()).isEmpty();
        }

        @Test
        @DisplayName("Warns when duration exceeds budget")
        void exceedsBudget_warns() {
            // 200 seconds estimated, but only 60 second budget
            PacingConfiguration pacing = new PacingConfiguration(
                    0, 60, 0, 0, 0, 1000, 1, 200000, 1.0);

            reporter.printFeasibilityWarning(pacing, 60000, 200);

            String output = getOutput();
            assertThat(output).contains("WARNING");
            assertThat(output).contains("Pacing conflict detected");
        }

        @Test
        @DisplayName("Suggests reducing sample count")
        void suggestsReducingSamples() {
            PacingConfiguration pacing = new PacingConfiguration(
                    0, 60, 0, 0, 0, 1000, 1, 200000, 1.0);

            reporter.printFeasibilityWarning(pacing, 60000, 200);

            String output = getOutput();
            assertThat(output).contains("Reduce sample count");
        }

        @Test
        @DisplayName("Suggests increasing time budget")
        void suggestsIncreasingBudget() {
            PacingConfiguration pacing = new PacingConfiguration(
                    0, 60, 0, 0, 0, 1000, 1, 200000, 1.0);

            reporter.printFeasibilityWarning(pacing, 60000, 200);

            String output = getOutput();
            assertThat(output).contains("Increase time budget");
        }

        @Test
        @DisplayName("Suggests relaxing pacing constraints")
        void suggestsRelaxingConstraints() {
            PacingConfiguration pacing = new PacingConfiguration(
                    0, 60, 0, 0, 0, 1000, 1, 200000, 1.0);

            reporter.printFeasibilityWarning(pacing, 60000, 200);

            String output = getOutput();
            assertThat(output).contains("Relax pacing constraints");
        }
    }
}

