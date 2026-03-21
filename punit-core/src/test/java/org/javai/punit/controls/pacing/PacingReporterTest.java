package org.javai.punit.controls.pacing;

import static org.assertj.core.api.Assertions.assertThat;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.time.Instant;
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

    private ByteArrayOutputStream stdoutCapture;
    private ByteArrayOutputStream stderrCapture;
    private PrintStream originalOut;
    private PrintStream originalErr;
    private PacingReporter reporter;

    @BeforeEach
    void setUp() {
        originalOut = System.out;
        originalErr = System.err;
        stdoutCapture = new ByteArrayOutputStream();
        stderrCapture = new ByteArrayOutputStream();
        System.setOut(new PrintStream(stdoutCapture));
        System.setErr(new PrintStream(stderrCapture));
        reporter = new PacingReporter(new PUnitReporter());
    }

    @AfterEach
    void tearDown() {
        System.setOut(originalOut);
        System.setErr(originalErr);
    }

    private String getStdout() {
        return stdoutCapture.toString();
    }

    private String getStderr() {
        return stderrCapture.toString();
    }

    private String getAllOutput() {
        return getStdout() + getStderr();
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

            assertThat(getAllOutput()).isEmpty();
        }

        @Test
        @DisplayName("Prints report when pacing is configured")
        void withPacing_printsReport() {
            PacingConfiguration pacing = new PacingConfiguration(
                    0, 60, 0, 0, 0, 1000, 1, 200000, 1.0);
            Instant startTime = Instant.now();

            reporter.printPreFlightReport("testMethod", 200, pacing, startTime);

            String output = getStdout();
            assertThat(output).contains("═ EXECUTION PLAN");
            assertThat(output).contains("PUnit ═");
            assertThat(output).contains("testMethod");
            assertThat(output).contains("Samples:");
            assertThat(output).contains("200");
        }

        @Test
        @DisplayName("Includes RPM constraint in report")
        void includesRpmConstraint() {
            PacingConfiguration pacing = new PacingConfiguration(
                    0, 60, 0, 0, 0, 1000, 1, 200000, 1.0);
            Instant startTime = Instant.now();

            reporter.printPreFlightReport("testMethod", 200, pacing, startTime);

            String output = getStdout();
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

            String output = getStdout();
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

            String output = getStdout();
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

            String output = getStdout();
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

            String output = getStdout();
            assertThat(output).contains("Estimated completion:");
        }

        @Test
        @DisplayName("Includes start time")
        void includesStartTime() {
            PacingConfiguration pacing = new PacingConfiguration(
                    0, 60, 0, 0, 0, 1000, 1, 200000, 1.0);
            Instant startTime = Instant.now();

            reporter.printPreFlightReport("testMethod", 200, pacing, startTime);

            String output = getStdout();
            assertThat(output).contains("Started:");
        }

        @Test
        @DisplayName("Includes effective throughput")
        void includesEffectiveThroughput() {
            PacingConfiguration pacing = new PacingConfiguration(
                    0, 60, 0, 0, 0, 1000, 1, 200000, 1.0);
            Instant startTime = Instant.now();

            reporter.printPreFlightReport("testMethod", 200, pacing, startTime);

            String output = getStdout();
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

            String output = getStdout();
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

            String output = getStdout();
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

            assertThat(getAllOutput()).isEmpty();
        }

        @Test
        @DisplayName("Does not warn when no time budget")
        void noTimeBudget_noWarning() {
            PacingConfiguration pacing = new PacingConfiguration(
                    0, 60, 0, 0, 0, 1000, 1, 200000, 1.0);

            reporter.printFeasibilityWarning(pacing, 0, 100);

            assertThat(getAllOutput()).isEmpty();
        }

        @Test
        @DisplayName("Does not warn when duration is within budget")
        void withinBudget_noWarning() {
            PacingConfiguration pacing = new PacingConfiguration(
                    0, 60, 0, 0, 0, 1000, 1, 50000, 1.0);

            reporter.printFeasibilityWarning(pacing, 60000, 50);

            assertThat(getAllOutput()).isEmpty();
        }

        @Test
        @DisplayName("Warns when duration exceeds budget")
        void exceedsBudget_warns() {
            PacingConfiguration pacing = new PacingConfiguration(
                    0, 60, 0, 0, 0, 1000, 1, 200000, 1.0);

            reporter.printFeasibilityWarning(pacing, 60000, 200);

            String output = getStderr();
            assertThat(output).contains("═ PACING CONFLICT");
            assertThat(output).contains("PUnit ═");
        }

        @Test
        @DisplayName("Suggests reducing sample count")
        void suggestsReducingSamples() {
            PacingConfiguration pacing = new PacingConfiguration(
                    0, 60, 0, 0, 0, 1000, 1, 200000, 1.0);

            reporter.printFeasibilityWarning(pacing, 60000, 200);

            String output = getStderr();
            assertThat(output).contains("Reduce sample count");
        }

        @Test
        @DisplayName("Suggests increasing time budget")
        void suggestsIncreasingBudget() {
            PacingConfiguration pacing = new PacingConfiguration(
                    0, 60, 0, 0, 0, 1000, 1, 200000, 1.0);

            reporter.printFeasibilityWarning(pacing, 60000, 200);

            String output = getStderr();
            assertThat(output).contains("Increase time budget");
        }

        @Test
        @DisplayName("Suggests relaxing pacing constraints")
        void suggestsRelaxingConstraints() {
            PacingConfiguration pacing = new PacingConfiguration(
                    0, 60, 0, 0, 0, 1000, 1, 200000, 1.0);

            reporter.printFeasibilityWarning(pacing, 60000, 200);

            String output = getStderr();
            assertThat(output).contains("Relax pacing constraints");
        }
    }
}
