package org.javai.punit.sentinel;

import static org.assertj.core.api.Assertions.assertThat;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import org.javai.punit.verdict.ProbabilisticTestVerdict;
import org.javai.punit.verdict.ProbabilisticTestVerdictBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SentinelConsoleReporterTest {

    private ByteArrayOutputStream buffer;
    private SentinelConsoleReporter reporter;

    @BeforeEach
    void setUp() {
        buffer = new ByteArrayOutputStream();
        reporter = new SentinelConsoleReporter(new PrintStream(buffer, true, StandardCharsets.UTF_8));
    }

    private String output() {
        return buffer.toString(StandardCharsets.UTF_8);
    }

    // ── Experiment progress (verbose mode) ───────────────────────────────

    @Nested
    @DisplayName("experiment progress")
    class ExperimentProgress {

        @Test
        @DisplayName("method start shows name and sample count")
        void methodStart() {
            reporter.onMethodStart("ShoppingBasketReliability.measureBaseline", 1000);

            assertThat(output()).isEqualTo(
                    "ShoppingBasketReliability.measureBaseline (1000 samples)\n");
        }

        @Test
        @DisplayName("sample pass shows lowercase 'pass'")
        void samplePass() {
            reporter.onSampleComplete(1, 1000, true);

            assertThat(output()).isEqualTo("  sample 1/1000 pass\n");
        }

        @Test
        @DisplayName("sample failure shows uppercase 'FAIL'")
        void sampleFail() {
            reporter.onSampleComplete(42, 1000, false);

            assertThat(output()).isEqualTo("  sample 42/1000 FAIL\n");
        }

        @Test
        @DisplayName("experiment completion shows sample totals, not a verdict")
        void experimentComplete() {
            reporter.onExperimentComplete("ShoppingBasketReliability.measureBaseline", 1000, 950);

            assertThat(output()).isEqualTo(
                    "  -> done (950/1000 samples passed)\n\n");
        }

        @Test
        @DisplayName("experiment completion never contains PASS or FAIL verdict")
        void experimentCompleteNoVerdict() {
            reporter.onExperimentComplete("X.measure", 100, 100);

            assertThat(output()).doesNotContain("PASS");
            assertThat(output()).doesNotContain("FAIL");
            assertThat(output()).doesNotContain("VERDICT");
        }

        @Test
        @DisplayName("full experiment verbose sequence")
        void fullExperimentSequence() {
            reporter.onMethodStart("Reliability.measure", 3);
            reporter.onSampleComplete(1, 3, true);
            reporter.onSampleComplete(2, 3, false);
            reporter.onSampleComplete(3, 3, true);
            reporter.onExperimentComplete("Reliability.measure", 3, 2);

            assertThat(output()).isEqualTo(
                    "Reliability.measure (3 samples)\n" +
                    "  sample 1/3 pass\n" +
                    "  sample 2/3 FAIL\n" +
                    "  sample 3/3 pass\n" +
                    "  -> done (2/3 samples passed)\n\n");
        }
    }

    // ── Test progress (verbose mode) ─────────────────────────────────────

    @Nested
    @DisplayName("test progress")
    class TestProgress {

        @Test
        @DisplayName("test completion shows PASS verdict")
        void testPass() {
            reporter.onTestComplete("Reliability.testBaseline", true);

            assertThat(output()).isEqualTo("  -> PASS\n\n");
        }

        @Test
        @DisplayName("test completion shows FAIL verdict")
        void testFail() {
            reporter.onTestComplete("Reliability.testBaseline", false);

            assertThat(output()).isEqualTo("  -> FAIL\n\n");
        }

        @Test
        @DisplayName("full test verbose sequence")
        void fullTestSequence() {
            reporter.onMethodStart("Reliability.testBaseline", 100);
            reporter.onSampleComplete(1, 100, true);
            reporter.onSampleComplete(2, 100, true);
            reporter.onTestComplete("Reliability.testBaseline", true);

            assertThat(output()).isEqualTo(
                    "Reliability.testBaseline (100 samples)\n" +
                    "  sample 1/100 pass\n" +
                    "  sample 2/100 pass\n" +
                    "  -> PASS\n\n");
        }
    }

    // ── Experiment summary ───────────────────────────────────────────────

    @Nested
    @DisplayName("experiment summary")
    class ExperimentSummary {

        @Test
        @DisplayName("shows sample-level totals aggregated across experiments")
        void sampleTotals() {
            SentinelResult result = experimentResult(
                    experimentVerdict("Exp", "exp1", 1000, 950, 50),
                    experimentVerdict("Exp", "exp2", 500, 480, 20));

            reporter.printExperimentSummary(result);

            assertThat(output()).contains("Exp.exp1");
            assertThat(output()).contains("Samples:   1000  Successes: 950  Failures: 50");
            assertThat(output()).contains("Exp.exp2");
            assertThat(output()).contains("Samples:   500  Successes: 480  Failures: 20");
        }

        @Test
        @DisplayName("result is COMPLETE, never PASS or FAIL")
        void resultIsComplete() {
            SentinelResult result = experimentResult(
                    experimentVerdict("Exp", "exp1", 100, 90, 10));

            reporter.printExperimentSummary(result);

            assertThat(output()).contains("Result: COMPLETE");
            assertThat(output()).doesNotContain("Result: PASS");
            assertThat(output()).doesNotContain("Result: FAIL");
        }

        @Test
        @DisplayName("shows duration")
        void showsDuration() {
            SentinelResult result = experimentResult(
                    experimentVerdict("Exp", "exp1", 100, 100, 0));

            reporter.printExperimentSummary(result);

            assertThat(output()).contains("Duration:");
        }

        @Test
        @DisplayName("full experiment summary format")
        void fullFormat() {
            SentinelResult result = new SentinelResult(
                    2, 2, 0, 0,
                    List.of(
                            experimentVerdict("Exp", "exp1", 1000, 950, 50),
                            experimentVerdict("Exp", "exp2", 500, 500, 0)),
                    Duration.ofMillis(25000));

            reporter.printExperimentSummary(result);

            assertThat(output()).isEqualTo(
                    "\n" +
                    "Sentinel Experiment Summary\n" +
                    "\u2500".repeat(40) + "\n" +
                    "Exp.exp1\n" +
                    "  Samples:   1000  Successes: 950  Failures: 50\n" +
                    "Exp.exp2\n" +
                    "  Samples:   500  Successes: 500  Failures: 0\n" +
                    "\n" +
                    "Duration: 25000ms\n" +
                    "Result: COMPLETE\n");
        }
    }

    // ── Test summary ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("test summary")
    class TestSummary {

        @Test
        @DisplayName("all passing shows Result: PASS")
        void allPassing() {
            SentinelResult result = new SentinelResult(
                    2, 2, 0, 0,
                    List.of(testVerdict("Suite", "t1", true), testVerdict("Suite", "t2", true)),
                    Duration.ofMillis(5000));

            reporter.printTestSummary(result);

            assertThat(output()).contains("Result: PASS");
            assertThat(output()).doesNotContain("FAILED:");
        }

        @Test
        @DisplayName("failures show Result: FAIL with failed test names")
        void withFailures() {
            SentinelResult result = new SentinelResult(
                    2, 1, 1, 0,
                    List.of(testVerdict("Suite", "t1", true), testVerdict("Suite", "t2", false)),
                    Duration.ofMillis(5000));

            reporter.printTestSummary(result);

            assertThat(output()).contains("Result: FAIL");
            assertThat(output()).contains("FAILED: Suite.t2");
        }

        @Test
        @DisplayName("full passing test summary format")
        void fullPassingFormat() {
            SentinelResult result = new SentinelResult(
                    3, 3, 0, 0,
                    List.of(
                            testVerdict("Suite", "t1", true),
                            testVerdict("Suite", "t2", true),
                            testVerdict("Suite", "t3", true)),
                    Duration.ofMillis(1234));

            reporter.printTestSummary(result);

            assertThat(output()).isEqualTo(
                    "\n" +
                    "Sentinel Test Summary\n" +
                    "\u2500".repeat(40) + "\n" +
                    "Total:    3\n" +
                    "Passed:   3\n" +
                    "Failed:   0\n" +
                    "Skipped:  0\n" +
                    "Duration: 1234ms\n" +
                    "\n" +
                    "Result: PASS\n");
        }

        @Test
        @DisplayName("empty run shows Result: NO MATCH")
        void emptyRun() {
            SentinelResult result = new SentinelResult(
                    0, 0, 0, 0, List.of(), Duration.ofMillis(0));

            reporter.printTestSummary(result);

            assertThat(output()).contains("Result: NO MATCH");
            assertThat(output()).doesNotContain("Result: PASS");
            assertThat(output()).doesNotContain("Result: FAIL");
        }
    }

    // ── Use case catalog ───────────────────────────────────────────────

    @Nested
    @DisplayName("use case catalog")
    class UseCaseCatalogOutput {

        @Test
        @DisplayName("heading order is Use Case Id, Type, Name, Samples")
        void headingOrder() {
            SentinelRunner.UseCaseCatalog catalog = new SentinelRunner.UseCaseCatalog(
                    List.of(new SentinelRunner.UseCaseCatalog.Entry(
                            "test", "shopping-basket", "Reliability.test", 100)));

            reporter.printUseCaseCatalog(catalog);

            String header = output().split("\n")[0];
            int idPos = header.indexOf("Use Case Id");
            int typePos = header.indexOf("Type");
            int namePos = header.indexOf("Name");
            int samplesPos = header.indexOf("Samples");
            assertThat(idPos).isLessThan(typePos);
            assertThat(typePos).isLessThan(namePos);
            assertThat(namePos).isLessThan(samplesPos);
        }

        @Test
        @DisplayName("entries are sorted alphabetically by use case id, then type")
        void sortedAlphabetically() {
            SentinelRunner.UseCaseCatalog catalog = new SentinelRunner.UseCaseCatalog(
                    List.of(
                            new SentinelRunner.UseCaseCatalog.Entry(
                                    "test", "shopping-basket", "Reliability.testBaseline", 100),
                            new SentinelRunner.UseCaseCatalog.Entry(
                                    "experiment", "payment-gateway", "Gateway.measure", 500),
                            new SentinelRunner.UseCaseCatalog.Entry(
                                    "experiment", "shopping-basket", "Reliability.measureBaseline", 1000)));

            reporter.printUseCaseCatalog(catalog);

            String out = output();
            int paymentPos = out.indexOf("payment-gateway");
            int shoppingExpPos = out.indexOf("Reliability.measureBaseline");
            int shoppingTestPos = out.indexOf("Reliability.testBaseline");
            assertThat(paymentPos).isLessThan(shoppingExpPos);
            assertThat(shoppingExpPos).isLessThan(shoppingTestPos);
        }

        @Test
        @DisplayName("shows message when no use cases found")
        void emptyCase() {
            SentinelRunner.UseCaseCatalog catalog = new SentinelRunner.UseCaseCatalog(List.of());

            reporter.printUseCaseCatalog(catalog);

            assertThat(output()).isEqualTo("No use cases found.\n");
        }

        @Test
        @DisplayName("separator spans full table width")
        void separatorSpansWidth() {
            SentinelRunner.UseCaseCatalog catalog = new SentinelRunner.UseCaseCatalog(
                    List.of(new SentinelRunner.UseCaseCatalog.Entry(
                            "test", "id", "Name.method", 50)));

            reporter.printUseCaseCatalog(catalog);

            String[] lines = output().split("\n");
            assertThat(lines.length).isGreaterThanOrEqualTo(3);
            assertThat(lines[1]).matches("─+");
        }
    }

    // ── Test helpers ─────────────────────────────────────────────────────

    private ProbabilisticTestVerdict experimentVerdict(
            String className, String methodName, int samples, int successes, int failures) {
        double observedRate = samples > 0 ? (double) successes / samples : 0.0;
        return new ProbabilisticTestVerdictBuilder()
                .identity(className, methodName, null)
                .execution(samples, samples, successes, failures, 0.0, observedRate, 0)
                .junitPassed(true)
                .passedStatistically(true)
                .build();
    }

    private ProbabilisticTestVerdict testVerdict(String className, String methodName, boolean passed) {
        return new ProbabilisticTestVerdictBuilder()
                .identity(className, methodName, null)
                .execution(10, 10, passed ? 10 : 5, passed ? 0 : 5, 0.9, passed ? 1.0 : 0.5, 100)
                .junitPassed(passed)
                .passedStatistically(passed)
                .build();
    }

    private SentinelResult experimentResult(ProbabilisticTestVerdict... verdicts) {
        return new SentinelResult(
                verdicts.length, verdicts.length, 0, 0,
                List.of(verdicts), Duration.ofMillis(5000));
    }
}
