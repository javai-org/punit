package org.javai.punit.sentinel;

import java.io.PrintStream;
import java.util.Map;
import org.javai.punit.reporting.VerdictEvent;

/**
 * Owns all console output for the Sentinel CLI.
 *
 * <p>This class is the single point of control for the shape and content of
 * sentinel output — both real-time progress (verbose mode) and the final
 * summary. Extracting it from {@link SentinelMain} makes the output format
 * transparent, testable, and easy to iterate on.
 *
 * <h2>Experiment output</h2>
 * <p>Experiments collect data — they do not deliver verdicts. Progress output
 * shows per-sample pass/fail and a completion line with sample totals. The
 * summary shows aggregate sample counts and {@code Result: COMPLETE}.
 *
 * <h2>Test output</h2>
 * <p>Tests deliver verdicts. Progress output shows per-sample pass/fail and
 * the test verdict. The summary shows test-level pass/fail counts and the
 * overall result.
 */
class SentinelConsoleReporter implements SentinelProgressListener {

    private static final String SEPARATOR = "\u2500".repeat(40);

    private final PrintStream out;

    SentinelConsoleReporter(PrintStream out) {
        this.out = out;
    }

    // ── Progress callbacks (verbose mode) ────────────────────────────────

    @Override
    public void onMethodStart(String name, int totalSamples) {
        out.println(name + " (" + totalSamples + " samples)");
    }

    @Override
    public void onSampleComplete(int sampleNumber, int totalSamples, boolean passed) {
        String status = passed ? "pass" : "FAIL";
        out.printf("  sample %d/%d %s%n", sampleNumber, totalSamples, status);
    }

    @Override
    public void onTestComplete(String testName, boolean passed) {
        String verdict = passed ? "PASS" : "FAIL";
        out.println("  -> " + verdict);
        out.println();
    }

    @Override
    public void onExperimentComplete(String experimentName, int samples, int successes) {
        out.printf("  -> done (%d/%d samples passed)%n", successes, samples);
        out.println();
    }

    // ── Summary output ───────────────────────────────────────────────────

    void printTestSummary(SentinelResult result) {
        out.println();
        out.println("Sentinel Test Summary");
        out.println(SEPARATOR);
        out.println("Total:    " + result.totalTests());
        out.println("Passed:   " + result.passed());
        out.println("Failed:   " + result.failed());
        out.println("Skipped:  " + result.skipped());
        out.println("Duration: " + result.totalDuration().toMillis() + "ms");
        out.println();

        if (result.allPassed()) {
            out.println("Result: PASS");
        } else {
            out.println("Result: FAIL");
            result.verdicts().stream()
                    .filter(v -> !v.passed())
                    .forEach(v -> out.println("  FAILED: " + v.testName()));
        }
    }

    void printExperimentSummary(SentinelResult result) {
        out.println();
        out.println("Sentinel Experiment Summary");
        out.println(SEPARATOR);

        for (VerdictEvent verdict : result.verdicts()) {
            int samples = parseReportInt(verdict, "punit.experiment.samples");
            int successes = parseReportInt(verdict, "punit.experiment.successes");
            int failures = parseReportInt(verdict, "punit.experiment.failures");
            out.println(verdict.testName());
            out.printf("  Samples:   %d  Successes: %d  Failures: %d%n",
                    samples, successes, failures);
        }

        out.println();
        out.println("Duration: " + result.totalDuration().toMillis() + "ms");
        out.println("Result: COMPLETE");
    }

    // ── Use case listing ───────────────────────────────────────────────

    void printUseCaseCatalog(SentinelRunner.UseCaseCatalog catalog) {
        Map<String, String> tests = catalog.tests();
        Map<String, String> experiments = catalog.experiments();

        if (experiments.isEmpty() && tests.isEmpty()) {
            out.println("No use cases found.");
            return;
        }

        if (!experiments.isEmpty()) {
            out.println("Experiments:");
            experiments.forEach((id, detail) ->
                    out.println("  " + id + "  " + detail));
            out.println();
        }

        if (!tests.isEmpty()) {
            out.println("Tests:");
            tests.forEach((id, detail) ->
                    out.println("  " + id + "  " + detail));
            out.println();
        }
    }

    private int parseReportInt(VerdictEvent verdict, String key) {
        String value = verdict.reportEntries().get(key);
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
