package org.javai.punit.reporting;

import java.io.PrintStream;

/**
 * A {@link VerdictSink} that renders IDE-like verdict output to a {@link PrintStream}.
 *
 * <p>Produces the same structured output as the JUnit 5 test runner's console
 * summary, reconstructed from the structured {@code punit.*} report entries
 * carried by each {@link VerdictEvent}.
 *
 * <p>Designed for CLI use (Sentinel runtime) where the operator wants to see
 * each verdict as it happens, formatted the way they would appear in an IDE
 * test run.
 *
 * <p>Output uses the standard PUnit framing (header/footer dividers) and
 * label-value alignment provided by {@link PUnitReporter}.
 */
public final class ConsoleVerdictSink implements VerdictSink {

    private final PrintStream out;
    private final PUnitReporter reporter;

    public ConsoleVerdictSink() {
        this(System.out);
    }

    public ConsoleVerdictSink(PrintStream out) {
        this.out = out;
        this.reporter = new PUnitReporter();
    }

    @Override
    public void accept(VerdictEvent event) {
        var entries = event.reportEntries();

        boolean passed = event.passed();
        String verdict = passed ? "PASS" : "FAIL";

        String title = "VERDICT: " + verdict;
        StringBuilder sb = new StringBuilder();
        sb.append(event.testName()).append("\n\n");

        String observedRate = entries.getOrDefault("punit.observedPassRate", "?");
        String minPassRate = entries.getOrDefault("punit.minPassRate", "?");
        String successes = entries.getOrDefault("punit.successes", "?");
        String samplesExecuted = entries.getOrDefault("punit.samplesExecuted", "?");
        String terminationReason = entries.getOrDefault("punit.terminationReason", "COMPLETED");

        boolean isBudgetExhausted = terminationReason.contains("BUDGET");

        if (passed) {
            sb.append(PUnitReporter.labelValueLn("Observed pass rate:",
                    String.format("%s (%s/%s) >= required: %s",
                            formatRate(observedRate), successes, samplesExecuted,
                            formatRate(minPassRate))));
        } else if (isBudgetExhausted) {
            String samples = entries.getOrDefault("punit.samples", "?");
            sb.append(PUnitReporter.labelValueLn("Samples executed:",
                    String.format("%s of %s (budget exhausted)", samplesExecuted, samples)));
            sb.append(PUnitReporter.labelValueLn("Pass rate:",
                    String.format("%s (%s/%s), required: %s",
                            formatRate(observedRate), successes, samplesExecuted,
                            formatRate(minPassRate))));
        } else {
            sb.append(PUnitReporter.labelValueLn("Observed pass rate:",
                    String.format("%s (%s/%s) < required: %s",
                            formatRate(observedRate), successes, samplesExecuted,
                            formatRate(minPassRate))));
        }

        // Per-dimension breakdown (when both dimensions are asserted)
        appendDimensionBreakdown(sb, entries);

        // Termination details
        if (!terminationReason.equals("COMPLETED")) {
            sb.append(PUnitReporter.labelValueLn("Termination:", terminationReason));
        }

        String elapsedMs = entries.getOrDefault("punit.elapsedMs", "?");
        sb.append(PUnitReporter.labelValue("Elapsed:", elapsedMs + "ms"));

        out.println(reporter.headerDivider(title));
        out.println();
        // Indent the body (consistent with PUnitReporter.format)
        for (String line : sb.toString().split("\n")) {
            out.println("  " + line);
        }
        out.println(reporter.footerDivider());
        out.println();
    }

    private void appendDimensionBreakdown(StringBuilder sb, java.util.Map<String, String> entries) {
        boolean functionalAsserted = "true".equals(entries.get("punit.dimension.functional"));
        boolean latencyAsserted = "true".equals(entries.get("punit.dimension.latency"));

        if (!functionalAsserted || !latencyAsserted) {
            return; // Only show breakdown when both dimensions are asserted
        }

        String funcSuccesses = entries.getOrDefault("punit.dimension.functional.successes", "?");
        String funcFailures = entries.getOrDefault("punit.dimension.functional.failures", "?");
        String latSuccesses = entries.getOrDefault("punit.dimension.latency.successes", "?");
        String latFailures = entries.getOrDefault("punit.dimension.latency.failures", "?");

        sb.append(PUnitReporter.labelValueLn("Contract:",
                String.format("%s/%s passed", funcSuccesses, addStrings(funcSuccesses, funcFailures))));
        sb.append(PUnitReporter.labelValueLn("Latency:",
                String.format("%s/%s within limit", latSuccesses, addStrings(latSuccesses, latFailures))));
    }

    /**
     * Formats a rate string (e.g., "0.9500") as a percentage (e.g., "95.00%").
     */
    private String formatRate(String rateStr) {
        try {
            double rate = Double.parseDouble(rateStr);
            return RateFormat.format(rate);
        } catch (NumberFormatException e) {
            return rateStr;
        }
    }

    private String addStrings(String a, String b) {
        try {
            return String.valueOf(Integer.parseInt(a) + Integer.parseInt(b));
        } catch (NumberFormatException e) {
            return "?";
        }
    }
}
